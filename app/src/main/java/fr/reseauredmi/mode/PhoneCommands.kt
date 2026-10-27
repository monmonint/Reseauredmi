package fr.reseauredmi.mode

/**
 * Commandes shell qui règlent le mode radio.
 * Le masque est en binaire, sans préfixe, comme `cmd phone` le parse (`Long.parseLong(s, 2)`).
 * `-s` est l’index d’emplacement physique, pas l’identifiant d’abonnement.
 */
object PhoneCommands {
    const val PORT = 5555
    const val LOOPBACK = "127.0.0.1"

    fun setAllowed(slotIndex: Int, bitmask: Long): Array<String> =
        phone("set-allowed-network-types-for-users", slotIndex, bitmask.toString(2))

    fun getAllowed(slotIndex: Int): Array<String> =
        phone("get-allowed-network-types-for-users", slotIndex, null)

    fun settingKeys(subId: Int): List<String> {
        val keys = mutableListOf("preferred_network_mode")
        if (subId >= 0) keys += "preferred_network_mode$subId"
        return keys
    }

    fun putSetting(key: String, rilMode: Int): Array<String> =
        arrayOf("/system/bin/settings", "put", "global", key, rilMode.toString())

    fun getSetting(key: String): Array<String> =
        arrayOf("/system/bin/settings", "get", "global", key)

    fun grantReadPhoneState(packageName: String): Array<String> =
        arrayOf("/system/bin/pm", "grant", packageName, "android.permission.READ_PHONE_STATE")

    fun probe(): Array<String> = arrayOf("/system/bin/id")

    fun shell(args: Array<String>): String = args.joinToString(" ") { arg ->
        require(arg.matches(SAFE)) { "argument refusé" }
        arg
    }

    private fun phone(action: String, slotIndex: Int, value: String?): Array<String> {
        val command = mutableListOf("/system/bin/cmd", "phone", action)
        if (slotIndex >= 0) {
            command += "-s"
            command += slotIndex.toString()
        }
        if (value != null) command += value
        return command.toTypedArray()
    }

    private val SAFE = Regex("^[A-Za-z0-9._/:-]+$")
}
