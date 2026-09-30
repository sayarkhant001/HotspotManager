package com.example.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.domain.models.ActiveUser
import com.example.data.remote.RouterStats
import com.example.data.repository.AppRepository
import com.example.domain.models.RouterSessionLog
import com.example.domain.models.UserProfile
import com.example.domain.models.Voucher
import com.example.utils.AppReleaseInfo
import com.example.utils.GitHubUpdateManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}

class MainViewModel(private val repository: AppRepository) : ViewModel() {

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        throwable.printStackTrace()
        userMessage.value = "✗ Error: ${throwable.localizedMessage ?: "Unexpected error"}"
    }

    val appUpdateInfo = MutableStateFlow<AppReleaseInfo?>(null)
    val isCheckingUpdate = MutableStateFlow(false)
    val isDownloadingUpdate = MutableStateFlow(false)
    val downloadProgress = MutableStateFlow(0f)
    val showUpdateDialog = MutableStateFlow(false)

    init {
        // Quietly check for update from GitHub on start
        checkForAppUpdate(manual = false)
    }

    val profiles: StateFlow<List<UserProfile>> = repository.profiles.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val vouchers: StateFlow<List<Voucher>> = repository.vouchers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allSessions: StateFlow<List<RouterSessionLog>> = repository.sessions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val userMessage = MutableStateFlow<String?>(null)

    fun clearUserMessage() {
        userMessage.value = null
    }

    val authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val routerStats = MutableStateFlow<RouterStats?>(null)
    val cpuLoadHistory = MutableStateFlow<List<Float>>(listOf(5f, 10f, 7f, 12f, 8f, 15f))
    val activeUsers = MutableStateFlow<List<ActiveUser>>(emptyList())

    // Real-time Bandwidth (in Mbps)
    val rxSpeedMbps = MutableStateFlow(0.0)
    val txSpeedMbps = MutableStateFlow(0.0)

    // Detected Hotspot Portal Login URL and Wi-Fi SSID
    val hotspotLoginUrl = MutableStateFlow("http://10.10.10.1")
    val hotspotSsid = MutableStateFlow("")

    // Router hardware total data bytes (rx, tx)
    val routerHardwareTotalBytes = MutableStateFlow(Pair(0L, 0L))

    // Date filter for historical metrics ("Today", "Yesterday", "Last 7 Days", "Last 30 Days", "All")
    val selectedDateFilter = MutableStateFlow("Today")

    val filteredSessions: StateFlow<List<RouterSessionLog>> = combine(
        allSessions,
        selectedDateFilter
    ) { sessions, filter ->
        val myanmarTz = TimeZone.getTimeZone("Asia/Yangon")
        val cal = Calendar.getInstance(myanmarTz)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = myanmarTz
        }
        val todayKey = dateFormat.format(cal.time)

        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayKey = dateFormat.format(cal.time)

        cal.time = Date()
        cal.add(Calendar.DAY_OF_YEAR, -7)
        val sevenDaysAgoTime = cal.timeInMillis

        cal.time = Date()
        cal.add(Calendar.DAY_OF_YEAR, -30)
        val thirtyDaysAgoTime = cal.timeInMillis

        when (filter) {
            "Today" -> sessions.filter { it.dateKey == todayKey }
            "Yesterday" -> sessions.filter { it.dateKey == yesterdayKey }
            "Last 7 Days" -> sessions.filter { it.sessionStartTime >= sevenDaysAgoTime }
            "Last 30 Days" -> sessions.filter { it.sessionStartTime >= thirtyDaysAgoTime }
            else -> sessions
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Activated voucher sales amount strictly for used vouchers within date range
    val activatedVoucherSales: StateFlow<Double> = combine(
        vouchers,
        selectedDateFilter
    ) { list, filter ->
        val myanmarTz = TimeZone.getTimeZone("Asia/Yangon")
        val cal = Calendar.getInstance(myanmarTz).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStart = cal.timeInMillis

        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStart = cal.timeInMillis

        cal.time = Date()
        cal.add(Calendar.DAY_OF_YEAR, -7)
        val sevenDaysAgo = cal.timeInMillis

        cal.time = Date()
        cal.add(Calendar.DAY_OF_YEAR, -30)
        val thirtyDaysAgo = cal.timeInMillis

        val activated = list.filter { it.isUsed }
        val filtered = when (filter) {
            "Today" -> activated.filter { it.generatedAt >= todayStart }
            "Yesterday" -> activated.filter { it.generatedAt in yesterdayStart until todayStart }
            "Last 7 Days" -> activated.filter { it.generatedAt >= sevenDaysAgo }
            "Last 30 Days" -> activated.filter { it.generatedAt >= thirtyDaysAgo }
            else -> activated
        }
        filtered.sumOf { it.price }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val isSyncingVouchers = MutableStateFlow(false)

    val profileVoucherCounts: StateFlow<Map<String, Int>> = vouchers.map { list ->
        list.groupingBy { it.profileName }.eachCount()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private var pollingJob: Job? = null

    fun connectToRouter(ip: String, user: String, pass: String) {
        viewModelScope.launch {
            authState.value = AuthState.Loading
            val result = repository.mikrotikClient.connect(ip, user, pass)
            if (result.isSuccess) {
                authState.value = AuthState.Success
                startPolling()
                syncProfiles()
                syncVouchers()
                fetchHotspotNetworkInfo()
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Failed to connect. Check IP, user, password or network."
                authState.value = AuthState.Error(errorMsg)
            }
        }
    }

    fun fetchHotspotNetworkInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = repository.getHotspotLoginUrl()
                hotspotLoginUrl.value = url
                val ssid = repository.getHotspotSsid()
                if (!ssid.isNullOrBlank()) {
                    hotspotSsid.value = ssid
                }
            } catch (_: Exception) {}
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                if (repository.mikrotikClient.isConnected() || repository.mikrotikClient.ensureConnected()) {
                    fetchRouterData()
                }
                delay(3500) // Poll every 3.5 seconds to leave socket clear for immediate commands
            }
        }
    }

    fun getConnectedIp(): String = repository.mikrotikClient.getCurrentIp()
    fun getConnectedUser(): String = repository.mikrotikClient.getCurrentUser()
    fun getConnectedPass(): String = repository.mikrotikClient.getCurrentPass()

    fun changeLoginPassword(oldPass: String, newPass: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val res = repository.changeRouterPassword(oldPass, newPass)
            if (res.isSuccess) {
                userMessage.value = "Password updated successfully!"
                onResult(true, "Password updated successfully!")
            } else {
                val err = res.exceptionOrNull()?.message ?: "Failed to change password"
                userMessage.value = err
                onResult(false, err)
            }
        }
    }

    fun disconnectFromRouter() {
        pollingJob?.cancel()
        viewModelScope.launch {
            repository.mikrotikClient.disconnect()
            authState.value = AuthState.Idle
        }
    }

    private var isFetchingRouterData = false

    fun checkForAppUpdate(manual: Boolean = false) {
        viewModelScope.launch(exceptionHandler) {
            try {
                if (manual) isCheckingUpdate.value = true
                val update = GitHubUpdateManager.checkForUpdate()
                appUpdateInfo.value = update
                if (update != null && update.isNewer) {
                    showUpdateDialog.value = true
                } else if (manual) {
                    userMessage.value = "✓ You are using the latest version (v${BuildConfig.VERSION_NAME})"
                }
            } catch (t: Throwable) {
                if (manual) {
                    userMessage.value = "✗ Update check failed: ${t.message ?: "Unknown error"}"
                }
            } finally {
                isCheckingUpdate.value = false
            }
        }
    }

    fun dismissUpdateDialog() {
        showUpdateDialog.value = false
    }

    fun openUpdateDialog() {
        showUpdateDialog.value = true
    }

    fun downloadAndInstallUpdate(context: Context) {
        val info = appUpdateInfo.value ?: return
        viewModelScope.launch(exceptionHandler) {
            try {
                isDownloadingUpdate.value = true
                downloadProgress.value = 0f
                userMessage.value = "Downloading update v${info.versionName}..."
                val res = GitHubUpdateManager.downloadAndInstallApk(context, info) { p ->
                    downloadProgress.value = p
                }
                if (res.isFailure) {
                    userMessage.value = "✗ Download failed: ${res.exceptionOrNull()?.message}"
                }
            } catch (t: Throwable) {
                userMessage.value = "✗ Error installing update: ${t.message}"
            } finally {
                isDownloadingUpdate.value = false
            }
        }
    }

    fun fetchRouterData() {
        if (isFetchingRouterData) return
        viewModelScope.launch(exceptionHandler) {
            if (isFetchingRouterData) return@launch
            isFetchingRouterData = true
            try {
                val stats = repository.getRouterStats()
                if (stats != null) {
                    routerStats.value = stats
                    val loadFloat = stats.cpuLoad.replace("%", "").trim().toFloatOrNull() ?: 0f
                    val cur = cpuLoadHistory.value.toMutableList()
                    cur.add(loadFloat)
                    if (cur.size > 20) cur.removeAt(0)
                    cpuLoadHistory.value = cur
                }

                val users = repository.getActiveHotspotUsers()
                activeUsers.value = users

                // Monitor interface throughput & hardware traffic in a single optimized query
                val metrics = repository.getInterfaceMetrics()
                val rx = (metrics.rxSpeedBps / 1_000_000.0)
                val tx = (metrics.txSpeedBps / 1_000_000.0)
                rxSpeedMbps.value = Math.round(rx * 100.0) / 100.0
                txSpeedMbps.value = Math.round(tx * 100.0) / 100.0
                routerHardwareTotalBytes.value = Pair(metrics.totalRxBytes, metrics.totalTxBytes)

                // Log session data to Room DB for date analytics with deduplication
                if (users.isNotEmpty()) {
                    repository.recordSessions(users)
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            } finally {
                isFetchingRouterData = false
            }
        }
    }

    fun resetAllStatistics() {
        viewModelScope.launch {
            val res = repository.resetAllStatistics()
            if (res.isSuccess) {
                rxSpeedMbps.value = 0.0
                txSpeedMbps.value = 0.0
                routerHardwareTotalBytes.value = Pair(0L, 0L)
                userMessage.value = "✓ All router traffic and usage statistics have been reset to zero!"
                fetchRouterData()
            } else {
                userMessage.value = "✗ Error: ${res.exceptionOrNull()?.message ?: "Failed to reset statistics"}"
            }
        }
    }

    fun markVouchersAsPrinted(codes: List<String>) {
        viewModelScope.launch {
            repository.markVouchersAsPrinted(codes)
        }
    }

    fun syncProfiles() {
        viewModelScope.launch {
            repository.syncProfilesFromRouter()
        }
    }

    fun syncVouchers() {
        viewModelScope.launch {
            isSyncingVouchers.value = true
            repository.syncVouchersFromRouter()
            isSyncingVouchers.value = false
        }
    }

    fun deleteProfile(profileName: String) {
        viewModelScope.launch {
            val res = repository.deleteProfile(profileName)
            if (res.isSuccess) {
                userMessage.value = "✓ Profile '$profileName' deleted from router!"
                repository.syncProfilesFromRouter()
                repository.syncVouchersFromRouter()
            } else {
                userMessage.value = "✗ Router Error: ${res.exceptionOrNull()?.message ?: "Failed to delete profile"}"
            }
        }
    }

    fun updateProfile(oldName: String, profile: UserProfile) {
        viewModelScope.launch {
            val res = repository.updateProfile(oldName, profile)
            if (res.isSuccess) {
                userMessage.value = "✓ Profile '${profile.name}' updated on router!"
                repository.syncProfilesFromRouter()
                repository.syncVouchersFromRouter()
            } else {
                userMessage.value = "✗ Router Error: ${res.exceptionOrNull()?.message ?: "Failed to update profile"}"
            }
        }
    }

    fun removeZeroVoucherProfiles() {
        viewModelScope.launch {
            repository.removeProfilesWithZeroVouchers()
        }
    }

    fun deleteVoucher(voucher: Voucher) {
        viewModelScope.launch(exceptionHandler) {
            try {
                val res = repository.deleteVoucher(voucher)
                if (res.isSuccess) {
                    userMessage.value = "✓ Voucher '${voucher.code}' deleted!"
                } else {
                    userMessage.value = "✗ Router Error: ${res.exceptionOrNull()?.message ?: "Failed to delete voucher"}"
                }
            } catch (t: Throwable) {
                userMessage.value = "✗ Error: ${t.message ?: "Failed to delete voucher"}"
            }
        }
    }

    fun deleteSelectedVouchers(codes: Set<String>) {
        if (codes.isEmpty()) return
        val count = codes.size
        viewModelScope.launch(exceptionHandler) {
            try {
                userMessage.value = "Deleting $count vouchers..."
                val res = repository.deleteVouchersByCodes(codes)
                if (res.isSuccess) {
                    userMessage.value = "✓ Deleted $count vouchers!"
                } else {
                    userMessage.value = "✗ Router Error: ${res.exceptionOrNull()?.message ?: "Failed to delete vouchers"}"
                }
            } catch (t: Throwable) {
                userMessage.value = "✗ Error: ${t.message ?: "Failed to delete vouchers"}"
            }
        }
    }

    fun renewVoucher(voucher: Voucher) {
        viewModelScope.launch(exceptionHandler) {
            try {
                val res = repository.renewVoucher(voucher)
                if (res.isSuccess) {
                    userMessage.value = "✓ Voucher '${voucher.code}' renewed on router!"
                } else {
                    userMessage.value = "✗ Router Error: ${res.exceptionOrNull()?.message ?: "Failed to renew voucher"}"
                }
            } catch (t: Throwable) {
                userMessage.value = "✗ Error: ${t.message ?: "Failed to renew voucher"}"
            }
        }
    }

    fun deleteUsedVouchers() {
        viewModelScope.launch(exceptionHandler) {
            try {
                userMessage.value = "Deleting used vouchers..."
                val res = repository.deleteUsedVouchers()
                if (res.isSuccess) {
                    userMessage.value = "✓ Cleaned used vouchers from router!"
                } else {
                    userMessage.value = "✗ Router Error: ${res.exceptionOrNull()?.message ?: "Failed to delete used vouchers"}"
                }
            } catch (t: Throwable) {
                userMessage.value = "✗ Error: ${t.message ?: "Failed to delete used vouchers"}"
            }
        }
    }

    fun setDateFilter(filter: String) {
        selectedDateFilter.value = filter
    }

    fun addProfile(
        name: String,
        rateLimit: String,
        sharedUsers: Int,
        dataMb: Int,
        durMin: Int,
        price: Double,
        sellingPrice: Double,
        validityDays: Int
    ) {
        viewModelScope.launch {
            val res = repository.addProfile(
                UserProfile(
                    name = name,
                    sharedUsers = sharedUsers,
                    rateLimit = rateLimit,
                    downloadLimitMbps = parseSpeed(rateLimit),
                    uploadLimitMbps = parseSpeed(rateLimit),
                    dataLimitMb = dataMb,
                    durationMinutes = durMin,
                    price = price,
                    sellingPrice = sellingPrice,
                    validityDays = validityDays
                )
            )
            if (res.isSuccess) {
                userMessage.value = "✓ Profile '$name' created on router successfully!"
                repository.syncProfilesFromRouter()
            } else {
                userMessage.value = "✗ Router Error: ${res.exceptionOrNull()?.message ?: "Failed to create profile"}"
            }
        }
    }

    private fun parseSpeed(rateLimit: String): Int {
        val parts = rateLimit.split("/")
        val dl = parts.getOrNull(0)?.trim() ?: ""
        return when {
            dl.endsWith("M", ignoreCase = true) -> dl.dropLast(1).toIntOrNull() ?: 5
            dl.endsWith("k", ignoreCase = true) -> (dl.dropLast(1).toIntOrNull() ?: 5000) / 1000
            else -> dl.toIntOrNull() ?: 5
        }
    }

    fun generateVouchers(
        profile: UserProfile,
        quantity: Int,
        length: Int,
        charMode: String,
        isAccount: Boolean = false,
        prefix: String = "",
        onComplete: ((List<Voucher>) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val safeLength = if (length < 4) 4 else length
            val isHyphenated = charMode.contains("Hyphenated", ignoreCase = true)

            val charPool = when {
                charMode.contains("Numbers Only", ignoreCase = true) ||
                charMode.contains("Hyphenated Numbers", ignoreCase = true) ||
                charMode.equals("Numbers", ignoreCase = true) -> ('0'..'9').toList()
                charMode.contains("Alphabet Only", ignoreCase = true) ||
                charMode.contains("Uppercase", ignoreCase = true) ||
                charMode.contains("Letters", ignoreCase = true) -> ('A'..'Z').toList()
                charMode.contains("Lowercase", ignoreCase = true) -> ('a'..'z').toList()
                charMode.contains("Numbers + Alphabet", ignoreCase = true) ||
                charMode.contains("Alphanumeric", ignoreCase = true) -> ('0'..'9') + ('A'..'Z')
                else -> ('0'..'9').toList()
            }

            // Initial comment on generation includes profile name and validity.
            // When user logs in, router script 'voucher-activate' reads validity and
            // sets a continuous expiration countdown timer from the exact time of insertion!
            val validityTag = when {
                profile.durationMinutes in 1..59 -> "${profile.durationMinutes}m"
                profile.durationMinutes in 60..1439 && profile.durationMinutes % 60 == 0 -> "${profile.durationMinutes / 60}h"
                profile.durationMinutes in 60..1439 -> "${profile.durationMinutes}m"
                profile.durationMinutes >= 1440 && profile.durationMinutes % 1440 == 0 -> "${profile.durationMinutes / 1440}d"
                profile.validityDays > 0 -> "${profile.validityDays}d"
                else -> "1d"
            }
            val initialComment = "${profile.name} V:$validityTag"

            val newVouchers = mutableListOf<Voucher>()

            for (i in 0 until quantity) {
                val raw = (1..safeLength)
                    .map { Random.nextInt(0, charPool.size) }
                    .map(charPool::get)
                    .joinToString("")

                val formattedCode = if (isHyphenated && raw.length >= 4) {
                    val half = raw.length / 2
                    raw.substring(0, half) + "-" + raw.substring(half)
                } else {
                    raw
                }

                val code = if (prefix.isNotBlank()) "$prefix$formattedCode" else formattedCode
                val password = if (isAccount) {
                    val rawPass = (1..safeLength)
                        .map { Random.nextInt(0, charPool.size) }
                        .map(charPool::get)
                        .joinToString("")
                    if (isHyphenated && rawPass.length >= 4) {
                        val half = rawPass.length / 2
                        rawPass.substring(0, half) + "-" + rawPass.substring(half)
                    } else {
                        rawPass
                    }
                } else code

                newVouchers.add(
                    Voucher(
                        code = code,
                        username = code,
                        password = password,
                        isAccount = isAccount,
                        profileId = profile.id,
                        profileName = profile.name,
                        downloadLimitMbps = profile.downloadLimitMbps,
                        uploadLimitMbps = profile.uploadLimitMbps,
                        dataLimitMb = profile.dataLimitMb,
                        durationMinutes = profile.durationMinutes,
                        validityDays = profile.validityDays,
                        price = profile.price,
                        comment = initialComment
                    )
                )
            }
            val res = repository.addVouchers(newVouchers, pushToRouter = true)
            if (res.isSuccess) {
                userMessage.value = "✓ Generated ${newVouchers.size} vouchers on router instantly!"
                onComplete?.invoke(newVouchers)
            } else {
                userMessage.value = "✗ Router Error: ${res.exceptionOrNull()?.message ?: "Failed to push vouchers to router"}"
            }
        }
    }

    fun kickUser(sessionId: String) {
        viewModelScope.launch {
            val res = repository.kickSession(sessionId)
            if (res.isSuccess) {
                userMessage.value = "✓ Active session kicked from router!"
            } else {
                userMessage.value = "✗ Failed to kick session from router"
            }
            fetchRouterData()
        }
    }

    fun banMac(mac: String) {
        viewModelScope.launch {
            val res = repository.banMac(mac)
            if (res.isSuccess) {
                userMessage.value = "✓ Blocked MAC $mac on router!"
            } else {
                userMessage.value = "✗ Failed to block MAC on router"
            }
            fetchRouterData()
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}

