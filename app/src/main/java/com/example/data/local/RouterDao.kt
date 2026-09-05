package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.domain.models.RouterSessionLog
import com.example.domain.models.UserProfile
import com.example.domain.models.Voucher
import kotlinx.coroutines.flow.Flow

@Dao
interface RouterDao {
    @Query("SELECT * FROM user_profiles ORDER BY name ASC")
    fun getAllProfiles(): Flow<List<UserProfile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserProfile)

    @Query("DELETE FROM user_profiles WHERE id = :id")
    suspend fun deleteProfile(id: Int)

    @Query("SELECT * FROM vouchers ORDER BY generatedAt DESC")
    fun getAllVouchers(): Flow<List<Voucher>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVouchers(vouchers: List<Voucher>)

    @Query("DELETE FROM vouchers WHERE id = :id")
    suspend fun deleteVoucher(id: Int)

    @Query("SELECT * FROM sessions ORDER BY sessionStartTime DESC")
    fun getAllSessions(): Flow<List<RouterSessionLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: RouterSessionLog)

    @Query("UPDATE sessions SET isBanned = :isBanned WHERE macAddress = :macAddress")
    suspend fun banSessionMac(macAddress: String, isBanned: Boolean)
}
