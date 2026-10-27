package fr.reseauredmi.mode

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class NetworkTileService : TileService() {
    override fun onStartListening() {
        render(Prefs.mode(this))
    }

    override fun onClick() {
        val next = if (Prefs.mode(this) == ForcedMode.WCDMA) ForcedMode.LTE else ForcedMode.WCDMA
        AppExecutors.io.execute {
            val outcome = NetworkModeController(applicationContext).apply(next, SimResolver.defaultSubId())
            Handler(Looper.getMainLooper()).post {
                if (outcome.level == ModeReport.Level.FAILED) {
                    openApp()
                } else {
                    render(next)
                }
            }
        }
    }

    private fun render(mode: ForcedMode?) {
        val tile = qsTile ?: return
        val adbReady = Prefs.adbLoopbackReady(this)
        val ready = adbReady || ShizukuStatus.ready()
        tile.label = when (mode) {
            ForcedMode.WCDMA -> "3G"
            ForcedMode.LTE -> "4G"
            null -> getString(R.string.tile_label)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                adbReady && mode == ForcedMode.WCDMA -> "WCDMA"
                adbReady && mode == ForcedMode.LTE -> "LTE"
                adbReady -> getString(R.string.tile_toggle)
                !ready -> getString(R.string.tile_setup)
                mode == ForcedMode.WCDMA -> "WCDMA"
                mode == ForcedMode.LTE -> "LTE"
                else -> getString(R.string.tile_toggle)
            }
        }
        tile.state = when {
            !ready -> Tile.STATE_INACTIVE
            mode == ForcedMode.WCDMA -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
