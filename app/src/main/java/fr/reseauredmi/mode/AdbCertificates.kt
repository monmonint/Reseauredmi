package fr.reseauredmi.mode

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Security
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.Date

/**
 * MIUI (et Android en général) enregistre un fournisseur « BC » amputé,
 * `com.android.org.bouncycastle`, qui ne signe pas en SHA256withRSA.
 * `Security.addProvider` ne le remplace pas : il faut le retirer, puis
 * installer le BouncyCastle complet embarqué dans l’application.
 * S’il reste en place, la signature passe par AndroidOpenSSL ou Conscrypt.
 */
object AdbCertificates {
    data class Generated(val pkcs8: ByteArray, val certificate: X509Certificate) {
        fun publicKey(): PublicKey = certificate.publicKey
    }

    fun generate(): Generated = generate(allowBundledBc = true)

    internal fun generate(allowBundledBc: Boolean): Generated {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048, SecureRandom())
        val keys = generator.generateKeyPair()
        val now = Date()
        val later = Date(now.time + TEN_YEARS_MS)
        val name = X500Name("CN=Mode 3G 4G")
        val serial = BigInteger(64, SecureRandom()).abs().let { if (it.signum() == 0) BigInteger.ONE else it }
        val holder = JcaX509v3CertificateBuilder(name, serial, now, later, name, keys.public)
            .build(contentSigner(keys.private, allowBundledBc))
        val certificate = convert(holder)
        certificate.checkValidity(now)
        verify(certificate, keys.public)
        return Generated(keys.private.encoded, certificate)
    }

    internal fun contentSigner(key: PrivateKey, allowBundledBc: Boolean): ContentSigner {
        val errors = mutableListOf<String>()
        if (allowBundledBc && BcProviders.installFull()) {
            try {
                return JcaContentSignerBuilder("SHA256withRSA").setProvider(BcProviders.NAME).build(key)
            } catch (error: Exception) {
                errors += "BC: ${error.message}"
            }
        }
        for (provider in listOf(null, "AndroidOpenSSL", "Conscrypt", "GmsCore_OpenSSL")) {
            if (provider != null && Security.getProvider(provider) == null) continue
            try {
                val builder = JcaContentSignerBuilder("SHA256withRSA")
                if (provider != null) builder.setProvider(provider)
                return builder.build(key)
            } catch (error: Exception) {
                errors += "${provider ?: "défaut"}: ${error.message}"
            }
        }
        throw IllegalStateException(
            "Impossible de signer le certificat d’appairage. ${errors.joinToString("; ")}",
        )
    }

    private fun convert(holder: org.bouncycastle.cert.X509CertificateHolder): X509Certificate {
        if (Security.getProvider(BcProviders.NAME) is BouncyCastleProvider) {
            try {
                return JcaX509CertificateConverter().setProvider(BcProviders.NAME).getCertificate(holder)
            } catch (_: Exception) {
                // Le magasin du système sait lire un certificat X.509 standard.
            }
        }
        return JcaX509CertificateConverter().getCertificate(holder)
    }

    private fun verify(certificate: X509Certificate, publicKey: PublicKey) {
        try {
            certificate.verify(publicKey)
        } catch (_: Exception) {
            if (Security.getProvider(BcProviders.NAME) is BouncyCastleProvider) {
                certificate.verify(publicKey, BcProviders.NAME)
            } else {
                throw IllegalStateException("Le certificat d’appairage ne se vérifie pas.")
            }
        }
    }

    private const val TEN_YEARS_MS = 10L * 365L * 24L * 60L * 60L * 1000L
}

internal object BcProviders {
    const val NAME = "BC"
    private val lock = Any()

    fun installFull(): Boolean {
        synchronized(lock) {
            val current = Security.getProvider(NAME)
            if (current !is BouncyCastleProvider) {
                Security.removeProvider(NAME)
                Security.addProvider(BouncyCastleProvider())
            }
            return try {
                Signature.getInstance("SHA256withRSA", NAME)
                Security.getProvider(NAME) is BouncyCastleProvider
            } catch (_: Exception) {
                false
            }
        }
    }
}
