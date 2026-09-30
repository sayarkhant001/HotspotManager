package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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

    @Update
    suspend fun updateProfile(profile: UserProfile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfiles(profiles: List<UserProfile>)

    @Query("DELETE FROM user_profiles WHERE id = :id")
    suspend fun deleteProfile(id: Int)

    @Query("DELETE FROM user_profiles WHERE name = :name")
    suspend fun deleteProfileByName(name: String)

    @Query("DELETE FROM user_profiles")
    suspend fun clearProfiles()

    @Query("SELECT * FROM user_profiles")
    suspend fun getAllProfilesSync(): List<UserProfile>

    @Query("SELECT * FROM vouchers ORDER BY generatedAt DESC")
    fun getAllVouchers(): Flow<List<Voucher>>

    @Query("SELECT * FROM vouchers")
    suspend fun getAllVouchersSync(): List<Voucher>

    @Query("SELECT * FROM vouchers WHERE profileName = :profileName ORDER BY generatedAt DESC")
    fun getVouchersByProfile(profileName: String): Flow<List<Voucher>>

    @Query("SELECT COUNT(*) FROM vouchers WHERE profileName = :profileName")
    suspend fun getVoucherCountForProfile(profileName: String): Int

    @Query("SELECT * FROM vouchers WHERE code = :code LIMIT 1")
    suspend fun getVoucherByCode(code: String): Voucher?

    @Query("SELECT * FROM vouchers WHERE code IN (:codes)")
    suspend fun getVouchersByCodesChunk(codes: List<String>): List<Voucher>

    @androidx.room.Transaction
    suspend fun getVouchersByCodes(codes: List<String>): List<Voucher> {
        if (codes.isEmpty()) return emptyList()
        return codes.chunked(250).flatMap { chunk ->
            getVouchersByCodesChunk(chunk)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVouchers(vouchers: List<Voucher>)

    @Query("DELETE FROM vouchers WHERE id = :id")
    suspend fun deleteVoucher(id: Int)

    @Query("DELETE FROM vouchers WHERE code = :code")
    suspend fun deleteVoucherByCode(code: String)

    @Query("DELETE FROM vouchers WHERE code IN (:codes)")
    suspend fun deleteVouchersByCodesChunk(codes: List<String>)

    @androidx.room.Transaction
    suspend fun deleteVouchersByCodes(codes: List<String>) {
        if (codes.isEmpty()) return
        codes.chunked(250).forEach { chunk ->
            deleteVouchersByCodesChunk(chunk)
        }
    }

    @Query("DELETE FROM vouchers WHERE profileName = :profileName")
    suspend fun deleteVouchersByProfile(profileName: String)

    @Query("DELETE FROM vouchers")
    suspend fun clearAllVouchers()

    @Query("UPDATE vouchers SET isUsed = :isUsed WHERE code = :code")
    suspend fun updateVoucherUsed(code: String, isUsed: Boolean)

    @Query("UPDATE vouchers SET isPrinted = :isPrinted WHERE code = :code")
    suspend fun updateVoucherPrinted(code: String, isPrinted: Boolean)

    @Query("UPDATE vouchers SET isPrinted = 1 WHERE code IN (:codes)")
    suspend fun markVouchersPrintedChunk(codes: List<String>)

    @androidx.room.Transaction
    suspend fun markVouchersPrinted(codes: List<String>) {
        if (codes.isEmpty()) return
        codes.chunked(250).forEach { chunk ->
            markVouchersPrintedChunk(chunk)
        }
    }

    @Query("SELECT * FROM vouchers WHERE isPrinted = 0 ORDER BY generatedAt DESC")
    fun getUnprintedVouchers(): Flow<List<Voucher>>

    @Query("SELECT * FROM sessions ORDER BY sessionStartTime DESC")
    fun getAllSessions(): Flow<List<RouterSessionLog>>

    @Query("SELECT * FROM sessions WHERE dateKey = :dateKey ORDER BY sessionStartTime DESC")
    fun getSessionsByDate(dateKey: String): Flow<List<RouterSessionLog>>

    @Query("SELECT * FROM sessions WHERE sessionStartTime >= :startTime AND sessionStartTime <= :endTime ORDER BY sessionStartTime DESC")
    fun getSessionsBetween(startTime: Long, endTime: Long): Flow<List<RouterSessionLog>>

    @Query("SELECT * FROM sessions WHERE macAddress = :mac AND dateKey = :dateKey LIMIT 1")
    suspend fun getSessionByMacAndDate(mac: String, dateKey: String): RouterSessionLog?

    @Query("UPDATE sessions SET bytesIn = :bytesIn, bytesOut = :bytesOut, dataUsedMb = :dataUsedMb, uptime = :uptime, voucherCode = :voucherCode, ipAddress = :ipAddress WHERE id = :id")
    suspend fun updateSessionUsage(id: Int, bytesIn: Long, bytesOut: Long, dataUsedMb: Double, uptime: String, voucherCode: String, ipAddress: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: RouterSessionLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<RouterSessionLog>)

    @Query("UPDATE sessions SET isBanned = :isBanned WHERE macAddress = :macAddress")
    suspend fun banSessionMac(macAddress: String, isBanned: Boolean)

    @Query("UPDATE vouchers SET profileName = :newName WHERE profileName = :oldName")
    suspend fun updateVoucherProfileName(oldName: String, newName: String)

    @Query("UPDATE vouchers SET isUsed = 0, isPrinted = 0 WHERE code = :code")
    suspend fun renewVoucher(code: String)

    @Query("DELETE FROM vouchers WHERE isUsed = 1")
    suspend fun deleteAllUsedVouchers()

    @Query("SELECT * FROM vouchers WHERE isUsed = 1")
    suspend fun getAllUsedVouchersSync(): List<Voucher>

    @Query("DELETE FROM sessions")
    suspend fun clearAllSessions()

    @Query("UPDATE vouchers SET isUsed = 0")
    suspend fun resetAllVouchersUsage()
}

