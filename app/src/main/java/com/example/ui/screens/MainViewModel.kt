package com.example.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.ActiveUser
import com.example.data.remote.RouterStats
import com.example.data.repository.AppRepository
import com.example.domain.models.RouterSessionLog
import com.example.domain.models.UserProfile
import com.example.domain.models.Voucher
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

    val authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val routerStats = MutableStateFlow<RouterStats?>(null)
    val activeUsers = MutableStateFlow<List<ActiveUser>>(emptyList())

    // Real-time Bandwidth (in Mbps)
    val rxSpeedMbps = MutableStateFlow(0.0)
    val txSpeedMbps = MutableStateFlow(0.0)

    // Date filter for historical metrics ("Today", "Yesterday", "Last 7 Days", "All")
    val selectedDateFilter = MutableStateFlow("Today")

    val filteredSessions: StateFlow<List<RouterSessionLog>> = combine(
        allSessions,
        selectedDateFilter
    ) { sessions, filter ->
        val cal = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayKey = dateFormat.format(cal.time)

        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayKey = dateFormat.format(cal.time)

        cal.time = Date()
        cal.add(Calendar.DAY_OF_YEAR, -7)
        val sevenDaysAgoTime = cal.timeInMillis

        when (filter) {
            "Today" -> sessions.filter { it.dateKey == todayKey }
            "Yesterday" -> sessions.filter { it.dateKey == yesterdayKey }
            "Last 7 Days" -> sessions.filter { it.sessionStartTime >= sevenDaysAgoTime }
            else -> sessions
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var pollingJob: Job? = null

    fun connectToRouter(ip: String, user: String, pass: String) {
        viewModelScope.launch {
            authState.value = AuthState.Loading
            val result = repository.mikrotikClient.connect(ip, user, pass)
            if (result.isSuccess) {
                authState.value = AuthState.Success
                startPolling()
                syncProfiles()
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Failed to connect. Check IP, user, password or network."
                authState.value = AuthState.Error(errorMsg)
            }
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive && repository.mikrotikClient.isConnected()) {
                fetchRouterData()
                delay(2000) // Poll every 2 seconds
            }
        }
    }

    fun fetchRouterData() {
        viewModelScope.launch {
            val stats = repository.getRouterStats()
            if (stats != null) routerStats.value = stats

            val users = repository.getActiveHotspotUsers()
            activeUsers.value = users

            // Monitor interface throughput
            val traffic = repository.getInterfaceTraffic()
            // Convert bits/s to Mbps (with 2 decimal places)
            val rx = (traffic.first / 1_000_000.0)
            val tx = (traffic.second / 1_000_000.0)
            rxSpeedMbps.value = Math.round(rx * 100.0) / 100.0
            txSpeedMbps.value = Math.round(tx * 100.0) / 100.0

            // Log session data to Room DB for date analytics
            if (users.isNotEmpty()) {
                repository.recordSessions(users)
            }
        }
    }

    fun syncProfiles() {
        viewModelScope.launch {
            repository.syncProfilesFromRouter()
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
            repository.addProfile(
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
        prefix: String = ""
    ) {
        viewModelScope.launch {
            val safeLength = if (length < 8) 8 else length
            val charPool = when (charMode) {
                "Numbers Only" -> ('0'..'9').toList()
                "Letters Only (a-z)" -> ('a'..'z').toList()
                "Uppercase (A-Z)" -> ('A'..'Z').toList()
                "Alphanumeric (A-Z + 0-9)" -> ('A'..'Z') + ('0'..'9')
                else -> ('a'..'z') + ('A'..'Z') + ('0'..'9')
            }

            // Expiry date format matching router's voucher-activate script: EXP:YYYYMMDD
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, if (profile.validityDays > 0) profile.validityDays else 1)
            val expFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            val expComment = "EXP:${expFormat.format(cal.time)}"

            val newVouchers = mutableListOf<Voucher>()

            for (i in 0 until quantity) {
                val randomPart = (1..safeLength)
                    .map { Random.nextInt(0, charPool.size) }
                    .map(charPool::get)
                    .joinToString("")

                val code = if (prefix.isNotBlank()) "$prefix$randomPart" else randomPart
                val password = if (isAccount) {
                    (1..safeLength)
                        .map { Random.nextInt(0, charPool.size) }
                        .map(charPool::get)
                        .joinToString("")
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
                        comment = expComment
                    )
                )
            }
            repository.addVouchers(newVouchers, pushToRouter = true)
        }
    }

    fun kickUser(sessionId: String) {
        viewModelScope.launch {
            repository.kickSession(sessionId)
            fetchRouterData()
        }
    }

    fun banMac(mac: String) {
        viewModelScope.launch {
            repository.banMac(mac)
            fetchRouterData()
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}

