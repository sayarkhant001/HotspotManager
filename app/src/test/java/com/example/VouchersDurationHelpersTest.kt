package com.example

import com.example.ui.screens.formatCompactDuration
import com.example.ui.screens.parseRouterOsUptimeSeconds
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards for the duration helpers powering the REAL remaining-time display on
 * used/partially-consumed voucher cards (continuous-countdown time left).
 */
class VouchersDurationHelpersTest {

    @Test
    fun parse_routeros_duration_formats() {
        assertEquals(93780L, parseRouterOsUptimeSeconds("1d2h3m")) // 86400+7200+180
        assertEquals(15120L, parseRouterOsUptimeSeconds("4h12m"))
        assertEquals(86399L, parseRouterOsUptimeSeconds("23:59:59"))
        assertEquals(604800L, parseRouterOsUptimeSeconds("1w"))
        assertEquals(90L, parseRouterOsUptimeSeconds("90s"))
        assertEquals(3600L, parseRouterOsUptimeSeconds("1h"))
    }

    @Test
    fun parse_empty_or_zero_duration_is_zero() {
        assertEquals(0L, parseRouterOsUptimeSeconds(""))
        assertEquals(0L, parseRouterOsUptimeSeconds("0s"))
        assertEquals(0L, parseRouterOsUptimeSeconds("  "))
    }

    @Test
    fun format_compact_duration_readable() {
        assertEquals("1d 2h 3m", formatCompactDuration(93780L))
        assertEquals("4h 12m", formatCompactDuration(15120L))
        assertEquals("23h 59m 59s", formatCompactDuration(86399L))
        assertEquals("1m 30s", formatCompactDuration(90L))
        assertEquals("45s", formatCompactDuration(45L))
        assertEquals("2d", formatCompactDuration(2L * 86400))
        assertEquals("0s", formatCompactDuration(0L))
    }
}