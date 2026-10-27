package fr.reseauredmi.mode

import android.content.Context

data class ApplyOutcome(
    val level: ModeReport.Level,
    val mode: ForcedMode,
    val detail: String,
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
        return try {
            val raw = ShizukuModeClient.apply(context, subId, SimResolver.slotIndex(subId), mode)
            val report = ModeReport.parse(raw)
            val level = ModeReport.level(report, mode)
            if (level != ModeReport.Level.FAILED) {
                Prefs.setMode(context, mode)
            }
            ApplyOutcome(level, mode, report.text())
        } catch (error: Throwable) {
            ApplyOutcome(ModeReport.Level.FAILED, mode, error.message ?: error.javaClass.simpleName)
        }
    }

    fun read(subId: Int): ModeReport.Report? {
        return try {
            ModeReport.parse(ShizukuModeClient.read(context, subId, SimResolver.slotIndex(subId)))
        } catch (_: Throwable) {
            null
        }
    }
}
