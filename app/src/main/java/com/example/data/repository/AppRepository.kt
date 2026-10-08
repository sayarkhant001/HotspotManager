package com.example.data.repository

import com.example.data.local.RouterDao
import com.example.domain.models.ActiveUser
import com.example.data.remote.MikrotikClient
import com.example.domain.models.RouterSessionLog
import com.example.domain.models.UserProfile
import com.example.domain.models.Voucher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONObject

class AppRepository(
    private val dao: RouterDao,
    val mikrotikClient: MikrotikClient,
    private val context: android.content.Context? = null
) {
    val profiles: Flow<List<UserProfile>> = dao.getAllProfiles()
    val vouchers: Flow<List<Voucher>> = dao.getAllVouchers()
    val sessions: Flow<List<RouterSessionLog>> = dao.getAllSessions()

    suspend fun ensureConnected(): Boolean {
        if (mikrotikClient.isConnected()) return true
        if (mikrotikClient.ensureConnected()) return true
        if (context != null) {
            try {
                val prefs = context.getSharedPreferences("hotspot_login_prefs", android.content.Context.MODE_PRIVATE)
                val ip = prefs.getString("router_ip", null)
                val user = prefs.getString("router_user", "admin") ?: "admin"
                val pass = prefs.getString("router_pass", "") ?: ""
                if (!ip.isNullOrBlank()) {
                    val res = mikrotikClient.connect(ip, user, pass)
                    return res.isSuccess
                }
            } catch (_: Exception) {}
        }
        return false
    }

    fun getSessionsByDate(dateKey: String): Flow<List<RouterSessionLog>> = dao.getSessionsByDate(dateKey)
    fun getSessionsBetween(start: Long, end: Long): Flow<List<RouterSessionLog>> = dao.getSessionsBetween(start, end)
    fun getVouchersByProfile(profileName: String): Flow<List<Voucher>> = dao.getVouchersByProfile(profileName)

    suspend fun changeRouterPassword(oldPass: String, newPass: String): Result<Unit> {
        return mikrotikClient.changeUserPassword(oldPass, newPass)
    }

    suspend fun getHotspotLoginUrl(): String {
        val detected = try {
            mikrotikClient.getHotspotServerDnsName()
        } catch (_: Exception) {
            null
        }
        val target = if (!detected.isNullOrBlank()) detected else "10.10.10.1"
        return if (target.startsWith("http://") || target.startsWith("https://")) target else "http://$target"
    }

    suspend fun getHotspotSsid(): String? {
        return try {
            mikrotikClient.getRouterSsid()
        } catch (_: Exception) {
            null
        }
    }

    data class ProfileSyncMeta(
        val price: Double = 0.0,
        val sellingPrice: Double = 0.0,
        val validityDays: Int = 1,
        val dataLimitMb: Int = 0,
        val durationMinutes: Int = 0
    )

    private fun parseMetadataJson(rawJson: String?): Map<String, ProfileSyncMeta> {
        if (rawJson.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<String, ProfileSyncMeta>()
        try {
            val root = JSONObject(rawJson)
            val keys = root.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                val item = root.optJSONObject(name)
                if (item != null) {
                    val p = item.optDouble("price", 0.0)
                    result[name] = ProfileSyncMeta(
                        price = p,
                        sellingPrice = item.optDouble("sellingPrice", p),
                        validityDays = item.optInt("validityDays", 1),
                        dataLimitMb = item.optInt("dataLimitMb", 0),
                        durationMinutes = item.optInt("durationMinutes", 0)
                    )
                }
            }
        } catch (_: Exception) {}
        return result
    }

    suspend fun syncMetadataToRouter(profilesList: List<UserProfile>) {
        if (!ensureConnected()) return
        try {
            val root = JSONObject()
            profilesList.forEach { p ->
                val obj = JSONObject()
                obj.put("price", p.price)
                obj.put("sellingPrice", p.sellingPrice)
                obj.put("validityDays", p.validityDays)
                obj.put("dataLimitMb", p.dataLimitMb)
                obj.put("durationMinutes", p.durationMinutes)
                root.put(p.name, obj)
            }
            mikrotikClient.saveRouterScriptSource(
                scriptName = "wexz_profiles_meta",
                source = root.toString(),
                comment = "HotspotManager Profiles & Price Tags"
            )
        } catch (_: Exception) {}
    }

    suspend fun addProfile(profile: UserProfile): Result<Unit> {
        if (!ensureConnected()) {
            return Result.failure(Exception("Router is not connected. Cannot add profile."))
        }
        val sessionTimeout = MikrotikClient.formatMikrotikUptime(profile.durationMinutes, profile.validityDays)
        val res = mikrotikClient.addRouterProfile(profile.name, profile.rateLimit, profile.sharedUsers, sessionTimeout)
        if (res.isFailure) {
            return res
        }
        val existing = dao.getProfileByName(profile.name)
        if (existing != null) {
            dao.updateProfileByName(
                oldName = profile.name,
                id = existing.id,
                newName = profile.name,
                sharedUsers = profile.sharedUsers,
                rateLimit = profile.rateLimit,
                downloadLimitMbps = profile.downloadLimitMbps,
                uploadLimitMbps = profile.uploadLimitMbps,
                dataLimitMb = profile.dataLimitMb,
                durationMinutes = profile.durationMinutes,
                price = profile.price,
                sellingPrice = profile.sellingPrice,
                validityDays = profile.validityDays
            )
        } else {
            dao.insertProfile(profile)
        }
        syncMetadataToRouter(dao.getAllProfilesSync())
        return Result.success(Unit)
    }

    suspend fun syncProfilesFromRouter() {
        if (!ensureConnected()) return

        val routerProfiles = mikrotikClient.getRouterProfiles()
        if (routerProfiles.isEmpty()) return

        val existingProfiles = dao.getAllProfilesSync().associateBy { it.name }

        // 1. Fetch persistent profiles metadata from RouterOS /system/script
        val routerMetaRaw = mikrotikClient.getRouterScriptSource("wexz_profiles_meta")
        val routerMeta = parseMetadataJson(routerMetaRaw)

        val list = routerProfiles.map { rp ->
            val existing = existingProfiles[rp.name]
            val meta = routerMeta[rp.name]
            val defaults = getDefaultProfilePriceAndQuota(rp.name)

            // Duration: RouterOS session-timeout is the authoritative active router setting!
            val routerUptimeMins = MikrotikClient.parseMikrotikUptimeToMinutes(rp.sessionTimeout)
            val duration = when {
                // If RouterOS has an explicit session-timeout (e.g. "2h", "1d", "30m"), that takes priority!
                routerUptimeMins > 0 -> routerUptimeMins
                // If RouterOS explicitly set session-timeout=none, duration is 0 (unlimited)
                rp.sessionTimeout.equals("none", ignoreCase = true) || rp.sessionTimeout == "0s" -> 0
                meta != null && meta.durationMinutes > 0 -> meta.durationMinutes
                existing != null && existing.durationMinutes > 0 -> existing.durationMinutes
                else -> defaults.third * 1440
            }

            val validity = when {
                duration >= 1440 -> duration / 1440
                duration in 1..1439 -> 1
                meta != null && meta.validityDays > 0 -> meta.validityDays
                existing != null && existing.validityDays > 0 -> existing.validityDays
                else -> defaults.third
            }

            val (upSpeed, downSpeed) = MikrotikClient.parseRateLimits(rp.rateLimit)

            val price = when {
                meta != null && (meta.price != meta.dataLimitMb.toDouble() || defaults.first == 0.0) -> meta.price
                defaults.first > 0 -> defaults.first
                existing != null && existing.price > 0.0 && existing.price != existing.dataLimitMb.toDouble() -> existing.price
                else -> defaults.first
            }
            val sellingPrice = when {
                meta != null && (meta.sellingPrice != meta.dataLimitMb.toDouble() || defaults.first == 0.0) -> meta.sellingPrice
                defaults.first > 0 -> defaults.first
                existing != null && existing.sellingPrice > 0.0 && existing.sellingPrice != existing.dataLimitMb.toDouble() -> existing.sellingPrice
                else -> price
            }
            val quota = when {
                meta != null -> meta.dataLimitMb
                existing != null && existing.dataLimitMb > 0 -> existing.dataLimitMb
                else -> defaults.second
            }

            val existingId = existing?.id ?: 0

            UserProfile(
                id = existingId,
                name = rp.name,
                rateLimit = rp.rateLimit,
                sharedUsers = rp.sharedUsers.toIntOrNull() ?: 1,
                downloadLimitMbps = downSpeed,
                uploadLimitMbps = upSpeed,
                dataLimitMb = quota,
                durationMinutes = duration,
                price = price,
                sellingPrice = sellingPrice,
                validityDays = validity
            )
        }

        // Remove profiles from local DB that no longer exist on the router
        val routerProfileNames = routerProfiles.map { it.name }.toSet()
        val toDelete = existingProfiles.keys - routerProfileNames
        for (delName in toDelete) {
            dao.deleteProfileByName(delName)
        }

        // Upsert all router profiles into Room DB without wiping or corrupting IDs
        dao.insertProfiles(list)

        // Always ensure the router script has all profiles consolidated
        syncMetadataToRouter(dao.getAllProfilesSync())
    }

    private fun getDefaultProfilePriceAndQuota(name: String): Triple<Double, Int, Int> {
        val upper = name.uppercase()
        val ksMatch = Regex("(\\d+)\\s*(?:KS|MMK|KYAT)").find(upper)
        val ks = ksMatch?.groupValues?.get(1)?.toDoubleOrNull()
        if (ks != null && ks > 0) {
            val quotaMb = when (ks.toInt()) {
                500 -> 750
                1000 -> 1700
                2000 -> 3500
                3000 -> 7168
                5000 -> 15360
                10000 -> 30720
                else -> (ks * 1.5).toInt()
            }
            return Triple(ks, quotaMb, 1)
        }
        val gbMatch = Regex("(\\d+)\\s*GB").find(upper)
        val gb = gbMatch?.groupValues?.get(1)?.toIntOrNull()
        if (gb != null) {
            val mb = gb * 1024
            val price = (gb * 500.0).coerceAtLeast(500.0)
            return Triple(price, mb, 30)
        }
        val mbMatch = Regex("(\\d+)\\s*MB").find(upper)
        val mb = mbMatch?.groupValues?.get(1)?.toIntOrNull()
        if (mb != null) {
            return Triple(300.0, mb, 1)
        }
        val hrMatch = Regex("(\\d+)\\s*(?:HOUR|HR|H)").find(upper)
        val hr = hrMatch?.groupValues?.get(1)?.toIntOrNull()
        if (hr != null) {
            val price = (hr * 300.0).coerceAtLeast(300.0)
            return Triple(price, 0, 1)
        }
        return when {
            upper.contains("30D") -> Triple(10000.0, 30720, 30)
            upper.contains("UNLIM") && upper.contains("30") -> Triple(20000.0, 0, 30)
            upper.contains("UNLIM") -> Triple(5000.0, 0, 7)
            else -> Triple(0.0, 0, 1)
        }
    }

    private fun parseRateLimitMbps(rate: String): Int {
        if (rate.isBlank()) return 5
        val parts = rate.split("/")
        val dl = parts.getOrNull(0)?.trim() ?: ""
        return when {
            dl.endsWith("M", ignoreCase = true) -> dl.dropLast(1).toIntOrNull() ?: 5
            dl.endsWith("k", ignoreCase = true) -> (dl.dropLast(1).toIntOrNull() ?: 5000) / 1000
            else -> dl.toIntOrNull() ?: 5
        }
    }

    suspend fun deleteProfile(profileName: String): Result<Unit> {
        if (profileName.equals("default", ignoreCase = true)) {
            return Result.failure(Exception("Cannot delete default profile."))
        }
        if (!ensureConnected()) {
            return Result.failure(Exception("Router is not connected. Cannot delete profile."))
        }
        val ok = mikrotikClient.deleteRouterProfile(profileName)
        if (!ok) {
            return Result.failure(Exception("Router rejected deleting profile '$profileName'."))
        }
        dao.deleteProfileByName(profileName)
        dao.deleteVouchersByProfile(profileName)
        syncMetadataToRouter(dao.getAllProfilesSync())
        return Result.success(Unit)
    }

    suspend fun updateProfile(oldName: String, profile: UserProfile): Result<Unit> {
        if (!ensureConnected()) {
            return Result.failure(Exception("Router is not connected. Cannot update profile."))
        }
        val sessionTimeout = MikrotikClient.formatMikrotikUptime(profile.durationMinutes, profile.validityDays)
        val limitBytesTotal = if (profile.dataLimitMb > 0) profile.dataLimitMb.toLong() * 1024L * 1024L else 0L
        val limitUptime = sessionTimeout

        val res = mikrotikClient.updateRouterProfile(
            oldName = oldName,
            newName = profile.name,
            rateLimit = profile.rateLimit,
            sharedUsers = profile.sharedUsers,
            sessionTimeout = sessionTimeout,
            limitBytesTotal = limitBytesTotal,
            limitUptime = limitUptime
        )
        if (res.isFailure) {
            return res
        }
        dao.updateProfileByName(
            oldName = oldName,
            id = profile.id,
            newName = profile.name,
            sharedUsers = profile.sharedUsers,
            rateLimit = profile.rateLimit,
            downloadLimitMbps = profile.downloadLimitMbps,
            uploadLimitMbps = profile.uploadLimitMbps,
            dataLimitMb = profile.dataLimitMb,
            durationMinutes = profile.durationMinutes,
            price = profile.price,
            sellingPrice = profile.sellingPrice,
            validityDays = profile.validityDays
        )
        val exists = dao.getAllProfilesSync().any { it.name.equals(profile.name, ignoreCase = true) }
        if (!exists) {
            dao.insertProfile(profile)
        }
        // Align all existing local vouchers in Room DB with the new profile adjustments
        dao.updateVouchersForProfile(
            oldName = oldName,
            newName = profile.name,
            downloadLimitMbps = profile.downloadLimitMbps,
            uploadLimitMbps = profile.uploadLimitMbps,
            dataLimitMb = profile.dataLimitMb,
            durationMinutes = profile.durationMinutes,
            validityDays = profile.validityDays,
            price = profile.price
        )
        syncMetadataToRouter(dao.getAllProfilesSync())
        return Result.success(Unit)
    }

    suspend fun removeProfilesWithZeroVouchers(): List<String> {
        val removed = mutableListOf<String>()
        val allProfiles = dao.getAllProfilesSync()
        for (profile in allProfiles) {
            if (profile.name.equals("default", ignoreCase = true)) continue
            val count = dao.getVoucherCountForProfile(profile.name)
            if (count == 0) {
                deleteProfile(profile.name)
                removed.add(profile.name)
            }
        }
        return removed
    }

    private val voucherBaseMap = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Long>>()
    private val lastVoucherLiveMap = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Long>>()

    suspend fun syncVouchersFromRouter(): Int {
        if (!mikrotikClient.isConnected()) {
            mikrotikClient.ensureConnected()
        }
        if (!mikrotikClient.isConnected()) return 0

        var profilesList = dao.getAllProfilesSync()
        if (profilesList.isEmpty()) {
            syncProfilesFromRouter()
            profilesList = dao.getAllProfilesSync()
        }
        val profileMap = profilesList.associateBy { it.name }

        val routerUsers = mikrotikClient.getAllHotspotUsers()
        if (routerUsers.isEmpty()) return 0

        val existingVouchersMap = dao.getAllVouchersSync().associateBy { it.code }
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Yangon")
        }
        val dateTimeFormat1 = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Yangon")
        }
        val dateTimeFormat2 = SimpleDateFormat("MMM/dd/yyyy HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Yangon")
        }

        fun parseActivationTimestamp(comment: String): Long? {
            val actIdx = comment.indexOf("[ACT:")
            if (actIdx >= 0) {
                val endIdx = comment.indexOf("]", actIdx)
                val rawDate = if (endIdx > actIdx) comment.substring(actIdx + 5, endIdx).trim() else comment.substring(actIdx + 5).trim()
                try {
                    val t = dateTimeFormat1.parse(rawDate)?.time
                    if (t != null) return t
                } catch (_: Exception) {}
                try {
                    val t = dateTimeFormat2.parse(rawDate)?.time
                    if (t != null) return t
                } catch (_: Exception) {}
                try {
                    val t = dateFormat.parse(rawDate)?.time
                    if (t != null) return t
                } catch (_: Exception) {}
            }
            return null
        }

        fun parseUptimeSeconds(uptime: String): Long {
            var total = 0L
            val clean = uptime.trim().lowercase()
            val regex = Regex("(\\d+)([wdhms])")
            regex.findAll(clean).forEach { m ->
                val v = m.groupValues[1].toLongOrNull() ?: 0L
                when (m.groupValues[2]) {
                    "w" -> total += v * 7 * 86400
                    "d" -> total += v * 86400
                    "h" -> total += v * 3600
                    "m" -> total += v * 60
                    "s" -> total += v
                }
            }
            return total
        }

        val activeUsersList = try {
            getActiveHotspotUsers()
        } catch (_: Exception) {
            emptyList<com.example.domain.models.ActiveUser>()
        }
        val activeUsersMap = activeUsersList.associateBy { it.user }

        val vouchers = routerUsers.map { u ->
            var prof = profileMap[u.profile]
                ?: profilesList.firstOrNull { it.name.equals(u.profile, ignoreCase = true) }
            if (prof == null) {
                val commentClean = u.comment.replace(" ", "").lowercase()
                prof = profilesList.firstOrNull { p ->
                    val pNameClean = p.name.replace(" ", "").lowercase()
                    pNameClean.isNotBlank() && (commentClean.contains(pNameClean) || pNameClean.contains(commentClean))
                }
            }

            val isAcc = u.password.isNotBlank() && u.password != u.name
            val totalBytes = u.limitBytesTotal
            val defaults = getDefaultProfilePriceAndQuota(if (prof != null) prof.name else if (u.profile != "default") u.profile else u.comment)
            val existing = existingVouchersMap[u.name]
            val parsedActTime = parseActivationTimestamp(u.comment)
            val parsedUsedBytes = parseUsedBytesFromComment(u.comment) ?: 0L
            val parsedBaseBytes = parseBaseBytesFromComment(u.comment) ?: 0L
            val parsedOrigBytes = parseOrigLimitFromComment(u.comment)

            val mb = when {
                parsedOrigBytes != null && parsedOrigBytes > 0L -> (parsedOrigBytes / (1024 * 1024)).toInt()
                prof != null && prof.dataLimitMb > 0 -> prof.dataLimitMb
                defaults.second > 0 -> defaults.second
                existing != null && existing.dataLimitMb > 0 -> existing.dataLimitMb
                totalBytes > 0 -> {
                    val fullBytes = if (parsedUsedBytes > 0L && totalBytes < 100L * 1024 * 1024 * 1024) totalBytes + parsedUsedBytes else totalBytes
                    (fullBytes / (1024 * 1024)).toInt()
                }
                else -> 0
            }
            val uptimeSec = parseUptimeSeconds(u.uptime)

            val activeUser = activeUsersMap[u.name]
            val liveIn = activeUser?.bytesIn?.toLongOrNull() ?: 0L
            val liveOut = activeUser?.bytesOut?.toLongOrNull() ?: 0L
            val liveTotal = liveIn + liveOut
            val liveUpSec = parseUptimeSeconds(activeUser?.uptime ?: "")

            // Once used or activated, isUsed NEVER resets to false on reboot
            val isUsed = (existing?.isUsed == true) ||
                         (activeUser != null) ||
                         (parsedActTime != null) ||
                         (parsedUsedBytes > 0L) ||
                         (u.uptime != "0s" && u.uptime.isNotBlank()) ||
                         (liveUpSec > 0L) ||
                         (u.bytesOut > 0L) ||
                         (u.bytesIn > 0L) ||
                         (liveTotal > 0L)

            val isPrinted = u.comment.contains("PRINTED", ignoreCase = true) || (existing?.isPrinted == true)

            // Calculate continuous cumulative data bytes (resilient to router reboot / counter reset)
            val sessionIn = u.bytesIn
            val sessionOut = u.bytesOut
            val sessionTotal = sessionIn + sessionOut

            val existingIn = existing?.bytesIn ?: 0L
            val existingOut = existing?.bytesOut ?: 0L
            val existingTotal = existingIn + existingOut

            // Total bytes reported by router (hardware balance, or base + current boot traffic or live session)
            val hardwareUsed = if (parsedOrigBytes != null && parsedOrigBytes > 0L && u.limitBytesTotal > 0L && u.limitBytesTotal <= parsedOrigBytes) {
                parsedOrigBytes - u.limitBytesTotal
            } else 0L

            // Router recorded used bytes:
            // When liveTotal > 0 (active session): active bytes are not yet flushed to user sessionTotal,
            // so we add liveTotal to baseBytes or use the live comment tag parsedUsedBytes.
            // When disconnected (liveTotal == 0): RouterOS already flushed finished session into sessionTotal,
            // so sessionTotal, parsedUsedBytes, and parsedBaseBytes represent the SAME traffic - take maxOf, NEVER add!
            val routerRecordedUsed = if (liveTotal > 0L) {
                maxOf(parsedUsedBytes, parsedBaseBytes + liveTotal, sessionTotal + liveTotal, liveTotal)
            } else {
                maxOf(sessionTotal, parsedUsedBytes, parsedBaseBytes)
            }

            val totalCumulative = when {
                hardwareUsed > 0L -> maxOf(hardwareUsed, routerRecordedUsed)
                routerRecordedUsed > 0L -> routerRecordedUsed
                existingTotal > 0L -> existingTotal
                else -> 0L
            }

            val activeOrSessionIn = if (liveTotal > 0L) liveIn else sessionIn
            val activeOrSessionTotal = if (liveTotal > 0L) liveTotal else sessionTotal
            val effectiveBytesIn: Long
            val effectiveBytesOut: Long
            if (activeOrSessionTotal > 0L) {
                val ratioIn = activeOrSessionIn.toDouble() / activeOrSessionTotal
                val inBytes = (totalCumulative * ratioIn).toLong()
                effectiveBytesIn = inBytes
                effectiveBytesOut = totalCumulative - inBytes
            } else if (existingTotal > 0L && totalCumulative == existingTotal) {
                effectiveBytesIn = existingIn
                effectiveBytesOut = existingOut
            } else {
                effectiveBytesIn = totalCumulative / 2
                effectiveBytesOut = totalCumulative - effectiveBytesIn
            }

            val activatedAt = when {
                parsedActTime != null -> parsedActTime
                existing?.activatedAt != null -> existing.activatedAt
                isUsed && uptimeSec > 0 -> System.currentTimeMillis() - (uptimeSec * 1000L)
                isUsed -> System.currentTimeMillis()
                existing?.isUsed == true && existing.generatedAt > 0L -> existing.generatedAt
                else -> null
            }

            val generatedAt = when {
                existing != null && existing.generatedAt > 0L -> existing.generatedAt
                activatedAt != null -> activatedAt
                else -> System.currentTimeMillis()
            }

            val rawPrice = when {
                existing != null && existing.price > 0.0 -> existing.price
                prof != null && prof.sellingPrice > 0 -> prof.sellingPrice
                prof != null && prof.price > 0 -> prof.price
                defaults.first > 0 -> defaults.first
                else -> 0.0
            }
            val price = com.example.utils.VoucherPrinter.resolveVoucherPrice(
                Voucher(code = u.name, profileName = u.profile, comment = u.comment, price = rawPrice, dataLimitMb = mb)
            )

            val usedUpSec = parseUsedUptimeSeconds(u.comment) ?: 0L
            val sessionUpSec = parseUptimeSeconds(u.uptime)
            val existingUpSec = parseUptimeSeconds(existing?.uptime ?: "")
            val baseUpSec = parseBaseUptimeSeconds(u.comment) ?: 0L
            val totalUpSec = maxOf(usedUpSec, sessionUpSec, existingUpSec, liveUpSec, sessionUpSec + baseUpSec)
            val effectiveUptime = if (totalUpSec > 0) formatUptimeSeconds(totalUpSec) else (activeUser?.uptime ?: u.uptime)

            Voucher(
                id = existing?.id ?: 0,
                code = u.name,
                username = u.name,
                password = u.password.ifBlank { u.name },
                isAccount = isAcc,
                profileId = prof?.id ?: 0,
                profileName = u.profile,
                downloadLimitMbps = prof?.downloadLimitMbps ?: 0,
                uploadLimitMbps = prof?.uploadLimitMbps ?: 0,
                dataLimitMb = mb,
                durationMinutes = prof?.durationMinutes ?: (defaults.third * 1440),
                validityDays = prof?.validityDays ?: defaults.third,
                price = price,
                generatedAt = generatedAt,
                activatedAt = activatedAt,
                isUsed = isUsed,
                isPrinted = isPrinted,
                comment = u.comment,
                bytesIn = effectiveBytesIn,
                bytesOut = effectiveBytesOut,
                uptime = effectiveUptime
            )
        }

        // Upsert vouchers without blindly clearing the table (strictly deduplicated by code)
        val deduplicatedVouchers = vouchers.distinctBy { it.code }
        deduplicatedVouchers.chunked(250).forEach { chunk ->
            dao.insertVouchers(chunk)
        }
        val currentRouterCodes = routerUsers.map { it.name }.toSet()
        // Never delete used/activated vouchers when router prunes them upon expiration; they must stay in the app as expired!
        val removedCodes = existingVouchersMap.filter { (code, v) ->
            code !in currentRouterCodes && !v.isUsed && (v.uptime.isBlank() || v.uptime == "0s") && v.bytesIn == 0L && v.bytesOut == 0L
        }.keys.toList()
        if (removedCodes.isNotEmpty()) {
            dao.deleteVouchersByCodes(removedCodes)
        }
        // Clean out any synthetic fake sessions from previous buggy versions
        dao.clearSyntheticVoucherSessions()
        // Automatically prune vouchers expired 30+ days ago (non-disruptive, preserves active users)
        autoPruneExpiredVouchers()
        return vouchers.size
    }

    fun parseOrigLimitFromComment(comment: String): Long? {
        val idx = comment.indexOf("[ORIG-LIMIT:")
        if (idx >= 0) {
            val endIdx = comment.indexOf("]", idx)
            val raw = if (endIdx > idx) comment.substring(idx + 12, endIdx).trim() else comment.substring(idx + 12).trim()
            return raw.toLongOrNull()
        }
        return null
    }

    fun parseUsedUptimeSeconds(comment: String): Long? {
        val idx = comment.indexOf("[USED-UP:")
        if (idx >= 0) {
            val endIdx = comment.indexOf("]", idx)
            val raw = if (endIdx > idx) comment.substring(idx + 9, endIdx).trim() else comment.substring(idx + 9).trim()
            return parseUptimeSeconds(raw)
        }
        val bIdx = comment.indexOf("[BASE-UP:")
        if (bIdx >= 0) {
            val endIdx = comment.indexOf("]", bIdx)
            val raw = if (endIdx > bIdx) comment.substring(bIdx + 9, endIdx).trim() else comment.substring(bIdx + 9).trim()
            return parseUptimeSeconds(raw)
        }
        return null
    }

    fun parseBaseUptimeSeconds(comment: String): Long? {
        val bIdx = comment.indexOf("[BASE-UP:")
        if (bIdx >= 0) {
            val endIdx = comment.indexOf("]", bIdx)
            val raw = if (endIdx > bIdx) comment.substring(bIdx + 9, endIdx).trim() else comment.substring(bIdx + 9).trim()
            return parseUptimeSeconds(raw)
        }
        return null
    }

    fun parseUptimeSeconds(uptime: String): Long {
        var total = 0L
        val clean = uptime.trim().lowercase()
        if (clean.contains(":")) {
            val parts = clean.split(":")
            if (parts.size == 3) {
                val h = parts[0].toLongOrNull() ?: 0L
                val m = parts[1].toLongOrNull() ?: 0L
                val s = parts[2].toLongOrNull() ?: 0L
                return h * 3600 + m * 60 + s
            } else if (parts.size == 2) {
                val m = parts[0].toLongOrNull() ?: 0L
                val s = parts[1].toLongOrNull() ?: 0L
                return m * 60 + s
            }
        }
        val regex = Regex("(\\d+)([wdhms])")
        regex.findAll(clean).forEach { m ->
            val v = m.groupValues[1].toLongOrNull() ?: 0L
            when (m.groupValues[2]) {
                "w" -> total += v * 7 * 86400
                "d" -> total += v * 86400
                "h" -> total += v * 3600
                "m" -> total += v * 60
                "s" -> total += v
            }
        }
        return total
    }

    fun formatUptimeSeconds(seconds: Long): String {
        if (seconds <= 0) return "0s"
        val d = seconds / 86400
        val h = (seconds % 86400) / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return buildString {
            if (d > 0) append("${d}d")
            if (h > 0) append("${h}h")
            if (m > 0) append("${m}m")
            if (s > 0 || isEmpty()) append("${s}s")
        }
    }

    fun formatDurationMs(ms: Long): String {
        if (ms <= 0) return "0s"
        val totalSec = ms / 1000
        val d = totalSec / 86400
        val h = (totalSec % 86400) / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return buildString {
            if (d > 0) append("${d}d")
            if (h > 0) append("${h}h")
            if (m > 0) append("${m}m")
            if (s > 0 || isEmpty()) append("${s}s")
        }
    }

    fun parseBaseBytesFromComment(comment: String): Long? {
        val baseIdx = comment.indexOf("[BASE:")
        if (baseIdx >= 0) {
            val endIdx = comment.indexOf("]", baseIdx)
            val raw = if (endIdx > baseIdx) comment.substring(baseIdx + 6, endIdx).trim() else comment.substring(baseIdx + 6).trim()
            try {
                return raw.toLongOrNull()
            } catch (_: Exception) {}
        }
        return null
    }

    fun parseUsedBytesFromComment(comment: String): Long? {
        val usedIdx = comment.indexOf("[USED:")
        if (usedIdx >= 0) {
            val endIdx = comment.indexOf("]", usedIdx)
            val raw = if (endIdx > usedIdx) comment.substring(usedIdx + 6, endIdx).trim() else comment.substring(usedIdx + 6).trim()
            try {
                return raw.toLongOrNull()
            } catch (_: Exception) {}
        }
        return null
    }

    fun parseActivationTimestamp(comment: String): Long? {
        val actIdx = comment.indexOf("[ACT:")
        if (actIdx >= 0) {
            val endIdx = comment.indexOf("]", actIdx)
            val rawDate = if (endIdx > actIdx) comment.substring(actIdx + 5, endIdx).trim() else comment.substring(actIdx + 5).trim()
            try {
                val t = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("Asia/Yangon")
                }.parse(rawDate)?.time
                if (t != null) return t
            } catch (_: Exception) {}
            try {
                val t = SimpleDateFormat("MMM/dd/yyyy HH:mm:ss", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("Asia/Yangon")
                }.parse(rawDate)?.time
                if (t != null) return t
            } catch (_: Exception) {}
            try {
                val t = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("Asia/Yangon")
                }.parse(rawDate)?.time
                if (t != null) return t
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Automatically prunes expired vouchers whose validity expired 30 or more days ago.
     * Retains all active, unexpired, and unused stock vouchers.
     * Guaranteed safe: Never touches any voucher currently active in hotspot sessions.
     */
    suspend fun autoPruneExpiredVouchers(): Int = withContext(Dispatchers.IO) {
        try {
            val allVouchers = dao.getAllVouchersSync()
            if (allVouchers.isEmpty()) return@withContext 0

            val now = System.currentTimeMillis()
            val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000L // 30 days retention after expiration

            // Safety guard: query all currently connected active hotspot users to NEVER touch anyone online
            val activeUserNames = try {
                mikrotikClient.getActiveUsers().map { it.user }.toSet()
            } catch (_: Exception) {
                emptySet<String>()
            }

            val toPrune = allVouchers.filter { v ->
                // Must be marked as used, and NOT currently online
                if (!v.isUsed || activeUserNames.contains(v.code)) return@filter false

                // Determine activation time
                val actTime = v.activatedAt
                    ?: parseActivationTimestamp(v.comment)
                    ?: if (v.generatedAt > 0L) v.generatedAt else null
                    ?: return@filter false

                // Calculate validity duration in ms
                val validityMs = when {
                    v.validityDays > 0 -> v.validityDays * 86400000L
                    v.durationMinutes > 0 -> v.durationMinutes * 60000L
                    else -> 86400000L // default 1 day
                }

                val expirationTime = actTime + validityMs
                // Check if current time is at least 30 days past expiration
                now >= (expirationTime + thirtyDaysMs)
            }

            if (toPrune.isEmpty()) return@withContext 0

            val codes = toPrune.map { it.code }

            // 1. Delete from Room DB (keeps app storage clean)
            dao.deleteVouchersByCodes(codes)

            // 2. Synchronize with router if router still has them in /ip/hotspot/user
            if (mikrotikClient.isConnected()) {
                try {
                    mikrotikClient.deleteHotspotUsersBatch(codes)
                } catch (_: Exception) {}
            }

            codes.size
        } catch (_: Exception) {
            0
        }
    }

    suspend fun addVouchers(vouchers: List<Voucher>, pushToRouter: Boolean = true): Result<Unit> {
        if (vouchers.isEmpty()) return Result.success(Unit)
        if (pushToRouter) {
            if (!mikrotikClient.isConnected()) {
                mikrotikClient.ensureConnected()
            }
            if (!mikrotikClient.isConnected()) {
                return Result.failure(Exception("Router is not connected. Cannot add vouchers to router."))
            }
            val ok = mikrotikClient.addHotspotUsersBatch(vouchers)
            if (!ok) {
                return Result.failure(Exception("Router rejected adding vouchers."))
            }
        }
        val existingMap = dao.getAllVouchersSync().associateBy { it.code }
        val toInsert = vouchers.distinctBy { it.code }.map { v ->
            val ex = existingMap[v.code]
            if (ex != null) v.copy(id = ex.id) else v
        }
        dao.insertVouchers(toInsert)
        return Result.success(Unit)
    }

    suspend fun deleteVoucher(voucher: Voucher): Result<Unit> {
        if (!mikrotikClient.isConnected()) {
            mikrotikClient.ensureConnected()
        }
        if (mikrotikClient.isConnected()) {
            val ok = mikrotikClient.deleteHotspotUser(voucher.code)
            if (!ok) {
                return Result.failure(Exception("Router rejected deleting voucher '${voucher.code}'."))
            }
        } else {
            return Result.failure(Exception("Router is not connected. Cannot delete voucher from router."))
        }
        dao.deleteVoucher(voucher.id)
        dao.deleteVoucherByCode(voucher.code)
        val cleanCode = voucher.code.replace("-", "").trim()
        if (cleanCode != voucher.code) {
            dao.deleteVoucherByCode(cleanCode)
        }
        return Result.success(Unit)
    }

    suspend fun deleteVouchersByCodes(codes: Collection<String>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (codes.isEmpty()) return@withContext Result.success(Unit)
            val allCodes = mutableSetOf<String>()
            codes.forEach {
                allCodes.add(it)
                val clean = it.replace("-", "").trim()
                if (clean.isNotBlank()) allCodes.add(clean)
            }
            // 1. Optimistic Room DB deletion: instant UI update in < 5ms!
            dao.deleteVouchersByCodes(allCodes.toList())

            // 2. Synchronize deletion with router
            if (!mikrotikClient.isConnected()) {
                mikrotikClient.ensureConnected()
            }
            if (mikrotikClient.isConnected()) {
                val ok = mikrotikClient.deleteHotspotUsersBatch(codes)
                if (!ok) {
                    return@withContext Result.failure(Exception("Router rejected batch deleting vouchers."))
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteVoucherById(id: Int) = dao.deleteVoucher(id)

    suspend fun banMac(macAddress: String): Result<Unit> {
        if (!mikrotikClient.isConnected()) {
            mikrotikClient.ensureConnected()
        }
        if (mikrotikClient.isConnected()) {
            val ok = mikrotikClient.banMacAddress(macAddress)
            if (!ok) {
                return Result.failure(Exception("Router rejected banning MAC $macAddress."))
            }
        } else {
            return Result.failure(Exception("Router is not connected. Cannot ban MAC on router."))
        }
        dao.banSessionMac(macAddress, true)
        return Result.success(Unit)
    }

    suspend fun unbanMac(macAddress: String): Result<Unit> {
        if (!mikrotikClient.isConnected()) {
            mikrotikClient.ensureConnected()
        }
        if (mikrotikClient.isConnected()) {
            val ok = mikrotikClient.unbanMacAddress(macAddress)
            if (!ok) {
                return Result.failure(Exception("Router rejected unbanning MAC $macAddress."))
            }
        }
        dao.banSessionMac(macAddress, false)
        return Result.success(Unit)
    }

    suspend fun releaseVoucherFromDevice(username: String, macAddress: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                if (!mikrotikClient.isConnected()) {
                    mikrotikClient.ensureConnected()
                }
                val ok = mikrotikClient.releaseVoucherFromDevice(username, macAddress)
                if (ok) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to release voucher from device on router"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun executeRscScript(scriptContent: String): com.example.data.remote.ScriptExecutionResult {
        return withContext(Dispatchers.IO) {
            if (!mikrotikClient.isConnected()) {
                mikrotikClient.ensureConnected()
            }
            mikrotikClient.executeRscScript(scriptContent)
        }
    }

    suspend fun executeSingleCommand(commandLine: String): Result<String> {
        return withContext(Dispatchers.IO) {
            if (!mikrotikClient.isConnected()) {
                mikrotikClient.ensureConnected()
            }
            mikrotikClient.executeSingleCommand(commandLine)
        }
    }


    suspend fun fetchAndRunRemoteScript(url: String): com.example.data.remote.ScriptExecutionResult {
        return withContext(Dispatchers.IO) {
            if (!mikrotikClient.isConnected()) {
                mikrotikClient.ensureConnected()
            }
            mikrotikClient.fetchAndRunRemoteScript(url)
        }
    }

    suspend fun kickSession(sessionId: String): Result<Unit> {
        if (!mikrotikClient.isConnected()) {
            mikrotikClient.ensureConnected()
        }
        if (!mikrotikClient.isConnected()) {
            return Result.failure(Exception("Router is not connected."))
        }
        val ok = mikrotikClient.removeActiveSession(sessionId)
        if (!ok) {
            return Result.failure(Exception("Router could not disconnect session."))
        }
        return Result.success(Unit)
    }

    private val recordMutex = kotlinx.coroutines.sync.Mutex()
    private val sessionBaseMap = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Long>>()
    private val sessionDayStartMap = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Long>>()
    private val lastLiveBytesMap = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Long>>()

    suspend fun recordSessions(users: List<ActiveUser>) = withContext(Dispatchers.IO) {
        if (users.isEmpty()) return@withContext
        if (!recordMutex.tryLock()) return@withContext // Prevent concurrent duplicate runs
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("Asia/Yangon")
            }
            val dateKey = dateFormat.format(Date())
            val logs = users.map { u ->
                val macUpper = u.macAddress.trim().uppercase()
                val sessionKey = "${macUpper}_${dateKey}"
                val liveBIn = u.bytesIn.toLongOrNull() ?: 0L
                val liveBOut = u.bytesOut.toLongOrNull() ?: 0L
                val liveTotal = liveBIn + liveBOut

                // 12:00 AM Midnight Baseline Snapshot:
                // Only if a session truly started yesterday (uptime > seconds elapsed since midnight)
                // do we baseline the initial bytes so yesterday's traffic does not bleed into today.
                // For sessions started today, dayStart is (0,0) so 100% of today's traffic is captured!
                var dayStart = sessionDayStartMap[sessionKey]
                if (dayStart == null) {
                    val uptimeSec = parseUptimeSeconds(u.uptime)
                    val calNow = Calendar.getInstance(TimeZone.getTimeZone("Asia/Yangon"))
                    val secSinceMidnight = calNow.get(Calendar.HOUR_OF_DAY) * 3600L + calNow.get(Calendar.MINUTE) * 60L + calNow.get(Calendar.SECOND)
                    if (uptimeSec > secSinceMidnight && secSinceMidnight > 0L) {
                        val existing = dao.getSessionByMacAndDate(u.macAddress, dateKey)
                        dayStart = if (existing == null) Pair(liveBIn, liveBOut) else Pair(0L, 0L)
                    } else {
                        dayStart = Pair(0L, 0L)
                    }
                    sessionDayStartMap[sessionKey] = dayStart
                }

                var base = sessionBaseMap[sessionKey] ?: Pair(0L, 0L)
                val lastLive = lastLiveBytesMap[sessionKey]

                // If active session reconnected/restarted today (live bytes reset):
                if (lastLive != null && liveTotal < (lastLive.first + lastLive.second)) {
                    val prevDeltaIn = maxOf(0L, lastLive.first - dayStart.first)
                    val prevDeltaOut = maxOf(0L, lastLive.second - dayStart.second)
                    base = Pair(base.first + prevDeltaIn, base.second + prevDeltaOut)
                    sessionBaseMap[sessionKey] = base
                    dayStart = Pair(0L, 0L)
                    sessionDayStartMap[sessionKey] = dayStart
                }

                lastLiveBytesMap[sessionKey] = Pair(liveBIn, liveBOut)

                val currentDeltaIn = maxOf(0L, liveBIn - dayStart.first)
                val currentDeltaOut = maxOf(0L, liveBOut - dayStart.second)

                val finalBIn = base.first + currentDeltaIn
                val finalBOut = base.second + currentDeltaOut
                val finalMb = (finalBIn + finalBOut) / (1024.0 * 1024.0)

                RouterSessionLog(
                    macAddress = u.macAddress,
                    ipAddress = u.address,
                    voucherCode = u.user,
                    uptime = u.uptime,
                    bytesIn = finalBIn,
                    bytesOut = finalBOut,
                    dataUsedMb = Math.round(finalMb * 100.0) / 100.0,
                    sessionStartTime = System.currentTimeMillis(),
                    dateKey = dateKey
                )
            }
            dao.insertSessions(logs)
            val activeCodes = users.map { it.user }.filter { it.isNotBlank() && it != "admin" }
            if (activeCodes.isNotEmpty()) {
                dao.markVouchersUsed(activeCodes)
            }
        } finally {
            recordMutex.unlock()
        }
    }

    suspend fun markVouchersAsPrinted(codes: List<String>) = withContext(Dispatchers.IO) {
        try {
            if (codes.isNotEmpty()) {
                codes.chunked(250).forEach { chunk ->
                    dao.markVouchersPrinted(chunk)
                }
                if (!mikrotikClient.isConnected()) {
                    mikrotikClient.ensureConnected()
                }
                if (mikrotikClient.isConnected()) {
                    mikrotikClient.markUsersAsPrintedOnRouter(codes)
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun renewVoucher(voucher: Voucher): Result<Unit> {
        if (!mikrotikClient.isConnected()) {
            mikrotikClient.ensureConnected()
        }
        if (mikrotikClient.isConnected()) {
            val ok = mikrotikClient.renewHotspotUser(voucher.code)
            if (!ok) {
                return Result.failure(Exception("Router rejected renewing voucher '${voucher.code}'."))
            }
        } else {
            return Result.failure(Exception("Router is not connected. Cannot renew voucher on router."))
        }
        dao.renewVoucher(voucher.code)
        return Result.success(Unit)
    }

    suspend fun deleteUsedVouchers(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val used = dao.getAllUsedVouchersSync()
            if (used.isEmpty()) return@withContext Result.success(Unit)
            // 1. Optimistic Room DB deletion: instant UI update in < 5ms!
            dao.deleteAllUsedVouchers()

            // 2. Synchronize deletion with router
            if (!mikrotikClient.isConnected()) {
                mikrotikClient.ensureConnected()
            }
            if (mikrotikClient.isConnected()) {
                val ok = mikrotikClient.deleteHotspotUsersBatch(used.map { it.code })
                if (!ok) {
                    return@withContext Result.failure(Exception("Router rejected deleting used vouchers."))
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearCorruptSessions() {
        dao.clearAllSessions()
    }

    suspend fun resetAllStatistics(): Result<Unit> = withContext(Dispatchers.IO) {
        if (!mikrotikClient.isConnected()) {
            mikrotikClient.ensureConnected()
        }
        val routerOk = if (mikrotikClient.isConnected()) {
            mikrotikClient.resetAllStatisticsOnRouter()
        } else false

        dao.clearAllSessions()
        dao.resetAllVouchersUsage()
        if (mikrotikClient.isConnected()) {
            syncVouchersFromRouter()
        }

        if (routerOk) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Could not reset statistics on router. Please verify router connection."))
        }
    }

    suspend fun getRouterStats() = mikrotikClient.getRouterStats()
    suspend fun getInterfaceMetrics() = mikrotikClient.getInterfaceMetrics()
    suspend fun getInterfaceTraffic() = mikrotikClient.getInterfaceTraffic()
    suspend fun getRouterTotalDataBytes() = mikrotikClient.getRouterTotalDataBytes()

    suspend fun getActiveHotspotUsers(): List<ActiveUser> {
        val rawUsers = mikrotikClient.getActiveUsers()
        if (rawUsers.isEmpty()) return emptyList()

        val profiles = dao.getAllProfilesSync()
        val profileMap = profiles.associateBy { it.name.lowercase() }
        val activeCodes = rawUsers.map { it.user }
        val vouchers = if (activeCodes.isNotEmpty()) {
            dao.getVouchersByCodes(activeCodes)
        } else emptyList()
        val voucherMap = vouchers.associateBy { it.code.lowercase() }
        val routerUsersMap = mikrotikClient.getCachedHotspotUsers().associateBy { it.name.lowercase() }

        return rawUsers.map { u ->
            val userLower = u.user.lowercase()
            val voucher = voucherMap[userLower]
            val routerUser = routerUsersMap[userLower]

            val effectiveComment = when {
                routerUser != null && routerUser.comment.isNotBlank() -> routerUser.comment
                u.comment.isNotBlank() -> u.comment
                voucher != null && voucher.comment.isNotBlank() -> voucher.comment
                else -> ""
            }

            // 1. Resolve Profile Name
            val profileName = when {
                voucher != null && voucher.profileName.isNotBlank() -> voucher.profileName
                routerUser != null && routerUser.profile.isNotBlank() -> routerUser.profile
                u.profileName.isNotBlank() -> u.profileName
                effectiveComment.isNotBlank() -> {
                    profiles.firstOrNull { prof -> effectiveComment.contains(prof.name, ignoreCase = true) }?.name
                        ?: profiles.firstOrNull { prof ->
                            val cleanProf = prof.name.replace("_", " ").lowercase()
                            effectiveComment.lowercase().contains(cleanProf)
                        }?.name ?: ""
                }
                else -> ""
            }

            val matchedProfile = profileMap[profileName.lowercase()]

            // 2. Resolve Quota Total in MB based on profile
            val parsedOrigLimit = parseOrigLimitFromComment(effectiveComment)
                ?: parseOrigLimitFromComment(u.comment)
                ?: routerUser?.let { parseOrigLimitFromComment(it.comment) }
                ?: voucher?.let { parseOrigLimitFromComment(it.comment) }
            val defaults = getDefaultProfilePriceAndQuota(profileName.ifBlank { effectiveComment })

            val quotaTotal = when {
                parsedOrigLimit != null && parsedOrigLimit > 0L -> (parsedOrigLimit / (1024 * 1024)).toInt()
                voucher != null && voucher.dataLimitMb > 0 -> voucher.dataLimitMb
                matchedProfile != null && matchedProfile.dataLimitMb > 0 -> matchedProfile.dataLimitMb
                defaults.second > 0 -> defaults.second
                else -> {
                    val combined = "$profileName $effectiveComment ${u.user}".uppercase()
                    when {
                        combined.contains("100GB") -> 102400
                        combined.contains("50GB") -> 51200
                        combined.contains("30GB") || combined.contains("30D") -> 30720
                        combined.contains("20GB") -> 20480
                        combined.contains("15GB") -> 15360
                        combined.contains("10GB") -> 10240
                        combined.contains("7GB") -> 7168
                        combined.contains("5GB") -> 5120
                        combined.contains("3GB") -> 3072
                        combined.contains("2GB") -> 2048
                        combined.contains("1GB") -> 1024
                        combined.contains("500MB") -> 500
                        matchedProfile != null -> matchedProfile.dataLimitMb
                        else -> 0 // 0 = unlimited quota
                    }
                }
            }

            val sessionBytes = (u.bytesIn.toLongOrNull() ?: 0L) + (u.bytesOut.toLongOrNull() ?: 0L)
            val baseBytes = parseBaseBytesFromComment(effectiveComment)
                ?: parseBaseBytesFromComment(u.comment)
                ?: 0L
            val usedBytes = parseUsedBytesFromComment(effectiveComment)
                ?: parseUsedBytesFromComment(u.comment)
                ?: 0L
            val quotaTotalBytes: Long = if (parsedOrigLimit != null && parsedOrigLimit > 0L) parsedOrigLimit else (quotaTotal.toLong() * 1024L * 1024L)

            // In active sessions: live session bytes are in sessionBytes.
            // baseBytes contains previous completed sessions, and usedBytes is updated continuously by hs-quota-save.
            // Do NOT add stored Room DB bytes (voucher.bytesIn + bytesOut) on top of sessionBytes to prevent double-counting.
            val exactTotalUsedBytes = maxOf(
                usedBytes,
                baseBytes + sessionBytes,
                sessionBytes
            )

            val exactRemainingBytes = if (quotaTotalBytes > 0L) {
                maxOf(0L, quotaTotalBytes - exactTotalUsedBytes)
            } else 0L

            val finalUsedMb = Math.round((exactTotalUsedBytes / (1024.0 * 1024.0)) * 10.0) / 10.0
            val finalRemainingMb = if (quotaTotal > 0) Math.round((exactRemainingBytes / (1024.0 * 1024.0)) * 10.0) / 10.0 else 0.0

            // 3. Resolve continuous cumulative Uptime across reboots
            val baseUpSec = parseBaseUptimeSeconds(effectiveComment) ?: 0L
            val usedUpSec = parseUsedUptimeSeconds(effectiveComment) ?: 0L
            val sessionUpSec = parseUptimeSeconds(u.uptime)
            val effectiveUpSec = maxOf(usedUpSec, baseUpSec + sessionUpSec, sessionUpSec, parseUptimeSeconds(voucher?.uptime ?: ""))
            val finalUptime = if (effectiveUpSec > 0) formatUptimeSeconds(effectiveUpSec) else u.uptime

            // 4. Resolve continuous Time Left countdown (deducting all consumed elapsed time)
            val actTime = parseActivationTimestamp(effectiveComment)
                ?: voucher?.let { parseActivationTimestamp(it.comment) }
            val durationMinutes = when {
                matchedProfile != null && matchedProfile.durationMinutes > 0 -> matchedProfile.durationMinutes
                voucher != null && voucher.durationMinutes > 0 -> voucher.durationMinutes
                defaults.third > 0 -> defaults.third * 1440
                else -> 0
            }

            val calculatedTimeLeft: String = when {
                actTime != null && durationMinutes > 0 -> {
                    val expiryTime = actTime + (durationMinutes * 60 * 1000L)
                    val remMs = expiryTime - System.currentTimeMillis()
                    if (remMs > 0) formatDurationMs(remMs) else "0s (Expired)"
                }
                routerUser != null && routerUser.limitUptime.isNotBlank() && routerUser.limitUptime != "0s" && routerUser.limitUptime != "none" -> {
                    val limitSec = parseUptimeSeconds(routerUser.limitUptime)
                    val remSec = limitSec - effectiveUpSec
                    if (remSec > 0) formatUptimeSeconds(remSec) else "0s (Expired)"
                }
                u.limitUptime.isNotBlank() && u.limitUptime != "0s" && u.limitUptime != "none" -> {
                    val limitSec = parseUptimeSeconds(u.limitUptime)
                    val remSec = limitSec - effectiveUpSec
                    if (remSec > 0) formatUptimeSeconds(remSec) else "0s (Expired)"
                }
                else -> u.sessionTimeLeft
            }

            u.copy(
                profileName = if (profileName.isNotBlank()) profileName else (matchedProfile?.name ?: ""),
                uptime = finalUptime,
                sessionTimeLeft = if (calculatedTimeLeft.isNotBlank()) calculatedTimeLeft else u.sessionTimeLeft,
                quotaUsedMb = finalUsedMb,
                quotaTotalMb = quotaTotal,
                quotaRemainingMb = finalRemainingMb,
                comment = effectiveComment
            )
        }
    }

    suspend fun uploadPortalZip(zipBytes: ByteArray, onProgress: (String, Int, Int) -> Unit) =
        mikrotikClient.uploadPortalZip(zipBytes, onProgress)

    suspend fun getIpBindings(): List<com.example.domain.models.IpBinding> = mikrotikClient.getIpBindings()
    suspend fun whitelistDevice(mac: String, ip: String = "", comment: String = "Whitelisted Device"): Boolean = mikrotikClient.whitelistDevice(mac, ip, comment)
    suspend fun removeIpBinding(id: String, mac: String = ""): Boolean = mikrotikClient.removeIpBinding(id, mac)
    suspend fun getNetworkTopology(): com.example.domain.models.NetworkTopologyData = mikrotikClient.getNetworkTopology()
    suspend fun renameAccessPoint(mac: String, newName: String): Boolean = mikrotikClient.renameAccessPoint(mac, newName)
    suspend fun setRouterAdvanceMode(): Boolean = mikrotikClient.setRouterAdvanceMode()
}

