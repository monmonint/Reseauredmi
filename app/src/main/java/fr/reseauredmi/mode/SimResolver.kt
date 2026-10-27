package fr.reseauredmi.mode

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

data class SimOption(val subId: Int, val slotIndex: Int, val label: String)

object SimResolver {
    fun defaultSubId(): Int {
        val data = SubscriptionManager.getDefaultDataSubscriptionId()
        if (data != SubscriptionManager.INVALID_SUBSCRIPTION_ID) return data
        return SubscriptionManager.getDefaultSubscriptionId()
    }

    fun slotIndex(subId: Int): Int {
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return -1
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                SubscriptionManager.getSlotIndex(subId)
            } else {
                -1
            }
        } catch (_: RuntimeException) {
            -1
        }
    }

    fun options(context: Context): List<SimOption> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return listOf(fallback())
        }
        val manager = context.getSystemService(SubscriptionManager::class.java) ?: return listOf(fallback())
        val infos = try {
            manager.activeSubscriptionInfoList.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }
        if (infos.isEmpty()) return listOf(fallback())
        val dataSub = SubscriptionManager.getDefaultDataSubscriptionId()
        return infos.map { info -> option(info, dataSub) }
    }

    private fun option(info: SubscriptionInfo, dataSubId: Int): SimOption {
        val slot = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            SubscriptionManager.getSlotIndex(info.subscriptionId)
        } else {
            info.simSlotIndex
        }
        val carrier = info.carrierName?.toString()?.takeIf { it.isNotBlank() }
        val sim = "SIM ${slot + 1}"
        val label = buildString {
            append(sim)
            if (carrier != null) {
                append(" · ")
                append(carrier)
            }
            if (info.subscriptionId == dataSubId) append(" · data")
        }
        return SimOption(info.subscriptionId, slot, label)
    }

    private fun fallback(): SimOption {
        val subId = defaultSubId()
        val slot = slotIndex(subId)
        val label = if (slot >= 0) "SIM ${slot + 1}" else "SIM data"
        return SimOption(subId, slot, label)
    }
}
