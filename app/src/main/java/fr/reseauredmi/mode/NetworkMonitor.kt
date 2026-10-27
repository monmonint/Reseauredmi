package fr.reseauredmi.mode

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

class NetworkMonitor(
    context: Context,
    private val subId: () -> Int,
    private val onUpdate: (Snapshot) -> Unit,
) {
    data class Snapshot(val headline: String, val detail: String)

    private val appContext = context.applicationContext
    private val telephony = appContext.getSystemService(TelephonyManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var callback: TelephonyCallback? = null
    private var polling = false

    private val poll = object : Runnable {
        override fun run() {
            if (!polling) return
            publish()
            main.postDelayed(this, 2000)
        }
    }

    fun start() {
        if (polling) return
        polling = true
        publish()
        main.postDelayed(poll, 2000)
        register()
    }

    fun stop() {
        polling = false
        main.removeCallbacks(poll)
        unregister()
    }

    fun publishNow() = publish()

    private fun register() {
        if (android.os.Build.VERSION.SDK_INT < 31 || !canReadPhone()) return
        val created = object :
            TelephonyCallback(),
            TelephonyCallback.DisplayInfoListener,
            TelephonyCallback.DataConnectionStateListener {
            override fun onDisplayInfoChanged(telephonyDisplayInfo: android.telephony.TelephonyDisplayInfo) {
                publish()
            }

            override fun onDataConnectionStateChanged(state: Int, networkType: Int) {
                publish()
            }
        }
        try {
            telephony.registerTelephonyCallback(ContextCompat.getMainExecutor(appContext), created)
            callback = created
        } catch (_: SecurityException) {
            callback = null
        }
    }

    private fun unregister() {
        val current = callback ?: return
        callback = null
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            runCatching { telephony.unregisterTelephonyCallback(current) }
        }
    }

    private fun publish() {
        val snapshot = read()
        main.post { onUpdate(snapshot) }
    }

    private fun read(): Snapshot {
        if (airplane()) {
            return Snapshot("Mode avion", "Le réseau mobile est coupé")
        }
        if (!canReadPhone()) {
            return Snapshot("—", "Autorisez l’accès au téléphone pour afficher le type de réseau")
        }
        val chosen = subId()
        val manager = if (chosen != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            telephony.createForSubscriptionId(chosen)
        } else {
            telephony
        }
        val data = runCatching { manager.dataNetworkType }.getOrDefault(TelephonyManager.NETWORK_TYPE_UNKNOWN)
        val voice = runCatching { manager.voiceNetworkType }.getOrDefault(TelephonyManager.NETWORK_TYPE_UNKNOWN)
        if (data == TelephonyManager.NETWORK_TYPE_UNKNOWN &&
            voice == TelephonyManager.NETWORK_TYPE_UNKNOWN &&
            !simReady(manager)
        ) {
            return Snapshot("Pas de SIM", "Aucune carte SIM prête")
        }
        val shown = if (data != TelephonyManager.NETWORK_TYPE_UNKNOWN) data else voice
        val operator = manager.networkOperatorName?.takeIf { it.isNotBlank() }
            ?: manager.simOperatorName?.takeIf { it.isNotBlank() }
        val detail = when {
            data != TelephonyManager.NETWORK_TYPE_UNKNOWN &&
                voice != TelephonyManager.NETWORK_TYPE_UNKNOWN &&
                NetworkLabels.headline(data) != NetworkLabels.headline(voice) -> {
                val voiceText = join(NetworkLabels.headline(voice), NetworkLabels.technology(voice))
                val dataText = join(NetworkLabels.headline(data), NetworkLabels.technology(data))
                "Voix $voiceText · Données $dataText"
            }
            else -> listOfNotNull(operator, NetworkLabels.technology(shown).ifBlank { null })
                .joinToString(" · ")
                .ifBlank { "Type de réseau en cours de lecture" }
        }
        return Snapshot(NetworkLabels.headline(shown), detail)
    }

    private fun join(generation: String, technology: String): String {
        return if (technology.isBlank() || technology == generation) generation else "$generation $technology"
    }

    private fun airplane(): Boolean {
        return Settings.Global.getInt(appContext.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
    }

    private fun canReadPhone(): Boolean {
        val read = ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        val basic = if (android.os.Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(appContext, "android.permission.READ_BASIC_PHONE_STATE") ==
                PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
        return read || basic
    }

    private fun simReady(manager: TelephonyManager): Boolean {
        return try {
            manager.simState == TelephonyManager.SIM_STATE_READY
        } catch (_: SecurityException) {
            true
        }
    }
}
