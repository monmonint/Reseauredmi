package fr.reseauredmi.mode

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.Provider
import java.security.Security
import java.security.Signature
import java.security.interfaces.RSAPublicKey

class AdbCertificatesTest {
    @Test
    fun fullBouncyCastleReplacesAndroidStubAndSigns() {
        val original = Security.getProvider("BC")
        Security.removeProvider("BC")
        Security.addProvider(StrippedAndroidBc())
        try {
            var stubRejected = false
            try {
                Signature.getInstance("SHA256withRSA", "BC")
            } catch (_: Exception) {
                stubRejected = true
            }
            assertTrue(stubRejected)
            val generated = AdbCertificates.generate()
            generated.certificate.verify(generated.publicKey())
            assertTrue(Security.getProvider("BC") is BouncyCastleProvider)
            assertEquals("SHA256WITHRSA", generated.certificate.sigAlgName.uppercase())
            val key = generated.publicKey() as RSAPublicKey
            assertEquals(2048, key.modulus.bitLength())
        } finally {
            Security.removeProvider("BC")
            if (original != null) Security.addProvider(original)
        }
    }

    @Test
    fun defaultProviderSignsWhenBundledBcIsSkipped() {
        val original = Security.getProvider("BC")
        Security.removeProvider("BC")
        Security.addProvider(StrippedAndroidBc())
        try {
            val generated = AdbCertificates.generate(allowBundledBc = false)
            generated.certificate.verify(generated.publicKey())
            assertTrue(Security.getProvider("BC") !is BouncyCastleProvider)
        } finally {
            Security.removeProvider("BC")
            if (original != null) Security.addProvider(original)
        }
    }
}

/** Reproduit le fournisseur BC amputé livré avec Android, sans SHA256withRSA. */
private class StrippedAndroidBc : Provider("BC", 1.0, "Android stripped BC")

class AdbServiceTypesTest {
    @Test
    fun serviceTypesMatchWirelessDebugging() {
        assertEquals("_adb-tls-pairing._tcp", AdbServiceTypes.PAIRING)
        assertEquals("_adb-tls-connect._tcp", AdbServiceTypes.CONNECT)
        assertEquals(AdbServiceTypes.Role.PAIRING, AdbServiceTypes.role("_adb-tls-pairing._tcp.local."))
        assertEquals(AdbServiceTypes.Role.CONNECT, AdbServiceTypes.role("_adb-tls-connect._tcp"))
        assertEquals(null, AdbServiceTypes.role("_adb._tcp"))
    }

    @Test
    fun hostPrefersIpv4ThenLoopback() {
        assertEquals("192.168.1.12", AdbServiceTypes.host(listOf("fe80::1", "192.168.1.12")))
        assertEquals(PhoneCommands.LOOPBACK, AdbServiceTypes.host(listOf(null, "fe80::1")))
    }
}
