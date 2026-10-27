package fr.reseauredmi.mode

/**
 * Compte-rendu texte renvoyé par le shell ADB ou par le service Shizuku.
 * La partie avant « --- » est une liste clé=valeur, le reste est un journal.
 */
object ModeReport {
    data class Report(
        val fields: Map<String, String>,
        val log: String,
    ) {
        fun int(key: String): Int? = fields[key]?.toIntOrNull()

        fun long(key: String): Long? = fields[key]?.toLongOrNull()

        fun flag(key: String): Boolean? = when (fields[key]) {
            "true", "ok" -> true
            "false", "fail" -> false
            else -> null
        }

        fun text(): String {
            val head = fields.entries.joinToString("\n") { "${it.key}=${it.value}" }
            return if (log.isBlank()) head else "$head\n$log"
        }
    }

    enum class Level {
        CONFIRMED,
        SENT,
        FAILED,
    }

    fun parse(raw: String): Report {
        val parts = raw.split("\n---\n", limit = 2)
        val fields = linkedMapOf<String, String>()
        for (line in parts[0].lineSequence()) {
            val index = line.indexOf('=')
            if (index <= 0) continue
            fields[line.substring(0, index).trim()] = line.substring(index + 1).trim()
        }
        return Report(fields, parts.getOrElse(1) { "" }.trim())
    }

    fun level(report: Report, mode: ForcedMode): Level {
        val allowed = report.long("allowed")
        if (allowed != null && allowed >= 0L) {
            return if (maskMatches(allowed, mode)) Level.CONFIRMED else Level.FAILED
        }
        val shell = report.fields["shell_get"]
        if (!shell.isNullOrBlank() && looksLikeNetworkList(shell)) {
            return if (shellGetMatches(shell, mode)) Level.CONFIRMED else Level.FAILED
        }
        val preferred = report.int("preferred")
        if (preferred != null && preferred >= 0) {
            return if (preferred == mode.ril) Level.CONFIRMED else Level.FAILED
        }
        if (report.flag("set_allowed") == true ||
            report.flag("set_preferred") == true ||
            report.flag("shell_set") == true
        ) {
            return Level.CONFIRMED
        }
        if (report.flag("settings_put") == true && report.int("settings") == mode.ril) {
            return Level.SENT
        }
        return Level.FAILED
    }

    fun inferred(report: Report): ForcedMode? {
        val allowed = report.long("allowed")
        if (allowed != null && allowed >= 0L) {
            return when {
                maskMatches(allowed, ForcedMode.WCDMA) -> ForcedMode.WCDMA
                maskMatches(allowed, ForcedMode.LTE) -> ForcedMode.LTE
                else -> null
            }
        }
        val shell = report.fields["shell_get"]
        if (!shell.isNullOrBlank() && looksLikeNetworkList(shell)) {
            return when {
                shellGetMatches(shell, ForcedMode.WCDMA) -> ForcedMode.WCDMA
                shellGetMatches(shell, ForcedMode.LTE) -> ForcedMode.LTE
                else -> null
            }
        }
        return when (report.int("preferred")) {
            ForcedMode.WCDMA.ril -> ForcedMode.WCDMA
            ForcedMode.LTE.ril -> ForcedMode.LTE
            else -> null
        }
    }

    fun maskMatches(mask: Long, mode: ForcedMode): Boolean {
        val lte = mask.has(NetworkBits.LTE) || mask.has(NetworkBits.LTE_CA)
        val umts = mask.has(NetworkBits.UMTS) || mask.has(NetworkBits.HSPA) || mask.has(NetworkBits.HSPAP)
        return if (mode == ForcedMode.LTE) lte else !lte && umts
    }

    fun looksLikeNetworkList(value: String): Boolean {
        val tokens = listOf("LTE", "UMTS", "GSM", "NR", "HSPA", "HSDPA", "HSUPA", "EDGE", "GPRS", "WCDMA")
        return tokens.any { value.contains(it) }
    }

    fun shellGetMatches(value: String, mode: ForcedMode): Boolean {
        val lte = value.contains("LTE") || value.contains("NR")
        val umts = value.contains("UMTS") || value.contains("HSPA") || value.contains("WCDMA")
        return if (mode == ForcedMode.LTE) value.contains("LTE") else !lte && umts
    }

    private fun Long.has(bit: Long): Boolean = this and bit != 0L
}
