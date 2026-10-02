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
                meta != null -> meta.price
                existing != null && existing.price > 0.0 -> existing.price
                else -> defaults.first
            }
            val sellingPrice = when {
                meta != null -> meta.sellingPrice
                existing != null && existing.sellingPrice > 0.0 -> existing.sellingPrice
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

        val sessionLogs = mutableListOf<com.example.domain.models.RouterSessionLog>()

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
            val mb = when {
                totalBytes > 0 -> (totalBytes / (1024 * 1024)).toInt()
                prof != null && prof.dataLimitMb > 0 -> prof.dataLimitMb
                defaults.second > 0 -> defaults.second
                else -> 0
            }
            val isUsed = (u.uptime != "0s" && u.uptime.isNotBlank()) || u.bytesOut > 0
            val isPrinted = u.comment.contains("PRINTED", ignoreCase = true)

            val existing = existingVouchersMap[u.name]
            val parsedActTime = parseActivationTimestamp(u.comment)
            val uptimeSec = parseUptimeSeconds(u.uptime)
            val generatedAt = when {
                parsedActTime != null -> parsedActTime
                existing != null && existing.generatedAt > 0L -> existing.generatedAt
                isUsed && uptimeSec > 0 -> System.currentTimeMillis() - (uptimeSec * 1000L)
                else -> existing?.generatedAt ?: System.currentTimeMillis()
            }

            if (isUsed) {
                val bIn = u.bytesIn
                val bOut = u.bytesOut
                val mbUsed = (bIn + bOut) / (1024.0 * 1024.0)
                val sDateKey = dateFormat.format(Date(generatedAt))
                sessionLogs.add(
                    com.example.domain.models.RouterSessionLog(
                        macAddress = u.name,
                        ipAddress = "",
                        voucherCode = u.name,
                        uptime = u.uptime,
                        bytesIn = bIn,
                        bytesOut = bOut,
                        dataUsedMb = Math.round(mbUsed * 100.0) / 100.0,
                        sessionStartTime = generatedAt,
                        dateKey = sDateKey
                    )
                )
            }

            val price = when {
                existing != null && existing.price > 0.0 -> existing.price
                prof != null && prof.sellingPrice > 0 -> prof.sellingPrice
                prof != null && prof.price > 0 -> prof.price
                defaults.first > 0 -> defaults.first
                else -> 0.0
            }

            Voucher(
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
                isUsed = isUsed,
                isPrinted = isPrinted,
                comment = u.comment,
                bytesIn = u.bytesIn,
                bytesOut = u.bytesOut,
                uptime = u.uptime
            )
        }
        dao.clearAllVouchers()
        vouchers.chunked(250).forEach { chunk ->
            dao.insertVouchers(chunk)
        }
        if (sessionLogs.isNotEmpty()) {
            sessionLogs.chunked(250).forEach { chunk ->
                dao.insertSessions(chunk)
            }
        }
        return vouchers.size
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
        dao.insertVouchers(vouchers)
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

    suspend fun recordSessions(users: List<ActiveUser>) = withContext(Dispatchers.IO) {
        if (users.isEmpty()) return@withContext
        if (!recordMutex.tryLock()) return@withContext // Prevent concurrent duplicate runs
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("Asia/Yangon")
            }
            val dateKey = dateFormat.format(Date())
            val logs = users.map { u ->
                val bIn = u.bytesIn.toLongOrNull() ?: 0L
                val bOut = u.bytesOut.toLongOrNull() ?: 0L
                val mb = (bIn + bOut) / (1024.0 * 1024.0)
                RouterSessionLog(
                    macAddress = u.macAddress,
                    ipAddress = u.address,
                    voucherCode = u.user,
                    uptime = u.uptime,
                    bytesIn = bIn,
                    bytesOut = bOut,
                    dataUsedMb = Math.round(mb * 100.0) / 100.0,
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

            // 1. Resolve Profile Name
            val profileName = when {
                voucher != null && voucher.profileName.isNotBlank() -> voucher.profileName
                routerUser != null && routerUser.profile.isNotBlank() -> routerUser.profile
                u.comment.isNotBlank() -> {
                    profiles.firstOrNull { prof -> u.comment.contains(prof.name, ignoreCase = true) }?.name
                        ?: profiles.firstOrNull { prof ->
                            val cleanProf = prof.name.replace("_", " ").lowercase()
                            u.comment.lowercase().contains(cleanProf)
                        }?.name ?: ""
                }
                else -> ""
            }

            val matchedProfile = profileMap[profileName.lowercase()]

            // 2. Resolve Quota Total in MB based on profile
            val quotaTotal = when {
                voucher != null && voucher.dataLimitMb > 0 -> voucher.dataLimitMb
                matchedProfile != null && matchedProfile.dataLimitMb > 0 -> matchedProfile.dataLimitMb
                routerUser != null && routerUser.limitBytesTotal > 0 -> (routerUser.limitBytesTotal / (1024 * 1024)).toInt()
                else -> {
                    val combined = "$profileName ${u.comment} ${u.user}".uppercase()
                    when {
                        combined.contains("50GB") -> 51200
                        combined.contains("30GB") || combined.contains("30D") -> 30720
                        combined.contains("7GB") -> 7168
                        combined.contains("3GB") -> 3072
                        combined.contains("2GB") -> 2048
                        combined.contains("1GB") -> 1024
                        matchedProfile != null -> matchedProfile.dataLimitMb
                        else -> 0 // 0 = unlimited quota
                    }
                }
            }

            val remaining = if (quotaTotal > 0) maxOf(0.0, quotaTotal - u.quotaUsedMb) else 0.0
            u.copy(
                profileName = if (profileName.isNotBlank()) profileName else (matchedProfile?.name ?: ""),
                quotaTotalMb = quotaTotal,
                quotaRemainingMb = Math.round(remaining * 10.0) / 10.0
            )
        }
    }

    suspend fun getIpBindings(): List<com.example.domain.models.IpBinding> = mikrotikClient.getIpBindings()
    suspend fun whitelistDevice(mac: String, ip: String = "", comment: String = "Whitelisted Device"): Boolean = mikrotikClient.whitelistDevice(mac, ip, comment)
    suspend fun removeIpBinding(id: String, mac: String = ""): Boolean = mikrotikClient.removeIpBinding(id, mac)
    suspend fun getNetworkTopology(): com.example.domain.models.NetworkTopologyData = mikrotikClient.getNetworkTopology()
    suspend fun renameAccessPoint(mac: String, newName: String): Boolean = mikrotikClient.renameAccessPoint(mac, newName)
    suspend fun setRouterAdvanceMode(): Boolean = mikrotikClient.setRouterAdvanceMode()
}

