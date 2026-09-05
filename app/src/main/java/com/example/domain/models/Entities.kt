package com.example.domain.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "user_profiles")
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

@Entity(tableName = "vouchers")
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
    val styleType: Int = 1,
    val comment: String = ""
)

@Entity(tableName = "sessions")
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

