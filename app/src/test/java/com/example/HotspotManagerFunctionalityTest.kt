package com.example

import com.example.data.remote.MikrotikClient
import com.example.utils.VoucherPrinter
import org.junit.Assert.assertEquals
import org.junit.Test

class HotspotManagerFunctionalityTest {

    @Test
    fun testMikrotikUptimeFormatting_Minutes() {
        assertEquals("15m", MikrotikClient.formatMikrotikUptime(durationMinutes = 15, validityDays = 1))
        assertEquals("30m", MikrotikClient.formatMikrotikUptime(durationMinutes = 30, validityDays = 1))
        assertEquals("45m", MikrotikClient.formatMikrotikUptime(durationMinutes = 45, validityDays = 1))
    }

    @Test
    fun testMikrotikUptimeFormatting_Hours() {
        assertEquals("1h", MikrotikClient.formatMikrotikUptime(durationMinutes = 60, validityDays = 1))
        assertEquals("2h", MikrotikClient.formatMikrotikUptime(durationMinutes = 120, validityDays = 1))
        assertEquals("6h", MikrotikClient.formatMikrotikUptime(durationMinutes = 360, validityDays = 1))
        assertEquals("90m", MikrotikClient.formatMikrotikUptime(durationMinutes = 90, validityDays = 1))
    }

    @Test
    fun testMikrotikUptimeFormatting_Days() {
        assertEquals("1d", MikrotikClient.formatMikrotikUptime(durationMinutes = 1440, validityDays = 1))
        assertEquals("2d", MikrotikClient.formatMikrotikUptime(durationMinutes = 2880, validityDays = 2))
        assertEquals("7d", MikrotikClient.formatMikrotikUptime(durationMinutes = 0, validityDays = 7))
        assertEquals("30d", MikrotikClient.formatMikrotikUptime(durationMinutes = 0, validityDays = 30))
    }

    @Test
    fun testMikrotikUptimeFormatting_Unlimited() {
        assertEquals("", MikrotikClient.formatMikrotikUptime(durationMinutes = 0, validityDays = 0))
    }

    @Test
    fun testQuotaStringFormatting() {
        assertEquals("Unlim", VoucherPrinter.formatQuotaString(0))
        assertEquals("100MB", VoucherPrinter.formatQuotaString(100))
        assertEquals("500MB", VoucherPrinter.formatQuotaString(500))
        assertEquals("1GB", VoucherPrinter.formatQuotaString(1024))
        assertEquals("2GB", VoucherPrinter.formatQuotaString(2048))
        assertEquals("3GB", VoucherPrinter.formatQuotaString(3072))
        assertEquals("10GB", VoucherPrinter.formatQuotaString(10240))
    }

    @Test
    fun testValidityStringFormatting() {
        assertEquals("15M", VoucherPrinter.formatValidityString(durationMinutes = 15, validityDays = 1))
        assertEquals("30M", VoucherPrinter.formatValidityString(durationMinutes = 30, validityDays = 1))
        assertEquals("1H", VoucherPrinter.formatValidityString(durationMinutes = 60, validityDays = 1))
        assertEquals("2H", VoucherPrinter.formatValidityString(durationMinutes = 120, validityDays = 1))
        assertEquals("1D", VoucherPrinter.formatValidityString(durationMinutes = 1440, validityDays = 1))
        assertEquals("7D", VoucherPrinter.formatValidityString(durationMinutes = 0, validityDays = 7))
    }
}
