package fr.reseauredmi.mode

/**
 * Transforme les sorties du shell ADB au même format que le service Shizuku,
 * pour réutiliser [ModeReport.level].
 */
object AdbReport {
    fun probeOk(text: String): Boolean = text.contains("uid=")

    fun assemble(
        probe: String,
        grant: String,
        setOutput: String,
        settingPuts: List<String>,
        getOutput: String,
        settingGet: String,
    ): String {
        val fields = mutableListOf<String>()
        val log = StringBuilder()
        fun field(key: String, value: String) {
            fields += "$key=${value.replace('\n', ' ').trim()}"
        }
        Regex("""uid=(\d+)""").find(probe)?.groupValues?.get(1)?.let { field("uid", it) }
        field("shell_set", setOutput.contains("completed", ignoreCase = true).toString())
        field("settings_put", settingPuts.none { failedSetting(it) }.toString())
        networkLine(getOutput)?.let { field("shell_get", it) }
        settingGet.lineSequence().map { it.trim() }.firstOrNull { it.toIntOrNull() != null }
            ?.let { field("settings", it) }
        log.appendLine("probe: ${one(probe)}")
        log.appendLine("grant: ${one(grant)}")
        log.appendLine("set: ${one(setOutput)}")
        settingPuts.forEach { log.appendLine("settings put: ${one(it)}") }
        log.appendLine("get: ${one(getOutput)}")
        log.appendLine("settings get: ${one(settingGet)}")
        return fields.joinToString("\n") + "\n---\n" + log.toString().take(3500)
    }

    fun networkLine(text: String): String? =
        text.lineSequence().map { it.trim() }.lastOrNull { ModeReport.looksLikeNetworkList(it) }

    private fun failedSetting(text: String): Boolean {
        val lower = text.lowercase()
        if (lower.isBlank()) return false
        return lower.contains("exception") ||
            lower.contains("error") ||
            lower.contains("failed") ||
            lower.contains("unknown") ||
            lower.contains("denied") ||
            lower.contains("usage")
    }

    private fun one(text: String): String = text.replace('\n', ' ').trim().take(500)
}
