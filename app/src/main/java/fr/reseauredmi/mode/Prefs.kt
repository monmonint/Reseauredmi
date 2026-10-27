package fr.reseauredmi.mode

import android.content.Context

object Prefs {
    private const val FILE = "mode_reseau"
    private const val MODE = "mode"
    private const val BOOT = "reapply_on_boot"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun mode(context: Context): ForcedMode? = when (prefs(context).getString(MODE, null)) {
        "3g" -> ForcedMode.WCDMA
        "4g" -> ForcedMode.LTE
        else -> null
    }

    fun setMode(context: Context, mode: ForcedMode) {
        prefs(context).edit()
            .putString(MODE, if (mode == ForcedMode.WCDMA) "3g" else "4g")
            .apply()
    }

    fun reapplyOnBoot(context: Context): Boolean = prefs(context).getBoolean(BOOT, true)

    fun setReapplyOnBoot(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(BOOT, enabled).apply()
    }
}
