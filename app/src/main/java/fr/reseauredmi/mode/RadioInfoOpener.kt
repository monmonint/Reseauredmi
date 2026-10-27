package fr.reseauredmi.mode

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent

object RadioInfoOpener {
    private val components = listOf(
        "com.android.phone" to "com.android.phone.settings.RadioInfo",
        "com.android.settings" to "com.android.settings.RadioInfo",
        "com.android.phone" to "com.android.phone.MobileNetworkSettings",
    )

    fun open(context: Context): Boolean {
        for ((pkg, cls) in components) {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                component = ComponentName(pkg, cls)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                continue
            } catch (_: SecurityException) {
                continue
            }
        }
        return false
    }
}
