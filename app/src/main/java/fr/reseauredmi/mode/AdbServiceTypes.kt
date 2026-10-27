package fr.reseauredmi.mode

/** Types mDNS du débogage sans fil Android 11+. */
object AdbServiceTypes {
    const val PAIRING = "_adb-tls-pairing._tcp"
    const val CONNECT = "_adb-tls-connect._tcp"

    enum class Role { PAIRING, CONNECT }

    fun role(serviceType: String): Role? {
        val normalized = serviceType.lowercase()
        return when {
            "adb-tls-pairing" in normalized -> Role.PAIRING
            "adb-tls-connect" in normalized -> Role.CONNECT
            else -> null
        }
    }

    fun host(addresses: List<String?>, fallback: String = PhoneCommands.LOOPBACK): String {
        return addresses.firstOrNull { it != null && AdbEndpoint.ipv4(it) } ?: fallback
    }
}
