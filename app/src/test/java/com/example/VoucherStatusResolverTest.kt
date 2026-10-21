package com.example

import com.example.domain.models.Voucher
import com.example.domain.models.VoucherStatus
import com.example.domain.models.VoucherStatusResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Provable guarantee for the exact voucher classification:
 *  - USING   = code has a live session RIGHT NOW (normalized, hyphen/case-safe).
 *  - EXPIRED = ONLY quota-fulled or time-dued vouchers (router truth tags).
 *  - UNUSED  = everything else (incl. partially-consumed vouchers that still
 *              hold remaining quota/time -> still sellable stock).
 * Using + Expired + Unused always equals the total voucher count.
 */
class VoucherStatusResolverTest {

    private val now = System.currentTimeMillis()
    private val dayMs = 24L * 60 * 60 * 1000

    private fun voucher(
        code: String = "12345678",
        comment: String = "",
        activatedAt: Long? = null,
        validityDays: Int = 1,
        isUsed: Boolean = false,
        dataLimitMb: Int = 0
    ) = Voucher(
        code = code,
        username = code,
        profileName = "1GB",
        comment = comment,
        activatedAt = activatedAt,
        validityDays = validityDays,
        isUsed = isUsed,
        dataLimitMb = dataLimitMb
    )

    // -- 1. A LIVE session protects the voucher from EXPIRED, always --
    @Test
    fun using_voucher_is_never_expired_even_with_expired_markers() {
        val v = voucher(comment = "plan [EXHAUSTED:DATA] [EXPIRED:1] [USED:2000] [ORIG-LIMIT:1000]")
        val status = VoucherStatusResolver.statusOf(v.code, v.username, v.comment, v.activatedAt, v.validityDays, v.isUsed, v.dataLimitMb, setOf("12345678"))
        assertEquals(VoucherStatus.USING, status)
        assertFalse("live session must never be EXPIRED", VoucherStatusResolver.isExpired(v.code, v.username, v.comment, v.activatedAt, v.validityDays, v.isUsed, v.dataLimitMb, setOf("12345678")))
    }

    // -- 2. Quota fully consumed -> EXPIRED --
    @Test
    fun quota_full_is_expired() {
        val v = voucher(isUsed = true, comment = "1GB [USED:10485760] [ORIG-LIMIT:10485760]")
        assertTrue(VoucherStatusResolver.isExpired(v.code, v.username, v.comment, v.activatedAt, v.validityDays, v.isUsed, v.dataLimitMb, emptySet()))
        assertEquals(VoucherStatus.EXPIRED, VoucherStatusResolver.statusOf(v.code, v.username, v.comment, v.activatedAt, v.validityDays, v.isUsed, v.dataLimitMb, emptySet()))
    }

    // -- 3. Partial consumption with remainder -> still UNUSED (sellable) --
    @Test
    fun quota_remaining_is_not_expired() {
        val v = voucher(isUsed = true, comment = "1GB [USED:5242880] [ORIG-LIMIT:10485760]")
        assertFalse(VoucherStatusResolver.isExpired(v.code, v.username, v.comment, v.activatedAt, v.validityDays, v.isUsed, v.dataLimitMb, emptySet()))
        assertEquals(VoucherStatus.UNUSED, VoucherStatusResolver.statusOf(v.code, v.username, v.comment, v.activatedAt, v.validityDays, v.isUsed, v.dataLimitMb, emptySet()))
    }

    // -- 4. Continuous validity time elapsed -> EXPIRED --
    @Test
    fun time_due_is_expired() {
        // Router [ACT:...] is the authoritative activation anchor.
        val oldAct = now - 2 * dayMs
        val v = voucher(activatedAt = oldAct, validityDays = 1, isUsed = true, comment = "1D [ACT:$oldAct]")
        assertTrue(VoucherStatusResolver.isExpired(v.code, v.username, v.comment, v.activatedAt, v.validityDays, v.isUsed, v.dataLimitMb, emptySet()))
    }

