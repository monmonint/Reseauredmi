package fr.reseauredmi.mode

/**
 * Masques « network type bitmask » d'Android (1 << (type - 1)).
 * Ils correspondent à TelephonyManager.NETWORK_TYPE_BITMASK_* depuis Android 7
 * et à RadioAccessFamily.getRafFromNetworkType() depuis Android 11.
 *
 * 3G = WCDMA only (mode RIL 2) : UMTS, HSDPA, HSUPA, HSPA, HSPAP.
 * 4G = LTE/GSM/WCDMA (mode RIL 9) : LTE préféré, repli 3G/2G, sans 5G.
 */
object NetworkBits {
    fun typeBit(networkType: Int): Long = 1L shl (networkType - 1)

    val GPRS = typeBit(1)
    val EDGE = typeBit(2)
    val UMTS = typeBit(3)
    val HSDPA = typeBit(8)
    val HSUPA = typeBit(9)
    val HSPA = typeBit(10)
    val LTE = typeBit(13)
    val HSPAP = typeBit(15)
    val GSM = typeBit(16)
    val LTE_CA = typeBit(19)
    val NR = typeBit(20)

    val WCDMA_ONLY: Long = UMTS or HSDPA or HSUPA or HSPA or HSPAP

    val LTE_PREFERRED: Long = WCDMA_ONLY or GSM or GPRS or EDGE or LTE or LTE_CA
}

enum class ForcedMode(val ril: Int, val mask: Long) {
    WCDMA(2, NetworkBits.WCDMA_ONLY),
    LTE(9, NetworkBits.LTE_PREFERRED),
}
