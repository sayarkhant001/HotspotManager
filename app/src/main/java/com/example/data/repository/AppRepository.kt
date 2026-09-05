package com.example.data.repository

import com.example.data.local.RouterDao
import com.example.data.remote.ActiveUser
import com.example.data.remote.MikrotikClient
import com.example.domain.models.RouterSessionLog
import com.example.domain.models.UserProfile
import com.example.domain.models.Voucher
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    suspend fun addProfile(profile: UserProfile) {
        dao.insertProfile(profile)
        // Also push to MikroTik if connected
        if (mikrotikClient.isConnected()) {
            mikrotikClient.addRouterProfile(profile.name, profile.rateLimit, profile.sharedUsers)
        }
    }

    suspend fun syncProfilesFromRouter() {
        if (mikrotikClient.isConnected()) {
            val routerProfiles = mikrotikClient.getRouterProfiles()
            val list = routerProfiles.map { rp ->
                UserProfile(
                    name = rp.name,
                    rateLimit = rp.rateLimit,
                    sharedUsers = rp.sharedUsers.toIntOrNull() ?: 1,
                    downloadLimitMbps = parseRateLimitMbps(rp.rateLimit),
                    uploadLimitMbps = parseRateLimitMbps(rp.rateLimit),
                    dataLimitMb = 0,
                    durationMinutes = 0,
                    price = 0.0,
                    validityDays = 30
                )
            }
            dao.insertProfiles(list)
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

    suspend fun deleteProfile(id: Int) = dao.deleteProfile(id)

    suspend fun addVouchers(vouchers: List<Voucher>, pushToRouter: Boolean = true) {
        dao.insertVouchers(vouchers)
        if (pushToRouter && mikrotikClient.isConnected()) {
            vouchers.forEach { v ->
                val pass = if (v.isAccount) v.password else v.code
                val limitBytes = if (v.dataLimitMb > 0) v.dataLimitMb.toLong() * 1024L * 1024L else 0L
                val comment = if (v.comment.isNotBlank()) v.comment else "EXP:Voucher"
                mikrotikClient.addHotspotUser(
                    name = v.code,
                    password = pass,
                    profile = v.profileName,
                    comment = comment,
                    limitBytesTotal = limitBytes
                )
            }
        }
    }

    suspend fun deleteVoucher(id: Int) = dao.deleteVoucher(id)

    suspend fun banMac(macAddress: String) {
        dao.banSessionMac(macAddress, true)
        if (mikrotikClient.isConnected()) {
            mikrotikClient.banMacAddress(macAddress)
        }
    }

    suspend fun kickSession(sessionId: String): Boolean {
        return if (mikrotikClient.isConnected()) {
            mikrotikClient.removeActiveSession(sessionId)
        } else false
    }

    suspend fun recordSessions(users: List<ActiveUser>) {
        if (users.isEmpty()) return
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
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
                dataUsedMb = mb,
                sessionStartTime = System.currentTimeMillis(),
                dateKey = dateKey
            )
        }
        dao.insertSessions(logs)
    }

    suspend fun getRouterStats() = mikrotikClient.getRouterStats()
    suspend fun getInterfaceTraffic(iface: String = "hotspot-bridge") = mikrotikClient.getInterfaceTraffic(iface)
    suspend fun getActiveHotspotUsers() = mikrotikClient.getActiveUsers()
}

