package fr.reseauredmi.mode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModeReportTest {
    @Test
    fun masksMatchRadioInfoModes() {
        assertEquals(17284L, NetworkBits.WCDMA_ONLY)
        assertEquals(316295L, NetworkBits.LTE_PREFERRED)
        assertEquals("100001110000100", NetworkBits.WCDMA_ONLY.toString(2))
        assertEquals(NetworkBits.WCDMA_ONLY, NetworkBits.WCDMA_ONLY.toString(2).toLong(2))
        assertEquals(NetworkBits.LTE_PREFERRED, NetworkBits.LTE_PREFERRED.toString(2).toLong(2))
        assertEquals(0L, NetworkBits.WCDMA_ONLY and NetworkBits.LTE)
        assertEquals(0L, NetworkBits.WCDMA_ONLY and NetworkBits.GSM)
        assertEquals(0L, NetworkBits.LTE_PREFERRED and NetworkBits.NR)
        assertTrue(NetworkBits.LTE_PREFERRED and NetworkBits.LTE != 0L)
        assertTrue(NetworkBits.LTE_PREFERRED and NetworkBits.UMTS != 0L)
    }

    @Test
    fun allowedMaskIsSourceOfTruth() {
        val confirmed = report("allowed=${ForcedMode.WCDMA.mask}\nset_allowed=true\npreferred=9")
        assertEquals(ModeReport.Level.CONFIRMED, ModeReport.level(confirmed, ForcedMode.WCDMA))

        val rejected = report("allowed=${ForcedMode.LTE.mask}\nset_allowed=true\npreferred=2")
        assertEquals(ModeReport.Level.FAILED, ModeReport.level(rejected, ForcedMode.WCDMA))
    }

    @Test
    fun shellListConfirmsWhenMaskMissing() {
        val raw = "shell_get=UMTS|HSDPA|HSUPA|HSPA|HSPAP\n---\njournal"
        assertEquals(ModeReport.Level.CONFIRMED, ModeReport.level(ModeReport.parse(raw), ForcedMode.WCDMA))
        val lte = report("shell_get=GSM|GPRS|EDGE|UMTS|HSPA|LTE|LTE_CA")
        assertEquals(ModeReport.Level.CONFIRMED, ModeReport.level(lte, ForcedMode.LTE))
        assertEquals(ModeReport.Level.FAILED, ModeReport.level(lte, ForcedMode.WCDMA))
    }

    @Test
    fun preferredModeUsedOnOlderAndroid() {
        assertEquals(ModeReport.Level.CONFIRMED, ModeReport.level(report("preferred=2"), ForcedMode.WCDMA))
        assertEquals(ModeReport.Level.FAILED, ModeReport.level(report("preferred=9"), ForcedMode.WCDMA))
    }

    @Test
    fun setFlagConfirmsOnlyWithoutReadBack() {
        assertEquals(
            ModeReport.Level.CONFIRMED,
            ModeReport.level(report("set_allowed=true"), ForcedMode.LTE),
        )
        assertEquals(
            ModeReport.Level.SENT,
            ModeReport.level(report("settings_put=true\nsettings=9"), ForcedMode.LTE),
        )
        assertEquals(ModeReport.Level.FAILED, ModeReport.level(report("settings_put=false"), ForcedMode.LTE))
    }

    @Test
    fun errorTextIsNotANetworkList() {
        val raw = "shell_get=\n---\nUnknown command: set-allowed-network-types-for-users"
        val parsed = ModeReport.parse(raw)
        assertFalse(ModeReport.looksLikeNetworkList(parsed.log))
        assertNull(ModeReport.inferred(parsed))
        assertEquals(ModeReport.Level.FAILED, ModeReport.level(parsed, ForcedMode.LTE))
    }

    @Test
    fun inferredModeFollowsMaskThenPreferred() {
        assertEquals(ForcedMode.WCDMA, ModeReport.inferred(report("allowed=${ForcedMode.WCDMA.mask}")))
        assertEquals(ForcedMode.LTE, ModeReport.inferred(report("preferred=9")))
        assertEquals(ForcedMode.WCDMA, ModeReport.inferred(report("shell_get=WCDMA|UMTS|HSPA")))
    }

    private fun report(fields: String) = ModeReport.parse(fields)
}
