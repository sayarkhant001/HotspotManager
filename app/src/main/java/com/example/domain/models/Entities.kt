package com.example.domain.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "user_profiles")
@Serializable
data class UserProfile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val downloadLimitMbps: Int,
    val uploadLimitMbps: Int,
    val dataLimitMb: Int,
    val durationMinutes: Int,
    val price: Double,
    val validityDays: Int
)

@Entity(tableName = "vouchers")
@Serializable
data class Voucher(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val code: String,
    val profileId: Int,
    val profileName: String,
    val downloadLimitMbps: Int,
    val uploadLimitMbps: Int,
    val dataLimitMb: Int,
    val durationMinutes: Int,
    val generatedAt: Long = System.currentTimeMillis(),
    val isUsed: Boolean = false,
    val styleType: Int = 1
)

@Entity(tableName = "sessions")
@Serializable
data class RouterSessionLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val macAddress: String,
    val ipAddress: String,
    val voucherCode: String?,
    val dataUsedMb: Double,
    val sessionStartTime: Long,
    val sessionEndTime: Long?,
    val isBanned: Boolean = false
)
