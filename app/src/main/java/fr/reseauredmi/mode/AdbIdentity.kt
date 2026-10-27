package fr.reseauredmi.mode

import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Security
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date

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
        val created = create()
        writeAtomic(keyFile, created.pkcs8())
        writeAtomic(certFile, created.certificate.encoded)
        return created
    }

    private fun parse(pkcs8: ByteArray, certBytes: ByteArray): Material {
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(certBytes.inputStream()) as X509Certificate
        val material = Material(pkcs8, certificate)
        material.privateKey()
        return material
    }

    private fun create(): Material {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048, SecureRandom())
        val keys = generator.generateKeyPair()
        val now = Date()
        val later = Date(now.time + TEN_YEARS_MS)
        val name = X500Name("CN=Mode 3G 4G")
        val serial = BigInteger(64, SecureRandom()).abs().let { if (it.signum() == 0) BigInteger.ONE else it }
        val builder = JcaX509v3CertificateBuilder(name, serial, now, later, name, keys.public)
        val signer = JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .build(keys.private)
        val certificate = JcaX509CertificateConverter()
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .getCertificate(builder.build(signer))
        certificate.checkValidity(now)
        certificate.verify(keys.public)
        return Material(keys.private.encoded, certificate)
    }

    private fun Material.pkcs8(): ByteArray = privateKey().encoded

    private fun writeAtomic(file: File, bytes: ByteArray) {
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.outputStream().use { it.write(bytes) }
        if (!temporary.renameTo(file)) {
            file.writeBytes(bytes)
            temporary.delete()
        }
    }

    private const val TEN_YEARS_MS = 10L * 365L * 24L * 60L * 60L * 1000L
}
