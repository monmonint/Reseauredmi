package fr.reseauredmi.mode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!Prefs.reapplyOnBoot(context)) return
        val mode = Prefs.mode(context) ?: return
        val pending = goAsync()
        Thread {
            try {
                val controller = NetworkModeController(context.applicationContext)
                repeat(6) { attempt ->
                    if (ShizukuStatus.ready()) {
                        val outcome = controller.apply(mode, SimResolver.defaultSubId())
                        Log.i(TAG, "réapplique ${mode.name} -> ${outcome.level}")
                        return@Thread
                    }
                    if (attempt < 5) Thread.sleep(4_000)
                }
                Log.i(TAG, "Shizuku indisponible au démarrage, mode non réappliqué")
            } catch (error: Throwable) {
                Log.w(TAG, "échec au démarrage", error)
            } finally {
                pending.finish()
            }
        }.apply {
            name = "mode-boot"
            isDaemon = true
            start()
        }
    }

    private companion object {
        const val TAG = "Mode3G4G"
    }
}
