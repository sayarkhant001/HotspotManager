package com.example.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.ActiveUser
import com.example.data.remote.RouterStats
import com.example.data.repository.AppRepository
import com.example.domain.models.UserProfile
import com.example.domain.models.Voucher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

    val authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val routerStats = MutableStateFlow<RouterStats?>(null)
    val activeUsers = MutableStateFlow<List<ActiveUser>>(emptyList())

    fun connectToRouter(ip: String, user: String, pass: String) {
        viewModelScope.launch {
            authState.value = AuthState.Loading
            val result = repository.mikrotikClient.connect(ip, user, pass)
            if (result.isSuccess) {
                authState.value = AuthState.Success
                fetchRouterData()
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Failed to connect. Check IP, user, password or network."
                authState.value = AuthState.Error(errorMsg)
            }
        }
    }

    fun fetchRouterData() {
        viewModelScope.launch {
            val stats = repository.getRouterStats()
            if (stats != null) routerStats.value = stats
            
            val users = repository.getActiveHotspotUsers()
            activeUsers.value = users
        }
    }

    fun addProfile(name: String, down: Int, up: Int, data: Int, duration: Int, price: Double, validity: Int) {
        viewModelScope.launch {
            repository.addProfile(UserProfile(
                name = name,
                downloadLimitMbps = down,
                uploadLimitMbps = up,
                dataLimitMb = data,
                durationMinutes = duration,
                price = price,
                validityDays = validity
            ))
        }
    }

    fun generateVouchers(profile: UserProfile, quantity: Int, length: Int, mode: String) {
        viewModelScope.launch {
            val newVouchers = mutableListOf<Voucher>()
            val charPool = when (mode) {
                "Numbers Only" -> ('0'..'9').toList()
                "Letters Only" -> ('A'..'Z').toList()
                else -> ('A'..'Z') + ('0'..'9')
            }
            
            for (i in 0 until quantity) {
                val code = (1..length)
                    .map { Random.nextInt(0, charPool.size) }
                    .map(charPool::get)
                    .joinToString("")
                
                newVouchers.add(
                    Voucher(
                        code = code,
                        profileId = profile.id,
                        profileName = profile.name,
                        downloadLimitMbps = profile.downloadLimitMbps,
                        uploadLimitMbps = profile.uploadLimitMbps,
                        dataLimitMb = profile.dataLimitMb,
                        durationMinutes = profile.durationMinutes
                    )
                )
            }
            repository.addVouchers(newVouchers)
            // Ideally we also create these users in RouterOS hotspot via API, but let's stick to generating them locally first and when connected we can sync or print.
            // For Mikhmon style, we generate and push to MikroTik:
            // This is a simplified approach for demonstration within bounds.
        }
    }

    fun banMac(mac: String) {
        viewModelScope.launch {
            repository.banMac(mac)
            fetchRouterData()
        }
    }
}
