package com.example.domain.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "user_profiles",
    indices = [Index(value = ["name"], unique = true, name = "index_user_profiles_name")]
)
@Serializable
data class UserProfile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val sharedUsers: Int = 1,
    val rateLimit: String = "5M/5M",
    val downloadLimitMbps: Int = 5,
    val uploadLimitMbps: Int = 5,
    val dataLimitMb: Int = 0, // 0 = unlimited
    val durationMinutes: Int = 0, // 0 = unlimited
    val price: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val validityDays: Int = 1,
    val lockUser: Boolean = false
)

@Entity(
    tableName = "vouchers",
    indices = [Index(value = ["code"], unique = true, name = "index_vouchers_code")]
)
@Serializable
data class Voucher(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val code: String,
    val username: String = code,
    val password: String = "",
    val isAccount: Boolean = false,
    val profileId: Int = 0,
    val profileName: String,
    val downloadLimitMbps: Int = 0,
    val uploadLimitMbps: Int = 0,
    val dataLimitMb: Int = 0,
    val durationMinutes: Int = 0,
    val validityDays: Int = 1,
    val price: Double = 0.0,
    val generatedAt: Long = System.currentTimeMillis(),
    val isUsed: Boolean = false,
    val isPrinted: Boolean = false,
    val styleType: Int = 1,
    val comment: String = "",
    val bytesIn: Long = 0L,
    val bytesOut: Long = 0L,
    val uptime: String = ""
)

@Entity(
    tableName = "sessions",
    indices = [Index(value = ["macAddress", "dateKey"], unique = true)]
)
@Serializable
data class RouterSessionLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val macAddress: String,
    val ipAddress: String,
    val voucherCode: String? = null,
    val uptime: String = "",
    val bytesIn: Long = 0L,
    val bytesOut: Long = 0L,
    val dataUsedMb: Double = 0.0,
    val sessionStartTime: Long = System.currentTimeMillis(),
    val sessionEndTime: Long? = null,
    val isBanned: Boolean = false,
    val dateKey: String = "" // format: YYYY-MM-DD
)

data class ActiveUser(
    val id: String,
    val server: String,
    val user: String,
    val address: String,
    val macAddress: String,
    val uptime: String,
    val bytesIn: String,
    val bytesOut: String,
    val hostName: String = "",
    val quotaUsedMb: Double = 0.0,
    val quotaTotalMb: Int = 0,
    val quotaRemainingMb: Double = 0.0,
    val profileName: String = "",
    val comment: String = "",
    val sessionTimeLeft: String = "",
    val limitUptime: String = ""
)

data class IpBinding(
    val id: String,
    val macAddress: String,
    val address: String = "",
    val toAddress: String = "",
    val type: String = "bypassed", // "bypassed", "blocked", "regular"
    val comment: String = "",
    val disabled: Boolean = false
)

data class AccessPointDevice(
    val name: String,
    val model: String,
    val ipAddress: String,
    val macAddress: String,
    val isWhitelisted: Boolean,
    val isOnline: Boolean,
    val bindingId: String? = null,
    val vendor: String = "Ruijie / Reyee",
    val interfaceName: String = "",
    val connectedClientMacs: List<String> = emptyList(),
    val currentRxBps: Long = 0L,
    val currentTxBps: Long = 0L,
    val dailyBytesIn: Long = 0L,
    val dailyBytesOut: Long = 0L
)

data class NetworkTopologyData(
    val routerModel: String = "RB4011iGS+",
    val manageIp: String = "192.168.88.1",
    val hotspotIp: String = "192.168.100.1",
    val accessPoints: List<AccessPointDevice> = emptyList()
)
