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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfiles(profiles: List<UserProfile>)

    @Query("DELETE FROM user_profiles WHERE id = :id")
    suspend fun deleteProfile(id: Int)

    @Query("DELETE FROM user_profiles WHERE name = :name")
    suspend fun deleteProfileByName(name: String)

    @Query("SELECT * FROM vouchers ORDER BY generatedAt DESC")
    fun getAllVouchers(): Flow<List<Voucher>>

    @Query("SELECT * FROM vouchers WHERE profileName = :profileName ORDER BY generatedAt DESC")
    fun getVouchersByProfile(profileName: String): Flow<List<Voucher>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVouchers(vouchers: List<Voucher>)

    @Query("DELETE FROM vouchers WHERE id = :id")
    suspend fun deleteVoucher(id: Int)

    @Query("UPDATE vouchers SET isUsed = :isUsed WHERE code = :code")
    suspend fun updateVoucherUsed(code: String, isUsed: Boolean)

    @Query("SELECT * FROM sessions ORDER BY sessionStartTime DESC")
    fun getAllSessions(): Flow<List<RouterSessionLog>>

    @Query("SELECT * FROM sessions WHERE dateKey = :dateKey ORDER BY sessionStartTime DESC")
    fun getSessionsByDate(dateKey: String): Flow<List<RouterSessionLog>>

    @Query("SELECT * FROM sessions WHERE sessionStartTime >= :startTime AND sessionStartTime <= :endTime ORDER BY sessionStartTime DESC")
    fun getSessionsBetween(startTime: Long, endTime: Long): Flow<List<RouterSessionLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: RouterSessionLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<RouterSessionLog>)

    @Query("UPDATE sessions SET isBanned = :isBanned WHERE macAddress = :macAddress")
    suspend fun banSessionMac(macAddress: String, isBanned: Boolean)
}

