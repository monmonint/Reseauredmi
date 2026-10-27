package fr.reseauredmi.mode

object AdbEndpoint {
    data class Host(val address: String, val port: Int?)

    fun parseHost(raw: String): Host {
        val text = raw.trim()
        val withPort = Regex("""^(.+):(\d{1,5})$""").matchEntire(text)
        if (withPort != null && !text.contains("]")) {
            val port = withPort.groupValues[2].toIntOrNull()
            val address = withPort.groupValues[1]
            if (port != null && ipv4(address)) return Host(address, port)
        }
        return Host(text, null)
    }

    fun ipv4(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            if (part.isEmpty() || part.length > 3) return false
            val number = part.toIntOrNull() ?: return false
            number in 0..255
        }
    }

    fun port(value: Int): Boolean = value in 1..65535
}
