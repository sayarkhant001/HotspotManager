package com.example.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.domain.models.AccessPointDevice
import com.example.domain.models.ActiveUser
import com.example.domain.models.IpBinding
import com.example.domain.models.NetworkTopologyData
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
    val isExecutingScript = MutableStateFlow(false)
    val scriptExecutionOutput = MutableStateFlow<String?>(null)

    // Captive Portal & RSC File Upload State
    val selectedPortalFileUri = MutableStateFlow<android.net.Uri?>(null)
    val selectedPortalFileName = MutableStateFlow<String?>(null)
    val selectedPortalFileSize = MutableStateFlow<Long>(0L)
    val selectedPortalFileValidation = MutableStateFlow<String?>(null)
    val isPortalValid = MutableStateFlow<Boolean>(false)
    val isRscFile = MutableStateFlow<Boolean>(false)
    val selectedRscContent = MutableStateFlow<String?>(null)
    val isUploadingPortal = MutableStateFlow<Boolean>(false)

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

    // Cloud User & Multi-Router Management
    val cloudUser = MutableStateFlow<com.example.data.remote.CloudUser?>(null)
    val assignedRouters = MutableStateFlow<List<com.example.data.remote.CloudRouter>>(emptyList())
    val activeRouterName = MutableStateFlow<String>("")
    val connectionMode = MutableStateFlow<String>("local") // "local", "cloud_remote", "direct"
    val cloudLoginStatus = MutableStateFlow<String?>(null)

    // Network Topology & Whitelisting State
    val networkTopology = MutableStateFlow(NetworkTopologyData())
    val isTopologyLoading = MutableStateFlow(false)
    val ipBindings = MutableStateFlow<List<IpBinding>>(emptyList())

    val whitelistedClients: StateFlow<List<IpBinding>> = ipBindings.map { list ->
        list.filter { it.type == "bypassed" && !it.disabled }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bannedClients: StateFlow<List<IpBinding>> = ipBindings.map { list ->
        list.filter { it.type == "blocked" || it.comment.contains("Banned", ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

        val cal7 = Calendar.getInstance(myanmarTz).apply {
            add(Calendar.DAY_OF_YEAR, -7)
        }
        val sevenDaysAgoTime = cal7.timeInMillis

        val cal30 = Calendar.getInstance(myanmarTz).apply {
            add(Calendar.DAY_OF_YEAR, -30)
        }
        val thirtyDaysAgoTime = cal30.timeInMillis

        val distinctSessions = sessions.distinctBy { Pair(it.macAddress, it.dateKey) }
        when (filter) {
            "Today" -> distinctSessions.filter { it.dateKey == todayKey }
            "Yesterday" -> distinctSessions.filter { it.dateKey == yesterdayKey }
            "Last 7 Days" -> distinctSessions.filter { it.sessionStartTime >= sevenDaysAgoTime }
            "Last 30 Days" -> distinctSessions.filter { it.sessionStartTime >= thirtyDaysAgoTime }
            else -> distinctSessions
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Activated voucher sales amount strictly for used vouchers within date range
    val activatedVoucherSales: StateFlow<Double> = combine(
        vouchers,
        selectedDateFilter,
        activeUsers
    ) { list, filter, actives ->
        val activeCodes = actives.map { it.user }.toSet()
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

        val cal7 = Calendar.getInstance(myanmarTz).apply {
            add(Calendar.DAY_OF_YEAR, -7)
        }
        val sevenDaysAgo = cal7.timeInMillis

        val cal30 = Calendar.getInstance(myanmarTz).apply {
            add(Calendar.DAY_OF_YEAR, -30)
        }
        val thirtyDaysAgo = cal30.timeInMillis

        val distinctVouchers = list.distinctBy { it.code }
        val activated = distinctVouchers.filter { v ->
            activeCodes.contains(v.code) || activeCodes.contains(v.username) ||
            v.activatedAt != null || (v.isUsed && v.generatedAt > 0L)
        }
        val filtered = when (filter) {
            "Today" -> activated.filter { v ->
                val isActiveNow = activeCodes.contains(v.code) || activeCodes.contains(v.username)
                val act = v.activatedAt ?: (if (isActiveNow) System.currentTimeMillis() else v.generatedAt)
                isActiveNow || act >= todayStart
            }
            "Yesterday" -> activated.filter { v ->
                val act = v.activatedAt ?: v.generatedAt
                act in yesterdayStart until todayStart
            }
            "Last 7 Days" -> activated.filter { v ->
                val isActiveNow = activeCodes.contains(v.code) || activeCodes.contains(v.username)
                val act = v.activatedAt ?: (if (isActiveNow) System.currentTimeMillis() else v.generatedAt)
                isActiveNow || act >= sevenDaysAgo
            }
            "Last 30 Days" -> activated.filter { v ->
                val isActiveNow = activeCodes.contains(v.code) || activeCodes.contains(v.username)
                val act = v.activatedAt ?: (if (isActiveNow) System.currentTimeMillis() else v.generatedAt)
                isActiveNow || act >= thirtyDaysAgo
            }
            else -> activated
        }
        filtered.sumOf { it.price }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Data usage (in MB) strictly consumed by vouchers active/activated within selected date range
    val dateFilteredVoucherDataMb: StateFlow<Double> = combine(
        vouchers,
        selectedDateFilter,
        activeUsers
    ) { list, filter, actives ->
        val activeCodes = actives.map { it.user }.toSet()
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

        val cal7 = Calendar.getInstance(myanmarTz).apply {
            add(Calendar.DAY_OF_YEAR, -7)
        }
        val sevenDaysAgo = cal7.timeInMillis

        val cal30 = Calendar.getInstance(myanmarTz).apply {
            add(Calendar.DAY_OF_YEAR, -30)
        }
        val thirtyDaysAgo = cal30.timeInMillis

        val distinctVouchers = list.distinctBy { it.code }
        val activated = distinctVouchers.filter { v ->
            activeCodes.contains(v.code) || activeCodes.contains(v.username) ||
            v.activatedAt != null || (v.isUsed && v.generatedAt > 0L) || (v.bytesIn + v.bytesOut) > 0L
        }
        val filtered = when (filter) {
            "Today" -> activated.filter { v ->
                val isActiveNow = activeCodes.contains(v.code) || activeCodes.contains(v.username)
                val act = v.activatedAt ?: (if (isActiveNow) System.currentTimeMillis() else v.generatedAt)
                isActiveNow || act >= todayStart
            }
            "Yesterday" -> activated.filter { v ->
                val act = v.activatedAt ?: v.generatedAt
                act in yesterdayStart until todayStart
            }
            "Last 7 Days" -> activated.filter { v ->
                val isActiveNow = activeCodes.contains(v.code) || activeCodes.contains(v.username)
                val act = v.activatedAt ?: (if (isActiveNow) System.currentTimeMillis() else v.generatedAt)
                isActiveNow || act >= sevenDaysAgo
            }
            "Last 30 Days" -> activated.filter { v ->
                val isActiveNow = activeCodes.contains(v.code) || activeCodes.contains(v.username)
                val act = v.activatedAt ?: (if (isActiveNow) System.currentTimeMillis() else v.generatedAt)
                isActiveNow || act >= thirtyDaysAgo
            }
            else -> activated
        }
        val activeBytesMap = actives.associate { a ->
            val bin = a.bytesIn.toLongOrNull() ?: 0L
            val bout = a.bytesOut.toLongOrNull() ?: 0L
            a.user to (bin + bout)
        }
        val voucherCodes = mutableSetOf<String>()
        val voucherMb = filtered.sumOf { v ->
            voucherCodes.add(v.code)
            voucherCodes.add(v.username)
            val liveBytes = activeBytesMap[v.code] ?: activeBytesMap[v.username] ?: 0L
            val dbBytes = v.bytesIn + v.bytesOut
            maxOf(dbBytes, liveBytes) / (1024.0 * 1024.0)
        }
        val remainingActiveMb = if (filter == "Today" || filter == "All" || filter == "Last 7 Days" || filter == "Last 30 Days") {
            actives.filter { a ->
                !voucherCodes.contains(a.user) &&
                !com.example.utils.DeviceModelDetector.isAirMetroOrBridge(a.user, a.hostName, a.comment, a.macAddress)
            }.sumOf { a ->
                val bin = a.bytesIn.toLongOrNull() ?: 0L
                val bout = a.bytesOut.toLongOrNull() ?: 0L
                (bin + bout) / (1024.0 * 1024.0)
            }
        } else 0.0
        voucherMb + remainingActiveMb
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val dateFilteredVoucherCount: StateFlow<Int> = combine(
        vouchers,
        selectedDateFilter,
        activeUsers
    ) { list, filter, actives ->
        val activeCodes = actives.map { it.user }.toSet()
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

        val cal7 = Calendar.getInstance(myanmarTz).apply {
            add(Calendar.DAY_OF_YEAR, -7)
        }
        val sevenDaysAgo = cal7.timeInMillis

        val cal30 = Calendar.getInstance(myanmarTz).apply {
            add(Calendar.DAY_OF_YEAR, -30)
        }
        val thirtyDaysAgo = cal30.timeInMillis

        val distinctVouchers = list.distinctBy { it.code }
        val activated = distinctVouchers.filter { v ->
            activeCodes.contains(v.code) || activeCodes.contains(v.username) ||
            v.activatedAt != null || (v.isUsed && v.generatedAt > 0L) || (v.bytesIn + v.bytesOut) > 0L
        }
        val filtered = when (filter) {
            "Today" -> activated.filter { v ->
                val isActiveNow = activeCodes.contains(v.code) || activeCodes.contains(v.username)
                val act = v.activatedAt ?: (if (isActiveNow) System.currentTimeMillis() else v.generatedAt)
                isActiveNow || act >= todayStart
            }
            "Yesterday" -> activated.filter { v ->
                val act = v.activatedAt ?: v.generatedAt
                act in yesterdayStart until todayStart
            }
            "Last 7 Days" -> activated.filter { v ->
                val isActiveNow = activeCodes.contains(v.code) || activeCodes.contains(v.username)
                val act = v.activatedAt ?: (if (isActiveNow) System.currentTimeMillis() else v.generatedAt)
                isActiveNow || act >= sevenDaysAgo
            }
            "Last 30 Days" -> activated.filter { v ->
                val isActiveNow = activeCodes.contains(v.code) || activeCodes.contains(v.username)
                val act = v.activatedAt ?: (if (isActiveNow) System.currentTimeMillis() else v.generatedAt)
                isActiveNow || act >= thirtyDaysAgo
            }
            else -> activated
        }
        val voucherCodes = mutableSetOf<String>()
        filtered.forEach { v ->
            voucherCodes.add(v.code)
            voucherCodes.add(v.username)
        }
        val uncountedActives = if (filter == "Today" || filter == "All" || filter == "Last 7 Days" || filter == "Last 30 Days") {
            actives.count { !voucherCodes.contains(it.user) }
        } else 0
        filtered.size + uncountedActives
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val isSyncingVouchers = MutableStateFlow(false)

    val profileVoucherCounts: StateFlow<Map<String, Int>> = vouchers.map { list ->
        list.groupingBy { it.profileName }.eachCount()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private var pollingJob: Job? = null

    fun connectToRouter(ip: String, user: String, pass: String) {
        viewModelScope.launch {
            authState.value = AuthState.Loading
            cloudLoginStatus.value = "Router သို့ ချိတ်ဆက်နေပါသည်..."
            connectionMode.value = "direct"
            activeRouterName.value = ip
            val result = repository.mikrotikClient.connect(ip, user, pass)
            cloudLoginStatus.value = null
            if (result.isSuccess) {
                authState.value = AuthState.Success
                startPolling()
                fetchHotspotNetworkInfo()
                // Sequential sync to ensure Profiles + prices are in Room DB BEFORE vouchers map them, and APs topology is auto-loaded
                viewModelScope.launch {
                    try {
                        isSyncingVouchers.value = true
                        repository.syncProfilesFromRouter()
                        repository.syncVouchersFromRouter()
                        val topo = repository.getNetworkTopology()
                        networkTopology.value = topo
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        isSyncingVouchers.value = false
                    }
                }
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Failed to connect. Check IP, user, password or network."
                authState.value = AuthState.Error(errorMsg)
            }
        }
    }

    fun loginToCloud(
        context: Context,
        username: String,
        password: String,
        selectedRouterId: Int? = null,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            authState.value = AuthState.Loading
            cloudLoginStatus.value = "အကောင့် စစ်ဆေးနေပါသည်..."

            val loginRes = com.example.data.remote.CloudApiClient.login(username.trim(), password)
            if (loginRes.isFailure) {
                val err = loginRes.exceptionOrNull()?.message ?: "Login failed"
                cloudLoginStatus.value = null
                authState.value = AuthState.Error(err)
                onResult(false, err)
                return@launch
            }

            val (token, user) = loginRes.getOrThrow()
            com.example.data.remote.CloudApiClient.saveAuth(context, token, user)
            cloudUser.value = user

            cloudLoginStatus.value = "သတ်မှတ်ထားသော Router ကို ရှာဖွေနေပါသည်..."
            val routersRes = com.example.data.remote.CloudApiClient.getCustomerRouters(context)
            if (routersRes.isFailure) {
                val err = routersRes.exceptionOrNull()?.message ?: "Failed to fetch routers"
                cloudLoginStatus.value = null
                authState.value = AuthState.Error(err)
                onResult(false, err)
                return@launch
            }

            val routers = routersRes.getOrThrow()
            assignedRouters.value = routers
            if (routers.isEmpty()) {
                val err = "ဤအကောင့်တွင် ချိတ်ဆက်ထားသော Router မရှိသေးပါ။ ကျေးဇူးပြု၍ Admin ထံ ဆက်သွယ်ပါ။"
                cloudLoginStatus.value = null
                authState.value = AuthState.Error(err)
                onResult(false, err)
                return@launch
            }

            val router = (if (selectedRouterId != null) routers.firstOrNull { it.id == selectedRouterId } else null) ?: routers.first()
            activeRouterName.value = router.name
            cloudLoginStatus.value = "${router.name} သို့ ချိတ်ဆက်နေပါသည်..."

            val localHost = router.localAddress.substringBefore(":").ifBlank { "10.10.10.1" }
            val isLocalReachable = com.example.data.remote.CloudApiClient.isLocalRouterReachable(localHost, 8728)

            var connectRes: Result<Unit>
            if (isLocalReachable) {
                connectRes = repository.mikrotikClient.connect(localHost, router.apiUser, router.apiPass)
                if (connectRes.isSuccess) {
                    connectionMode.value = "local"
                } else {
                    val remoteHost = router.remoteAddress.ifBlank { "3.84.81.152:${router.remotePort}" }
                    connectRes = repository.mikrotikClient.connect(remoteHost, router.apiUser, router.apiPass)
                    if (connectRes.isSuccess) {
                        connectionMode.value = "cloud_remote"
                    }
                }
            } else {
                val remoteHost = router.remoteAddress.ifBlank { "3.84.81.152:${router.remotePort}" }
                connectRes = repository.mikrotikClient.connect(remoteHost, router.apiUser, router.apiPass)
                if (connectRes.isSuccess) {
                    connectionMode.value = "cloud_remote"
                } else {
                    connectRes = repository.mikrotikClient.connect(localHost, router.apiUser, router.apiPass)
                    if (connectRes.isSuccess) {
                        connectionMode.value = "local"
                    }
                }
            }

            cloudLoginStatus.value = null
            if (connectRes.isSuccess) {
                authState.value = AuthState.Success
                startPolling()
                fetchHotspotNetworkInfo()
                viewModelScope.launch {
                    try {
                        isSyncingVouchers.value = true
                        repository.syncProfilesFromRouter()
                        repository.syncVouchersFromRouter()
                        val topo = repository.getNetworkTopology()
                        networkTopology.value = topo
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        isSyncingVouchers.value = false
                    }
                }
                onResult(true, null)
            } else {
                val err = connectRes.exceptionOrNull()?.message ?: "Failed to connect to router ${router.name}"
                authState.value = AuthState.Error(err)
                onResult(false, err)
            }
        }
    }

    fun disconnectFromRouter(context: Context? = null) {
        pollingJob?.cancel()
        viewModelScope.launch {
            repository.mikrotikClient.disconnect()
            if (context != null) {
                com.example.data.remote.CloudApiClient.clearAuth(context)
            }
            cloudUser.value = null
            activeRouterName.value = ""
            authState.value = AuthState.Idle
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
                delay(6000) // Poll every 6 seconds for balanced speed and router load
            }
        }
    }

    fun fetchActiveUsersFast() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val users = repository.getActiveHotspotUsers()
                activeUsers.value = users
            } catch (_: Exception) {}
        }
    }

    fun onPortalFileSelected(context: Context, uri: android.net.Uri) {
        selectedPortalFileUri.value = uri
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var fileName = "unknown"
                var fileSize = 0L
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIdx >= 0) fileName = cursor.getString(nameIdx)
                        if (sizeIdx >= 0) fileSize = cursor.getLong(sizeIdx)
                    }
                }
                selectedPortalFileName.value = fileName
                selectedPortalFileSize.value = fileSize

                val isZip = fileName.endsWith(".zip", ignoreCase = true)
                val isRsc = fileName.endsWith(".rsc", ignoreCase = true) || fileName.endsWith(".txt", ignoreCase = true)

                if (isZip) {
                    isRscFile.value = false
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                    var fileCount = 0
                    var hasHtml = false
                    java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory && !entry.name.contains("__MACOSX")) {
                                fileCount++
                                if (entry.name.endsWith(".html", ignoreCase = true)) hasHtml = true
                            }
                            entry = zis.nextEntry
                        }
                    }
                    if (fileCount > 0) {
                        isPortalValid.value = true
                        selectedPortalFileValidation.value = "✓ Valid Captive Portal Archive ($fileCount files detected${if (hasHtml) ", login.html found" else ""})"
                    } else {
                        isPortalValid.value = false
                        selectedPortalFileValidation.value = "✗ Empty zip archive or no valid portal files found."
                    }
                } else if (isRsc) {
                    isRscFile.value = true
                    val content = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: ""
                    val lineCount = content.lines().filter { it.isNotBlank() }.size
                    selectedRscContent.value = content
                    isPortalValid.value = lineCount > 0
                    selectedPortalFileValidation.value = "✓ Valid RouterOS Script ($lineCount lines ready to execute)"
                } else {
                    isRscFile.value = false
                    isPortalValid.value = false
                    selectedPortalFileValidation.value = "✗ Unsupported file. Please choose a .zip portal or .rsc script."
                }
            } catch (e: Exception) {
                isPortalValid.value = false
                selectedPortalFileValidation.value = "✗ Error inspecting file: ${e.localizedMessage}"
            }
        }
    }

    fun clearSelectedPortalFile() {
        selectedPortalFileUri.value = null
        selectedPortalFileName.value = null
        selectedPortalFileSize.value = 0L
        selectedPortalFileValidation.value = null
        isPortalValid.value = false
        isRscFile.value = false
        selectedRscContent.value = null
    }

    fun uploadSelectedPortal(context: Context) {
        val uri = selectedPortalFileUri.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            isUploadingPortal.value = true
            isExecutingScript.value = true
            val log = StringBuilder()
            log.appendLine("➜ Starting captive portal upload to router storage...")
            scriptExecutionOutput.value = log.toString()
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                val res = repository.uploadPortalZip(bytes) { fileName: String, cur: Int, total: Int ->
                    log.appendLine("  [$cur/$total] Uploading $fileName...")
                    scriptExecutionOutput.value = log.toString()
                }
                if (res.isSuccess) {
                    val count: Int = res.getOrThrow()
                    log.appendLine("✓ Successfully uploaded $count portal files to router /hotspot directory!")
                    log.appendLine("➜ Captive portal files are now active on MikroTik!")
                    userMessage.value = "✓ Captive Portal uploaded successfully ($count files)!"
                } else {
                    log.appendLine("✗ Upload error: ${res.exceptionOrNull()?.message}")
                    userMessage.value = "✗ Error uploading portal: ${res.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                log.appendLine("✗ Upload failed: ${e.localizedMessage}")
                userMessage.value = "✗ Upload failed: ${e.localizedMessage}"
            } finally {
                scriptExecutionOutput.value = log.toString()
                isUploadingPortal.value = false
                isExecutingScript.value = false
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
    private var pollCycleCount = 0

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

    fun executeRscScript(scriptContent: String, onFinished: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            isExecutingScript.value = true
            try {
                userMessage.value = "Executing script on router..."
                val res = repository.executeRscScript(scriptContent)
                scriptExecutionOutput.value = res.outputLog
                if (res.success) {
                    userMessage.value = "✓ Script executed successfully! (${res.executionTimeMs}ms)"
                    onFinished?.invoke(true, res.outputLog)
                } else {
                    userMessage.value = "✗ Script execution failed"
                    onFinished?.invoke(false, res.outputLog)
                }
                fetchRouterData()
            } catch (t: Throwable) {
                val err = "Error executing script: ${t.message}"
                scriptExecutionOutput.value = err
                userMessage.value = "✗ $err"
                onFinished?.invoke(false, err)
            } finally {
                isExecutingScript.value = false
            }
        }
    }

    fun executeSingleCommand(commandLine: String, onFinished: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            isExecutingScript.value = true
            try {
                val res = repository.executeSingleCommand(commandLine)
                if (res.isSuccess) {
                    val out = res.getOrDefault("OK")
                    scriptExecutionOutput.value = out
                    userMessage.value = "✓ Command executed successfully"
                    onFinished?.invoke(true, out)
                } else {
                    val err = "Failed: ${res.exceptionOrNull()?.message}"
                    scriptExecutionOutput.value = err
                    userMessage.value = "✗ $err"
                    onFinished?.invoke(false, err)
                }
                fetchRouterData()
            } catch (t: Throwable) {
                val err = "Error: ${t.message}"
                scriptExecutionOutput.value = err
                userMessage.value = "✗ $err"
                onFinished?.invoke(false, err)
            } finally {
                isExecutingScript.value = false
            }
        }
    }

    fun fetchAndRunRemoteScript(url: String, onFinished: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            isExecutingScript.value = true
            try {
                userMessage.value = "Fetching and executing script from GitHub..."
                val res = repository.fetchAndRunRemoteScript(url)
                scriptExecutionOutput.value = res.outputLog
                if (res.success) {
                    userMessage.value = "✓ Remote script executed successfully from GitHub!"
                    onFinished?.invoke(true, res.outputLog)
                } else {
                    userMessage.value = "✗ Remote script execution failed"
                    onFinished?.invoke(false, res.outputLog)
                }
                fetchRouterData()
            } catch (t: Throwable) {
                val err = "Error running remote script: ${t.message}"
                scriptExecutionOutput.value = err
                userMessage.value = "✗ $err"
                onFinished?.invoke(false, err)
            } finally {
                isExecutingScript.value = false
            }
        }
    }

    fun runUniversalRouterSetup(
        ssid: String,
        adminPassword: String = "Khant1234@",
        capacity: Int = 250,
        wireguardScript: String? = null,
        context: Context? = null,
        onFinished: ((Boolean, String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            var finalWgScript = wireguardScript
            if (finalWgScript.isNullOrBlank() && context != null) {
                try {
                    val routersRes = com.example.data.remote.CloudApiClient.getCustomerRouters(context)
                    if (routersRes.isSuccess) {
                        val routers = routersRes.getOrNull().orEmpty()
                        if (routers.isNotEmpty()) {
                            val rId = routers.first().id
                            val scriptRes = com.example.data.remote.CloudApiClient.getRouterScript(context, rId)
                            if (scriptRes.isSuccess) {
                                finalWgScript = scriptRes.getOrNull()
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            val script = com.example.utils.UniversalSetupHelper.generateScript(ssid, adminPassword, capacity, finalWgScript)
            userMessage.value = "Starting setup for SSID '$ssid' (Direct IP Gateway, no DNS name required)..."
            executeRscScript(script) { success, log ->
                if (success) {
                    userMessage.value = "✓ Router setup completed successfully for SSID '$ssid'!"
                    fetchRouterData()
                } else {
                    userMessage.value = "✗ Setup finished with errors. See log."
                }
                onFinished?.invoke(success, log)
            }
        }
    }

    fun runUpdateRouterScript(onFinished: ((Boolean, String) -> Unit)? = null) {
        val info = appUpdateInfo.value ?: return
        val content = info.routerScriptContent
        val url = info.routerScriptUrl
        when {
            !content.isNullOrBlank() -> executeRscScript(content, onFinished)
            !url.isNullOrBlank() -> fetchAndRunRemoteScript(url, onFinished)
            else -> userMessage.value = "No router script found in this update."
        }
    }

    fun clearScriptExecutionOutput() {
        scriptExecutionOutput.value = null
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

                // Update IP bindings for Whitelist & Banned tabs
                try {
                    val bindings = repository.getIpBindings()
                    ipBindings.value = bindings
                } catch (_: Exception) {}

                // Periodic AP & Topology auto-sync without needing to open the tab (every 3 cycles ~36s or initial load)
                pollCycleCount++
                if (pollCycleCount % 3 == 0 || networkTopology.value.accessPoints.isEmpty()) {
                    try {
                        val topo = repository.getNetworkTopology()
                        networkTopology.value = topo
                    } catch (_: Exception) {}
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
            repository.syncVouchersFromRouter()
            userMessage.value = "✓ Profiles synchronized with router successfully!"
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
            } else {
                userMessage.value = "✗ Router Error: ${res.exceptionOrNull()?.message ?: "Failed to delete profile"}"
            }
        }
    }

    fun updateProfile(oldName: String, profile: UserProfile) {
        viewModelScope.launch(exceptionHandler) {
            val res = repository.updateProfile(oldName, profile)
            if (res.isSuccess) {
                userMessage.value = "✓ Profile '${profile.name}' updated on router!"
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

    fun releaseVoucherFromDevice(username: String, macAddress: String) {
        viewModelScope.launch {
            val res = repository.releaseVoucherFromDevice(username, macAddress)
            if (res.isSuccess) {
                userMessage.value = "✓ Released device from voucher $username! Voucher is now unassigned."
                fetchRouterData()
                syncVouchers()
            } else {
                userMessage.value = "✗ Failed to release device from voucher: ${res.exceptionOrNull()?.message}"
            }
        }
    }

    fun fetchNetworkTopology() {
        viewModelScope.launch {
            isTopologyLoading.value = true
            try {
                val data = repository.getNetworkTopology()
                networkTopology.value = data
            } catch (t: Throwable) {
                t.printStackTrace()
            } finally {
                isTopologyLoading.value = false
            }
        }
    }

    fun fetchIpBindings() {
        viewModelScope.launch {
            try {
                val list = repository.getIpBindings()
                ipBindings.value = list
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    fun whitelistDevice(mac: String, ip: String = "", comment: String = "Whitelisted Device") {
        viewModelScope.launch {
            val ok = repository.whitelistDevice(mac, ip, comment)
            if (ok) {
                userMessage.value = "✓ Device $mac whitelisted!"
                fetchIpBindings()
                fetchNetworkTopology()
                fetchRouterData()
            } else {
                userMessage.value = "✗ Failed to whitelist device"
            }
        }
    }

    fun renameAccessPoint(mac: String, newName: String) {
        val cleanMac = mac.trim().uppercase()
        val cleanName = newName.trim()
        if (cleanMac.isBlank() || cleanName.isBlank()) return

        // 1. INSTANT OPTIMISTIC UI UPDATE (0 ms latency - immediate Compose recomposition)
        val currentTopo = networkTopology.value
        val updatedAps = currentTopo.accessPoints.map { ap ->
            if (ap.macAddress.equals(cleanMac, ignoreCase = true)) {
                ap.copy(name = cleanName)
            } else ap
        }
        networkTopology.value = currentTopo.copy(accessPoints = updatedAps)

        val currentBindings = ipBindings.value
        val updatedBindings = currentBindings.map { b ->
            if (b.macAddress.equals(cleanMac, ignoreCase = true)) {
                b.copy(comment = "AP: $cleanName")
            } else b
        }
        ipBindings.value = updatedBindings

        // 2. Persist to MikroTik router asynchronously
        viewModelScope.launch {
            val ok = repository.renameAccessPoint(cleanMac, cleanName)
            if (ok) {
                userMessage.value = "✓ Renamed AP to $cleanName!"
                fetchNetworkTopology()
                fetchIpBindings()
            } else {
                userMessage.value = "✗ Failed to rename AP"
                fetchNetworkTopology()
            }
        }
    }

    fun removeIpBinding(id: String, mac: String = "") {
        viewModelScope.launch {
            val ok = repository.removeIpBinding(id, mac)
            if (ok) {
                userMessage.value = "✓ Removed device from whitelist/bindings!"
                fetchIpBindings()
                fetchNetworkTopology()
                fetchRouterData()
            } else {
                userMessage.value = "✗ Failed to remove binding"
            }
        }
    }

    fun unbanMac(mac: String, id: String = "") {
        viewModelScope.launch {
            if (id.isNotBlank()) {
                repository.removeIpBinding(id, mac)
            }
            val res = repository.unbanMac(mac)
            if (res.isSuccess) {
                userMessage.value = "✓ Unbanned MAC $mac!"
            } else {
                userMessage.value = "✓ Unbanned MAC $mac"
            }
            fetchIpBindings()
            fetchRouterData()
        }
    }

    fun setRouterAdvanceMode() {
        viewModelScope.launch {
            val ok = repository.setRouterAdvanceMode()
            if (ok) {
                userMessage.value = "✓ Router upgraded to Advance Mode!"
            } else {
                userMessage.value = "✗ Failed to switch router to Advance Mode"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}

