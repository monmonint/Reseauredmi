package fr.reseauredmi.mode

import android.content.Context
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.concurrent.TimeUnit

/**
 * Une connexion ADB ouverte le temps d’une action, puis refermée.
 * Aucun service et aucune connexion ne restent en arrière-plan.
 */
internal class AdbSession(context: Context) : AutoCloseable {
    private val manager = Manager(AdbIdentity.get(context)).apply {
        setApi(android.os.Build.VERSION.SDK_INT)
        setHostAddress(PhoneCommands.LOOPBACK)
    }

    fun pair(host: String, port: Int, code: String) {
        if (!TcpProbe.open(host, port, 2_500)) {
            throw AdbUnavailable("Rien n’écoute sur $host:$port.")
        }
        val paired = manager.pair(host, port, code)
        if (!paired) throw AdbUnavailable("Appairage refusé.")
    }

    fun connect(host: String, port: Int, prompt: Boolean, handshakeSeconds: Long) {
        if (!TcpProbe.open(host, port, 2_500)) {
            throw AdbUnavailable("Rien n’écoute sur $host:$port.")
        }
        manager.setThrowOnUnauthorised(!prompt)
        manager.setTimeout(handshakeSeconds, TimeUnit.SECONDS)
        drop()
        val connected = manager.connect(host, port)
        if (!connected) {
            throw AdbUnavailable("Délai dépassé en contactant $host:$port.")
        }
    }

    fun exec(args: Array<String>, timeoutMs: Long = 15_000): String {
        val stream = manager.openStream("shell:" + PhoneCommands.shell(args))
        return read(stream, timeoutMs).replace("\r", "")
    }

    /**
     * Équivalent de `adb tcpip 5555` : service ADB `tcpip:`, pas une commande shell.
     * adbd redémarre et coupe cette connexion. Le port 5555 écoute ensuite sur toutes
     * les interfaces, y compris 127.0.0.1, jusqu’au redémarrage du téléphone.
     */
    fun tcpip() {
        try {
            val stream = manager.openStream("tcpip:${PhoneCommands.PORT}")
            runCatching { read(stream, 2_500) }
        } catch (_: Exception) {
            // Le démon coupe le socket en redémarrant.
        } finally {
            drop()
        }
    }

    fun drop() {
        runCatching { manager.disconnect() }
    }

    override fun close() {
        drop()
    }

    private fun read(stream: AdbStream, timeoutMs: Long): String {
        val out = ByteArrayOutputStream()
        val worker = Thread {
            try {
                val input = stream.openInputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) out.write(buffer, 0, count)
                }
            } catch (_: IOException) {
                // Le flux se ferme quand la commande se termine, ou quand on l’interrompt.
            }
        }
        worker.name = "adb-read"
        worker.isDaemon = true
        worker.start()
        worker.join(timeoutMs)
        runCatching { stream.close() }
        worker.join(1_500)
        if (worker.isAlive) throw AdbUnavailable("Le shell n’a pas répondu à temps.")
        return out.toString(Charsets.UTF_8)
    }

    private class Manager(private val material: AdbIdentity.Material) : AbsAdbConnectionManager() {
        override fun getPrivateKey(): PrivateKey = material.privateKey()

        override fun getCertificate(): Certificate = material.certificate

        override fun getDeviceName(): String = "Mode3G4G"
    }
}

internal object TcpProbe {
    fun open(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
