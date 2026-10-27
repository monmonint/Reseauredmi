package fr.reseauredmi.mode

import android.content.Context

data class ApplyOutcome(
    val level: ModeReport.Level,
    val mode: ForcedMode,
    val detail: String,
    val needsSetup: Boolean = false,
)

class NetworkModeController(private val context: Context) {
    fun apply(mode: ForcedMode, subId: Int): ApplyOutcome {
        if (!MaskCheck.agreesWithSdk()) {
            return ApplyOutcome(
                ModeReport.Level.FAILED,
                mode,
                "Les masques réseau ne correspondent pas à cette version d’Android.",
            )
        }
        val slot = SimResolver.slotIndex(subId)
        when (val adb = AdbMode.apply(context, mode, subId, slot)) {
            is AdbMode.Attempt.Applied -> {
                if (adb.outcome.level != ModeReport.Level.FAILED) {
                    Prefs.setMode(context, mode)
                }
                return adb.outcome
            }
            is AdbMode.Attempt.Offline -> {
                if (ShizukuStatus.ready()) {
                    return applyWithShizuku(mode, subId, slot)
                }
                return ApplyOutcome(
                    ModeReport.Level.FAILED,
                    mode,
                    adb.message,
                    needsSetup = true,
                )
            }
        }
    }

    fun read(subId: Int): ModeReport.Report? {
        val slot = SimResolver.slotIndex(subId)
        AdbMode.read(context, subId, slot)?.let { return it }
        if (!ShizukuStatus.ready()) return null
        return try {
            ModeReport.parse("via=shizuku\n" + ShizukuModeClient.read(context, subId, slot))
        } catch (_: Throwable) {
            null
        }
    }

    private fun applyWithShizuku(mode: ForcedMode, subId: Int, slot: Int): ApplyOutcome {
        return try {
            val raw = ShizukuModeClient.apply(context, subId, slot, mode)
            val report = ModeReport.parse("via=shizuku\n$raw")
            val level = ModeReport.level(report, mode)
            if (level != ModeReport.Level.FAILED) {
                Prefs.setMode(context, mode)
            }
            ApplyOutcome(level, mode, report.text())
        } catch (error: Throwable) {
            ApplyOutcome(ModeReport.Level.FAILED, mode, error.message ?: error.javaClass.simpleName)
        }
    }
}
