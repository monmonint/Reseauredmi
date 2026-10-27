package fr.reseauredmi.mode

import android.content.Context
import android.net.wifi.WifiManager
import io.github.muntashirakon.adb.android.AdbMdns
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Recherche mDNS du débogage sans fil. Échoue souvent : la saisie manuelle reste le chemin sûr. */
object AdbDiscovery {
    data class Endpoint(val host: String, val port: Int)

    fun find(context: Context, serviceType: String, timeoutMs: Long): Endpoint? {
        val app = context.applicationContext
        val wifi = app.getSystemService(WifiManager::class.java)
        val lock = wifi?.createMulticastLock("mode-adb-mdns")
        lock?.setReferenceCounted(false)
        lock?.acquire()
        try {
            val host = AtomicReference<String?>(null)
            val port = AtomicInteger(-1)
            val latch = CountDownLatch(1)
            val mdns = AdbMdns(app, serviceType) { address, foundPort ->
                val ip = address?.hostAddress
                if (ip != null && AdbEndpoint.ipv4(ip) && foundPort > 0) {
                    host.set(ip)
                    port.set(foundPort)
                    latch.countDown()
                }
            }
            mdns.start()
            try {
                latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            } finally {
                runCatching { mdns.stop() }
            }
            val foundHost = host.get() ?: return null
            val foundPort = port.get()
            if (foundPort <= 0) return null
            return Endpoint(foundHost, foundPort)
        } finally {
            runCatching { lock?.release() }
        }
    }
}
