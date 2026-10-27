package fr.reseauredmi.mode

import android.app.Application
import android.util.Log
import com.google.android.material.color.DynamicColors

class ModeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        if (!MaskCheck.agreesWithSdk()) {
            Log.e(TAG, "Les masques réseau ne correspondent pas au SDK")
        }
    }

    private companion object {
        const val TAG = "Mode3G4G"
    }
}
