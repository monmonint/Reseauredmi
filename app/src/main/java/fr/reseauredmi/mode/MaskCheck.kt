package fr.reseauredmi.mode

import android.telephony.TelephonyManager

object MaskCheck {
    @Suppress("DEPRECATION")
    fun agreesWithSdk(): Boolean {
        return NetworkBits.GPRS == TelephonyManager.NETWORK_TYPE_BITMASK_GPRS &&
            NetworkBits.EDGE == TelephonyManager.NETWORK_TYPE_BITMASK_EDGE &&
            NetworkBits.UMTS == TelephonyManager.NETWORK_TYPE_BITMASK_UMTS &&
            NetworkBits.HSDPA == TelephonyManager.NETWORK_TYPE_BITMASK_HSDPA &&
            NetworkBits.HSUPA == TelephonyManager.NETWORK_TYPE_BITMASK_HSUPA &&
            NetworkBits.HSPA == TelephonyManager.NETWORK_TYPE_BITMASK_HSPA &&
            NetworkBits.HSPAP == TelephonyManager.NETWORK_TYPE_BITMASK_HSPAP &&
            NetworkBits.GSM == TelephonyManager.NETWORK_TYPE_BITMASK_GSM &&
            NetworkBits.LTE == TelephonyManager.NETWORK_TYPE_BITMASK_LTE &&
            NetworkBits.LTE_CA == TelephonyManager.NETWORK_TYPE_BITMASK_LTE_CA
    }
}
