package com.example.domain.models

/**
 * Exact, authoritative voucher lifecycle status.
 *
 * Categories are mutually exclusive and exhaustive over the voucher set:
 *  - [USING]   – there is a live router hotspot session for this code right now.
 *  - [EXPIRED] – the voucher has been FULLY consumed: data quota finished
 *                ([USED:] >= [ORIG-LIMIT:]) OR continuous validity time elapsed
 *                ([ACT:]+validity), OR the router hard-marked it
 *                ([EXPIRED:] / [EXHAUSTED:] / [CUTOFF] / disabled + limit=1).
 *                A voucher is NEVER expired while it is still USING.
 *  - [UNUSED]  – everything else, including partially-consumed vouchers that
 *                still hold remaining quota/time (they are sellable again).
 */
enum class VoucherStatus { USING, EXPIRED, UNUSED }

/**
 * Pure resolver used by the Vouchers tab, the safety of "delete finished"
 * operations, the dashboard metrics and the JVM unit tests.
 */
object VoucherStatusResolver {

    /** Normalize a code like our router matching does: strip hyphens, trim, lowercase. */
    fun normalize(code: String?): String =
        (code ?: "").replace("-", "").trim().lowercase()

    fun isCurrentlyUsing(code: String, username: String, activeCodes: Set<String>): Boolean {
        if (activeCodes.isEmpty()) return false
        val codeNorm = normalize(code)
        val userNorm = normalize(username)
        val normActive = activeCodes.mapTo(HashSet()) { normalize(it) }
        return codeNorm.isNotBlank() && (normActive.contains(codeNorm) || (userNorm.isNotBlank() && normActive.contains(userNorm)))
    }

    /**
     * A voucher is EXPIRED only when it is provably exhausted.
     * It is NEVER expired while there is a live session using it.
     */
    fun isExpired(
        code: String,
        username: String,
        comment: String,
        activatedAt: Long?,
        validityDays: Int,
        isUsed: Boolean,
        dataLimitMb: Int,
        activeCodes: Set<String>
    ): Boolean {
        // 1. Live session = USING, never expired (protect active customers!).
        if (isCurrentlyUsing(code, username, activeCodes)) return false

        // 2. Router hard markers — 100% authoritative.
        val commentLower = comment.lowercase()
        if (comment.contains("[EXPIRED:", ignoreCase = true)) return true
        if (comment.contains("[EXHAUSTED:", ignoreCase = true)) return true
        if (comment.contains("[CUTOFF]", ignoreCase = true)) return true
        if (commentLower.contains("[expired]") || commentLower.contains("expired=true")) return true

        // 3. Quota fully consumed: cumulative [USED:] reached the original limit.
        //    (both tags must exist and be > 0 so we never guess.)
        val origLim = parseTagLong(comment, "[ORIG-LIMIT:")
        val used = parseTagLong(comment, "[USED:")
        if (origLim != null && origLim > 0L && used != null && used >= origLim) return true

        // 4. Continuous validity time elapsed since activation.
        //    [ACT:<epoch>] from the router is authoritative (set at first login, never reset).
        val actEpoch = parseTagLong(comment, "[ACT:") ?: activatedAt
        if (actEpoch != null && actEpoch > 0L && validityDays > 0) {
            val validityMs = validityDays * 24L * 60L * 60L * 1000L
            if (System.currentTimeMillis() - actEpoch >= validityMs) return true
        }

        // 5. A voucher consumed but with remainder is NOT expired (sellable again).
        //    Local activatedAt + validity fallback only when we have real activation proof.
        if (isUsed && dataLimitMb > 0 && (origLim == null || origLim <= 0L) && comment.contains("[ACT:", ignoreCase = true)) {
            // Under quota BUT comment carries [ACT:] — only time can finish it, handled above.
        }

        return false
    }

    fun statusOf(
        code: String,
        username: String,
        comment: String,
        activatedAt: Long?,
        validityDays: Int,
        isUsed: Boolean,
        dataLimitMb: Int,
        activeCodes: Set<String>
    ): VoucherStatus = when {
        isCurrentlyUsing(code, username, activeCodes) -> VoucherStatus.USING
        isExpired(code, username, comment, activatedAt, validityDays, isUsed, dataLimitMb, activeCodes) -> VoucherStatus.EXPIRED
        else -> VoucherStatus.UNUSED
    }

    private fun parseTagLong(comment: String, tag: String): Long? {
        val idx = comment.indexOf(tag, ignoreCase = true)
        if (idx < 0) return null
        val rest = comment.substring(idx + tag.length).takeBefore(']')
        return rest.trim().toLongOrNull()
    }

    private fun String.takeBefore(c: Char): String {
        val i = indexOf(c)
        return if (i < 0) this else substring(0, i)
    }
}