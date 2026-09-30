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

class AppRepository(
    private val dao: RouterDao,
    val mikrotikClient: MikrotikClient
) {
    val profiles: Flow<List<UserProfile>> = dao.getAllProfiles()
    val vouchers: Flow<List<Voucher>> = dao.getAllVouchers()
    val sessions: Flow<List<RouterSessionLog>> = dao.getAllSessions()

    fun getSessionsByDate(dateKey: String): Flow<List<RouterSessionLog>> = dao.getSessionsByDate(dateKey)
    fun getSessionsBetween(start: Long, end: Long): Flow<List<RouterSessionLog>> = dao.getSessionsBetween(start, end)
    fun getVouchersByProfile(profileName: String): Flow<List<Voucher>> = dao.getVouchersByProfile(profileName)

    suspend fun addProfile(profile: UserProfile): Result<Unit> {
        if (!mikrotikClient.isConnected()) {
            mikrotikClient.ensureConnected()
        }
        if (!mikrotikClient.isConnected()) {
            return Result.failure(Exception("Router is not connected. Cannot add profile."))
        }
        val ok = mikrotikClient.addRouterProfile(profile.name, profile.rateLimit, profile.sharedUsers)
        if (!ok) {
            return Result.failure(Exception("Router rejected creating profile '${profile.name}'."))
        }
        dao.insertProfile(profile)
        return Result.success(Unit)
    }

    suspend fun syncProfilesFromRouter() {
        if (mikrotikClient.isConnected()) {
            val routerProfiles = mikrotikClient.getRouterProfiles()
            val existingProfiles = dao.getAllProfilesSync().associateBy { it.name }
            val list = routerProfiles.map { rp ->
                val existing = existingProfiles[rp.name]
                val defaults = getDefaultProfilePriceAndQuota(rp.name)
                val price = if (existing != null && existing.price > 0) existing.price else defaults.first
                val quota = if (existing != null && existing.dataLimitMb > 0) existing.dataLimitMb else defaults.second
                val validity = if (existing != null && existing.validityDays > 0) existing.validityDays else defaults.third
                UserProfile(
                    name = rp.name,
                    rateLimit = rp.rateLimit,
                    sharedUsers = rp.sharedUsers.toIntOrNull() ?: 1,
                    downloadLimitMbps = parseRateLimitMbps(rp.rateLimit),
                    uploadLimitMbps = parseRateLimitMbps(rp.rateLimit),
                    dataLimitMb = quota,
                    durationMinutes = existing?.durationMinutes ?: 0,
                    price = price,
                    sellingPrice = price,
                    validityDays = validity
                )
            }
            dao.clearProfiles()
            dao.insertProfiles(list)
        }
    }

    private fun getDefaultProfilePriceAndQuota(name: String): Triple<Double, Int, Int> {
        val upper = name.uppercase()
        return when {
            upper.contains("30GB") || upper.contains("30D") -> Triple(10000.0, 30720, 30)
            upper.contains("7GB") -> Triple(3000.0, 7168, 7)
            upper.contains("3GB") -> Triple(1000.0, 3072, 3)
            upper.contains("2GB") -> Triple(800.0, 2048, 2)
            upper.contains("1GB") -> Triple(500.0, 1024, 1)
            upper.contains("50GB") -> Triple(15000.0, 51200, 30)
            upper.contains("UNLIM") && upper.contains("30") -> Triple(20000.0, 0, 30)
            upper.contains("UNLIM") -> Triple(5000.0, 0, 7)
            else -> Triple(0.0, 0, 30)
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
        if (!mikrotikClient.isConnected()) {
            mikrotikClient.ensureConnected()
        }
        if (!mikrotikClient.isConnected()) {
            return Result.failure(Exception("Router is not connected. Cannot delete profile."))
        }
        val ok = mikrotikClient.deleteRouterProfile(profileName)
        if (!ok) {
            return Result.failure(Exception("Router rejected deleting profile '$profileName'."))
        }
        dao.deleteProfileByName(profileName)
        dao.deleteVouchersByProfile(profileName)
        return Result.success(Unit)
    }

    suspend fun updateProfile(oldName: String, profile: UserProfile): Result<Unit> {
        if (!mikrotikClient.isConnected()) {
            mikrotikClient.ensureConnected()
        }
        if (!mikrotikClient.isConnected()) {
            return Result.failure(Exception("Router is not connected. Cannot update profile."))
        }
        val ok = mikrotikClient.updateRouterProfile(
            oldName = oldName,
            newName = profile.name,
            rateLimit = profile.rateLimit,
            sharedUsers = profile.sharedUsers
        )
        if (!ok) {
            return Result.failure(Exception("Router rejected updating profile '$oldName'."))
        }
        dao.updateProfile(profile)
        if (oldName != profile.name) {
            dao.updateVoucherProfileName(oldName, profile.name)
        }
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
        if (!mikrotikClient.isConnected()) return 0
        val routerUsers = mikrotikClient.getAllHotspotUsers()
        if (routerUsers.isEmpty()) return 0

        val profilesList = dao.getAllProfilesSync()
        val profileMap = profilesList.associateBy { it.name }

        val vouchers = routerUsers.map { u ->
            val prof = profileMap[u.profile]
            val isAcc = u.password.isNotBlank() && u.password != u.name
            val totalBytes = u.limitBytesTotal
            val mb = if (totalBytes > 0) (totalBytes / (1024 * 1024)).toInt() else (prof?.dataLimitMb ?: 0)
            val isUsed = (u.uptime != "0s" && u.uptime.isNotBlank()) || u.bytesOut > 0
            val isPrinted = u.comment.contains("PRINTED", ignoreCase = true)

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
                durationMinutes = prof?.durationMinutes ?: 0,
                validityDays = prof?.validityDays ?: 1,
                price = if (prof != null && prof.sellingPrice > 0) prof.sellingPrice else (prof?.price ?: 0.0),
                generatedAt = System.currentTimeMillis(),
                isUsed = isUsed,
                isPrinted = isPrinted,
                comment = u.comment
            )
        }
        dao.clearAllVouchers()
        dao.insertVouchers(vouchers)
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
}

