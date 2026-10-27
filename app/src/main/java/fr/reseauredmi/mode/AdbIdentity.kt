package fr.reseauredmi.mode

import android.content.Context
import java.io.File
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Clé RSA de l’application, enregistrée dans le stockage privé.
 * C’est cette clé que le débogage sans fil mémorise dans adb_keys.
 * Elle survit à un redémarrage ; le port TCP 5555, non.
 */
object AdbIdentity {
    class Material(private val pkcs8: ByteArray, val certificate: X509Certificate) {
        fun privateKey(): PrivateKey =
            KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(pkcs8.copyOf()))
    }

    @Volatile
    private var cached: Material? = null

    fun get(context: Context): Material {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val loaded = load(context.applicationContext)
            cached = loaded
            return loaded
        }
    }

    private fun load(context: Context): Material {
        val dir = File(context.filesDir, "adb-identity")
        val keyFile = File(dir, "private.pkcs8")
        val certFile = File(dir, "cert.der")
        if (keyFile.isFile && certFile.isFile) {
            try {
                return parse(keyFile.readBytes(), certFile.readBytes())
            } catch (_: Exception) {
                keyFile.delete()
                certFile.delete()
            }
        }
        dir.mkdirs()
        val created = AdbCertificates.generate()
        val material = Material(created.pkcs8, created.certificate)
        writeAtomic(keyFile, created.pkcs8)
        writeAtomic(certFile, created.certificate.encoded)
        return material
    }

    private fun parse(pkcs8: ByteArray, certBytes: ByteArray): Material {
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(certBytes.inputStream()) as X509Certificate
        val material = Material(pkcs8, certificate)
        material.privateKey()
        return material
    }

    private fun writeAtomic(file: File, bytes: ByteArray) {
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.outputStream().use { it.write(bytes) }
        if (!temporary.renameTo(file)) {
            file.writeBytes(bytes)
            temporary.delete()
        }
    }
}
