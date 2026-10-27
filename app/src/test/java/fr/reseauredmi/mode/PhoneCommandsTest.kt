package fr.reseauredmi.mode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneCommandsTest {
    @Test
    fun setCommandUsesBinaryMaskAndSlot() {
        assertEquals(
            "/system/bin/cmd phone set-allowed-network-types-for-users -s 0 100001110000100",
            PhoneCommands.shell(PhoneCommands.setAllowed(0, NetworkBits.WCDMA_ONLY)),
        )
        assertEquals(
            "/system/bin/cmd phone set-allowed-network-types-for-users ${NetworkBits.LTE_PREFERRED.toString(2)}",
            PhoneCommands.shell(PhoneCommands.setAllowed(-1, NetworkBits.LTE_PREFERRED)),
        )
        assertFalse(PhoneCommands.shell(PhoneCommands.setAllowed(1, ForcedMode.LTE.mask)).contains("0b"))
        assertFalse(PhoneCommands.shell(PhoneCommands.setAllowed(1, ForcedMode.WCDMA.mask)).contains("NR"))
    }

    @Test
    fun settingsKeyMatchesSubscriptionWithoutUnderscore() {
        assertEquals(listOf("preferred_network_mode"), PhoneCommands.settingKeys(-1))
        assertEquals(
            listOf("preferred_network_mode", "preferred_network_mode12"),
            PhoneCommands.settingKeys(12),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun shellRejectsSpaces() {
        PhoneCommands.shell(arrayOf("/system/bin/echo", "deux mots"))
    }
}

class AdbEndpointTest {
    @Test
    fun pastedHostKeepsItsPort() {
        val parsed = AdbEndpoint.parseHost("192.168.1.23:37145")
        assertEquals("192.168.1.23", parsed.address)
        assertEquals(37145, parsed.port)
        assertTrue(AdbEndpoint.ipv4("10.0.0.2"))
        assertFalse(AdbEndpoint.ipv4("10.0.0"))
        assertFalse(AdbEndpoint.ipv4("127.0.0.1:5555"))
        assertTrue(AdbEndpoint.port(5555))
        assertFalse(AdbEndpoint.port(0))
    }
}

class AdbReportTest {
    @Test
    fun shellOutputsConfirmWcdma() {
        val raw = AdbReport.assemble(
            probe = "uid=2000(shell) gid=2000(shell)\n",
            grant = "",
            setOutput = "Set allowed network types for users completed.\n",
            settingPuts = listOf("", ""),
            getOutput = "For slot 0, the allowed network types are: UMTS|HSDPA|HSUPA|HSPA|HSPAP\n",
            settingGet = "2\n",
        )
        val report = ModeReport.parse("via=adb\n$raw")
        assertEquals(ModeReport.Level.CONFIRMED, ModeReport.level(report, ForcedMode.WCDMA))
        assertEquals(ForcedMode.WCDMA, ModeReport.inferred(report))
        assertEquals("2000", report.fields["uid"])
    }

    @Test
    fun mismatchedShellListFailsEvenIfSetClaimsSuccess() {
        val raw = AdbReport.assemble(
            probe = "uid=2000(shell)\n",
            grant = "",
            setOutput = "Set allowed network types for users completed.\n",
            settingPuts = listOf(""),
            getOutput = "GSM|GPRS|EDGE|UMTS|HSPA|LTE|LTE_CA\n",
            settingGet = "2\n",
        )
        assertEquals(ModeReport.Level.FAILED, ModeReport.level(ModeReport.parse(raw), ForcedMode.WCDMA))
    }

    @Test
    fun settingsAloneStaySent() {
        val raw = AdbReport.assemble(
            probe = "uid=2000(shell)\n",
            grant = "",
            setOutput = "Unknown command: set-allowed-network-types-for-users\n",
            settingPuts = listOf(""),
            getOutput = "",
            settingGet = "9\n",
        )
        val report = ModeReport.parse(raw)
        assertEquals(ModeReport.Level.SENT, ModeReport.level(report, ForcedMode.LTE))
        assertFalse(report.flag("shell_set") == true)
    }

    @Test
    fun settingsErrorIsNotAWrite() {
        val raw = AdbReport.assemble(
            probe = "uid=2000(shell)\n",
            grant = "",
            setOutput = "SecurityException: permission denied\n",
            settingPuts = listOf("SecurityException: permission denied"),
            getOutput = "",
            settingGet = "null\n",
        )
        assertEquals(ModeReport.Level.FAILED, ModeReport.level(ModeReport.parse(raw), ForcedMode.LTE))
    }
}
