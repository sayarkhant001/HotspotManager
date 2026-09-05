package com.example.data.repository

import com.example.data.local.RouterDao
import com.example.data.remote.MikrotikClient
import com.example.domain.models.RouterSessionLog
import com.example.domain.models.UserProfile
import com.example.domain.models.Voucher
import kotlinx.coroutines.flow.Flow

class AppRepository(
    private val dao: RouterDao,
    val mikrotikClient: MikrotikClient
) {
    val profiles: Flow<List<UserProfile>> = dao.getAllProfiles()
    val vouchers: Flow<List<Voucher>> = dao.getAllVouchers()
    val sessions: Flow<List<RouterSessionLog>> = dao.getAllSessions()

    suspend fun addProfile(profile: UserProfile) = dao.insertProfile(profile)
    suspend fun deleteProfile(id: Int) = dao.deleteProfile(id)

    suspend fun addVouchers(vouchers: List<Voucher>) = dao.insertVouchers(vouchers)
    suspend fun deleteVoucher(id: Int) = dao.deleteVoucher(id)

    suspend fun banMac(macAddress: String) {
        dao.banSessionMac(macAddress, true)
        // Also call mikrotik API to ban
        mikrotikClient.banMacAddress(macAddress)
    }

    suspend fun getRouterStats() = mikrotikClient.getRouterStats()
    suspend fun getActiveHotspotUsers() = mikrotikClient.getActiveUsers()
}