    @Test
    fun time_not_due_is_not_expired() {
        val v = voucher(activatedAt = now - 1000, validityDays = 1, isUsed = true, comment = "1D [ACT:${now - 1000}]")
        assertFalse(VoucherStatusResolver.isExpired(v.code, v.username, v.comment, v.activatedAt, v.validityDays, v.isUsed, v.dataLimitMb, emptySet()))
        assertEquals(VoucherStatus.UNUSED, VoucherStatusResolver.statusOf(v.code, v.username, v.comment, v.activatedAt, v.validityDays, v.isUsed, v.dataLimitMb, emptySet()))
    }

    // -- 5. Router hard markers --
    @Test
    fun router_hard_markers_are_expired() {
        val exhausted = voucher(comment = "plan [EXHAUSTED:DATA]")
        assertTrue(VoucherStatusResolver.isExpired(exhausted.code, exhausted.username, exhausted.comment, exhausted.activatedAt, exhausted.validityDays, exhausted.isUsed, exhausted.dataLimitMb, emptySet()))
        val cutOff = voucher(comment = "plan [CUTOFF]")
        assertTrue(VoucherStatusResolver.isExpired(cutOff.code, cutOff.username, cutOff.comment, cutOff.activatedAt, cutOff.validityDays, cutOff.isUsed, cutOff.dataLimitMb, emptySet()))
        val expiredTag = voucher(comment = "plan [EXPIRED:1]")
        assertTrue(VoucherStatusResolver.isExpired(expiredTag.code, expiredTag.username, expiredTag.comment, expiredTag.activatedAt, expiredTag.validityDays, expiredTag.isUsed, expiredTag.dataLimitMb, emptySet()))
        val legacy = voucher(comment = "prof [expired]")
        assertTrue(VoucherStatusResolver.isExpired(legacy.code, legacy.username, legacy.comment, legacy.activatedAt, legacy.validityDays, legacy.isUsed, legacy.dataLimitMb, emptySet()))
    }

    // -- 6. Fresh / never-used stock --
    @Test
    fun fresh_voucher_is_unused() {
        assertEquals(VoucherStatus.UNUSED, VoucherStatusResolver.statusOf("87654321", "87654321", "", null, 1, false, 1024, emptySet()))
    }

    // -- 7. Session user normalization (hyphen + case) --
    @Test
    fun active_code_normalization() {
        assertTrue(VoucherStatusResolver.isCurrentlyUsing("AB12-3456", "AB12-3456", setOf("ab123456", "other")))
        assertTrue(VoucherStatusResolver.isCurrentlyUsing("9999-8888", "9999-8888", setOf("OTHER", "99998888")))
        assertFalse(VoucherStatusResolver.isCurrentlyUsing("1111-2222", "1111-2222", setOf("11112223")))
    }

    // -- 8. USING + EXPIRED + UNUSED is exhaustive & mutually exclusive --
    @Test
    fun status_partition_is_exhaustive() {
        val lu = voucher(code = "A0000001", comment = "1GB")
        val le = voucher(code = "B0000002", isUsed = true, comment = "1GB [USED:10485760] [ORIG-LIMIT:10485760]")
        val lt = voucher(code = "C0000003", isUsed = true, activatedAt = now - 2 * dayMs, validityDays = 1, comment = "1D [ACT:${now - 2 * dayMs}]")
        val lp = voucher(code = "D0000004", isUsed = true, comment = "1GB [USED:1048576] [ORIG-LIMIT:10485760]")
        val la = voucher(code = "E0000005", comment = "1GB [EXPIRED:1]")

        val using = setOf("E0000005") // E has hard expired tag BUT is currently online
        val statuses = listOf(lu, le, lt, lp, la).map {
            VoucherStatusResolver.statusOf(it.code, it.username, it.comment, it.activatedAt, it.validityDays, it.isUsed, it.dataLimitMb, using)
        }
        val usingCount = statuses.count { it == VoucherStatus.USING }
        val expiredCount = statuses.count { it == VoucherStatus.EXPIRED }
        val unusedCount = statuses.count { it == VoucherStatus.UNUSED }

        assertEquals("partition sum must equal total", statuses.size, usingCount + expiredCount + unusedCount)
        assertEquals(1, usingCount)   // E: online despite expired tag
        assertEquals(2, expiredCount) // B (quota) + C (time)
        assertEquals(2, unusedCount)  // A fresh + D partial-with-remainder
    }
}