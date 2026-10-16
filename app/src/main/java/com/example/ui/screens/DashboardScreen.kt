package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.ui.components.GlassCard
import com.example.utils.AppLanguage
import com.example.utils.LanguageManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: MainViewModel, navController: NavController) {
    val stats by viewModel.routerStats.collectAsStateWithLifecycle()
    val activeUsers by viewModel.activeUsers.collectAsStateWithLifecycle()
    val vouchers by viewModel.vouchers.collectAsStateWithLifecycle()
    val topology by viewModel.networkTopology.collectAsStateWithLifecycle()
    val rxSpeed by viewModel.rxSpeedMbps.collectAsStateWithLifecycle()
    val txSpeed by viewModel.txSpeedMbps.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedDateFilter.collectAsStateWithLifecycle()
    val filteredSessions by viewModel.filteredSessions.collectAsStateWithLifecycle()
    val activatedSales by viewModel.activatedVoucherSales.collectAsStateWithLifecycle()
    val voucherDataRawMb by viewModel.dateFilteredVoucherDataMb.collectAsStateWithLifecycle()
    val voucherCount by viewModel.dateFilteredVoucherCount.collectAsStateWithLifecycle()
    val profileUsageBreakdown by viewModel.profileUsageBreakdown.collectAsStateWithLifecycle()
    var showProfileBreakdown by remember { mutableStateOf(false) }
    val hwBytes by viewModel.routerHardwareTotalBytes.collectAsStateWithLifecycle()
    val cpuHistory by viewModel.cpuLoadHistory.collectAsStateWithLifecycle()

    val userMsg by viewModel.userMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val currentLang by LanguageManager.currentLanguage
    val strings = LanguageManager.strings

    val appUpdateInfo by viewModel.appUpdateInfo.collectAsStateWithLifecycle()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsStateWithLifecycle()
    val isDownloadingUpdate by viewModel.isDownloadingUpdate.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val isExecutingScript by viewModel.isExecutingScript.collectAsStateWithLifecycle()
    val scriptExecutionOutput by viewModel.scriptExecutionOutput.collectAsStateWithLifecycle()
    val showUpdateDialog by viewModel.showUpdateDialog.collectAsStateWithLifecycle()
    val activeRouterName by viewModel.activeRouterName.collectAsStateWithLifecycle()
    val cloudUser by viewModel.cloudUser.collectAsStateWithLifecycle()
    val connMode by viewModel.connectionMode.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var showSettingsDialog by remember { mutableStateOf(false) }
    val lowStockProfiles by viewModel.lowStockProfiles.collectAsStateWithLifecycle()
    var selectedProfileForModal by remember { mutableStateOf<String?>(null) }
    var showCustomerLookupDialog by remember { mutableStateOf(false) }
    var dispensedVoucher by remember { mutableStateOf<com.example.domain.models.Voucher?>(null) }

    LaunchedEffect(userMsg) {
        userMsg?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.fetchRouterData()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color(0xFF4CAF50), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            val routerDisplayName = if (activeRouterName.isNotBlank()) activeRouterName else (stats?.boardName ?: strings.appName)
                            Text(
                                text = routerDisplayName,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subtitleText = when {
                                cloudUser != null -> "${cloudUser?.fullName} • ${if (connMode == "cloud_remote") "☁️ Remote Relay" else "📶 Local Wi-Fi"}"
                                stats != null -> "RouterOS v${stats?.version}"
                                else -> ""
                            }
                            if (subtitleText.isNotBlank()) {
                                Text(
                                    text = subtitleText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (cloudUser != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    FilledTonalButton(
                        onClick = { LanguageManager.toggleLanguage(context) },
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(
                            text = if (currentLang == AppLanguage.MYANMAR) "🇲🇲 မြန်မာ" else "🇬🇧 EN",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = { showSettingsDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = strings.settings,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = {
                            if (appUpdateInfo?.isNewer == true) {
                                viewModel.openUpdateDialog()
                            } else {
                                viewModel.checkForAppUpdate(manual = true)
                            }
                        }
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (appUpdateInfo?.isNewer == true) {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.error
                                    ) {
                                        Text("NEW", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = strings.updateAvailable,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = strings.checkUpdate
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Live Real-Time Network Speed Banner
            Text(
                text = strings.realTimeTraffic,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Download Speed Card
                GlassCard(
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "Download",
                                tint = Color(0xFF2196F3),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                strings.downloadRx,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "$rxSpeed Mbps",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF1976D2),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Upload Speed Card
                GlassCard(
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Upload",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                strings.uploadTx,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "$txSpeed Mbps",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF388E3C),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Router Hardware Health Section
            Text(
                text = strings.hardwareConditions,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CpuStatCard(
                    modifier = Modifier.weight(1f),
                    title = strings.cpuLoad,
                    value = "${stats?.cpuLoad ?: "0"}%",
                    icon = Icons.Default.Memory,
                    color = MaterialTheme.colorScheme.primary,
                    history = cpuHistory
                )
                TopologyStatCard(
                    modifier = Modifier.weight(1f),
                    title = strings.networkTopology,
                    apCount = topology.accessPoints.size,
                    uptime = stats?.uptime ?: "00:00",
                    onClick = { navController.navigate("network_topology") }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = strings.freeMemory,
                    value = formatBytes(stats?.freeMemory?.toLongOrNull() ?: 0L),
                    icon = Icons.Default.Storage,
                    color = MaterialTheme.colorScheme.secondary
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = strings.routerOs,
                    value = stats?.version ?: "v7.x",
                    icon = Icons.Default.Router,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Date-Filtered Network Usage & Session Metrics
            var showResetDialog by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showProfileBreakdown = !showProfileBreakdown },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = strings.usageMetrics,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        if (showProfileBreakdown) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                TextButton(
                    onClick = { showResetDialog = true },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        Icons.Default.RestartAlt,
                        contentDescription = strings.resetStats,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        strings.resetStats,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (showResetDialog) {
                AlertDialog(
                    onDismissRequest = { showResetDialog = false },
                    icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    title = { Text(strings.resetStatsTitle) },
                    text = {
                        Text(strings.resetStatsMsg)
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showResetDialog = false
                                viewModel.resetAllStatistics()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(strings.resetAllToZero, color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetDialog = false }) {
                            Text(strings.cancel)
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(6.dp))

            // Date Filter Chips (with up to 30 days)
            val filterOptions = listOf(
                "Today" to strings.dateToday,
                "Yesterday" to strings.dateYesterday,
                "Last 7 Days" to strings.date7Days,
                "Last 30 Days" to strings.date30Days,
                "All" to strings.dateAll
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                filterOptions.forEach { (filterKey, filterLabel) ->
                    FilterChip(
                        selected = selectedFilter == filterKey,
                        onClick = { viewModel.setDateFilter(filterKey) },
                        label = { Text(filterLabel, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Usage Summary Cards for selected date range
            // Calculate accurate router voucher data usage strictly synchronized with actual consumption as sum of all profiles
            val sessionDataRawMb = filteredSessions.sumOf { it.dataUsedMb }
            val totalProfileDataMb = profileUsageBreakdown.sumOf { it.dataUsedMb }
            val totalDataRawMb = if (profileUsageBreakdown.isNotEmpty()) totalProfileDataMb else (if (voucherDataRawMb > 0.0) voucherDataRawMb else sessionDataRawMb)
            val formattedDataUsage = if (totalDataRawMb >= 1024.0) {
                String.format(java.util.Locale.US, "%.2f GB", totalDataRawMb / 1024.0)
            } else {
                String.format(java.util.Locale.US, "%.1f MB", totalDataRawMb)
            }

            val totalActivatedCount = if (profileUsageBreakdown.isNotEmpty()) profileUsageBreakdown.sumOf { it.vouchersSold } else (if (voucherCount > 0) voucherCount else filteredSessions.size)
            val totalActivatedRevenue = if (profileUsageBreakdown.isNotEmpty()) profileUsageBreakdown.sumOf { it.totalRevenue } else activatedSales

            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showProfileBreakdown = !showProfileBreakdown }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.activeSessionsStat,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${activeUsers.size}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50),
                                maxLines = 1
                            )
                        }
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                            Text(
                                text = strings.totalDataUsage,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formattedDataUsage,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text(
                                text = strings.vouchersSoldLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$totalActivatedCount",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))
                    val activeFilterLabel = filterOptions.firstOrNull { it.first == selectedFilter }?.second ?: selectedFilter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${strings.activatedVoucherSales} ($activeFilterLabel):",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${"%,d".format(java.util.Locale.US, totalActivatedRevenue.toLong())} Ks",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }

                    // Profile-by-Profile Sales and Exact Data Consumed Breakdown
                    if (showProfileBreakdown) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strings.profileSalesBreakdown,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "${strings.totalDataUsage} Sync ✓",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF4CAF50),
                                fontSize = 10.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        if (profileUsageBreakdown.isEmpty()) {
                            Text(
                                text = strings.noSalesInPeriod,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                profileUsageBreakdown.forEach { stat ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedProfileForModal = stat.profileName },
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = MaterialTheme.colorScheme.primaryContainer
                                                    ) {
                                                        Text(
                                                            text = stat.profileName,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    if (stat.activeNow > 0) {
                                                        Spacer(Modifier.width(6.dp))
                                                        Surface(
                                                            shape = RoundedCornerShape(10.dp),
                                                            color = Color(0xFF10B981).copy(alpha = 0.15f)
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(6.dp)
                                                                        .background(Color(0xFF10B981), CircleShape)
                                                                )
                                                                Spacer(Modifier.width(4.dp))
                                                                Text(
                                                                    text = "${stat.activeNow} ${strings.activeNowLabel}",
                                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = Color(0xFF047857)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = "${"%,d".format(java.util.Locale.US, stat.totalRevenue.toLong())} Ks",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${strings.vouchersSoldLabel}: ${stat.vouchersSold} / ${stat.totalVouchers}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "${strings.exactDataConsumedLabel}: ${stat.exactGbFormatted.ifBlank { stat.dataUsedFormatted }}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.tertiary
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Tap to view ${stat.vouchersSold} ${strings.vouchersSoldLabel.lowercase()} →",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "▼ ${strings.profileSalesBreakdown}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Low Stock Inventory Alert Banner
            if (lowStockProfiles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFEF3C7),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.lowStockWarning,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E)
                            )
                            val lowStockSummary = lowStockProfiles.joinToString(", ") { "${it.first} (${it.second} left)" }
                            Text(
                                text = lowStockSummary,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = Color(0xFFB45309)
                            )
                        }
                        TextButton(onClick = { navController.navigate("vouchers") }) {
                            Text("Generate", fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Voucher Seller & Customer Lookup Bar
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = strings.quickSellVoucher,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(
                            onClick = { showCustomerLookupDialog = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = strings.customerLookup,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    val topProfiles = remember(vouchers) {
                        vouchers.groupBy { it.profileName.ifBlank { "1000Ks" } }
                            .map { (pName, vList) ->
                                val available = vList.count { !it.isUsed && !it.isPrinted && (it.bytesIn + it.bytesOut == 0L) }
                                pName to available
                            }
                            .sortedByDescending { it.second }
                            .take(4)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        topProfiles.forEach { (pName, stockCount) ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (stockCount > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(enabled = stockCount > 0) {
                                        viewModel.quickDispenseVoucher(pName) { v, msg ->
                                            if (v != null) {
                                                dispensedVoucher = v
                                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(v.code))
                                            }
                                        }
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = pName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "$stockCount left",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = if (stockCount > 0) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Management Navigation
            Text(
                text = strings.hotspotControls,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            DashboardMenuCard(
                title = strings.activeSessionsTitle,
                subtitle = "${activeUsers.size} ${strings.activeSessionsSubtitle}",
                icon = Icons.Default.NetworkWifi,
                badgeCount = activeUsers.size,
                onClick = { navController.navigate("active_sessions") }
            )
            DashboardMenuCard(
                title = strings.vouchersAccountsTitle,
                subtitle = strings.vouchersAccountsSubtitle,
                icon = Icons.Default.ConfirmationNumber,
                badgeCount = vouchers.size,
                onClick = { navController.navigate("vouchers") }
            )
            DashboardMenuCard(
                title = strings.userProfilesTitle,
                subtitle = strings.userProfilesSubtitle,
                icon = Icons.Default.GroupWork,
                onClick = { navController.navigate("profiles") }
            )
            DashboardMenuCard(
                title = strings.networkTopology,
                subtitle = "${topology.accessPoints.size} APs Connected (Ruijie / Reyee)",
                icon = Icons.Default.Hub,
                badgeCount = topology.accessPoints.size,
                onClick = { navController.navigate("network_topology") }
            )
        }

        // GitHub App Update Dialog
        if (showUpdateDialog && appUpdateInfo != null) {
            val update = appUpdateInfo!!
            AlertDialog(
                onDismissRequest = { viewModel.dismissUpdateDialog() },
                icon = {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        if (update.isNewer) strings.updateAvailable else strings.appVersionInfo,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    "${strings.currentVersion}: v${com.example.BuildConfig.VERSION_NAME}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (update.isNewer) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    "${strings.latestVersion}: v${update.versionName}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (update.isNewer) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        Text(
                            update.releaseTitle,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = update.releaseNotes.ifBlank { "Performance updates and bug fixes." },
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (update.hasRouterScript) {
                            Spacer(Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Terminal,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            strings.routerUpdateAvailable,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        if (!update.routerScriptUrl.isNullOrBlank()) "Remote script: ${update.routerScriptUrl}"
                                        else "Embedded RouterOS update script included in release",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = { viewModel.runUpdateRouterScript() },
                                        enabled = !isExecutingScript,
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (isExecutingScript) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Executing on Router...", style = MaterialTheme.typography.labelMedium)
                                        } else {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(strings.runUpdateScriptBtn, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    if (!scriptExecutionOutput.isNullOrBlank()) {
                                        Spacer(Modifier.height(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color.Black.copy(alpha = 0.85f),
                                            modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp)
                                        ) {
                                            Text(
                                                text = scriptExecutionOutput ?: "",
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = Color(0xFF69F0AE),
                                                modifier = Modifier.padding(8.dp).verticalScroll(rememberScrollState())
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (isDownloadingUpdate) {
                            Spacer(Modifier.height(16.dp))
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${strings.downloadingUpdate}: ${(downloadProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                confirmButton = {
                    if (update.isNewer) {
                        Button(
                            onClick = {
                                viewModel.downloadAndInstallUpdate(context)
                            },
                            enabled = !isDownloadingUpdate
                        ) {
                            if (isDownloadingUpdate) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("${strings.downloadingUpdate}...")
                            } else {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.downloadAndInstall)
                            }
                        }
                    } else {
                        Button(onClick = { viewModel.dismissUpdateDialog() }) {
                            Text(strings.close)
                        }
                    }
                },
                dismissButton = {
                    if (update.isNewer) {
                        TextButton(
                            onClick = { viewModel.dismissUpdateDialog() },
                            enabled = !isDownloadingUpdate
                        ) {
                            Text(strings.later)
                        }
                    }
                }
            )
        }

        if (showSettingsDialog) {
            com.example.ui.components.DashboardSettingsDialog(
                viewModel = viewModel,
                strings = strings,
                onDismiss = { showSettingsDialog = false },
                onLogout = {
                    viewModel.disconnectFromRouter(context)
                    showSettingsDialog = false
                    navController.navigate("login") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            )
        }

        selectedProfileForModal?.let { profName ->
            ProfileActivatedVouchersDialog(
                profileName = profName,
                viewModel = viewModel,
                strings = strings,
                onDismiss = { selectedProfileForModal = null }
            )
        }

        if (showCustomerLookupDialog) {
            CustomerQuotaLookupDialog(
                viewModel = viewModel,
                strings = strings,
                onDismiss = { showCustomerLookupDialog = false }
            )
        }

        dispensedVoucher?.let { v ->
            VoucherDispensedDialog(
                voucher = v,
                strings = strings,
                onDismiss = { dispensedVoucher = null }
            )
        }
    }
}

@Composable
fun ProfileActivatedVouchersDialog(
    profileName: String,
    viewModel: MainViewModel,
    strings: com.example.utils.AppStrings,
    onDismiss: () -> Unit
) {
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val activeUsers by viewModel.activeUsers.collectAsStateWithLifecycle()
    val activeCodes = remember(activeUsers) { activeUsers.map { it.user }.toSet() }
    val selectedFilter by viewModel.selectedDateFilter.collectAsStateWithLifecycle()
    val activatedVouchers = remember(profileName, viewModel.vouchers, selectedFilter) {
        viewModel.getActivatedVouchersForProfile(profileName, selectedFilter)
    }
    var searchQuery by remember { mutableStateOf("") }
    val filteredList = remember(activatedVouchers, searchQuery) {
        if (searchQuery.isBlank()) activatedVouchers
        else activatedVouchers.filter {
            it.code.contains(searchQuery.trim(), ignoreCase = true) ||
            it.comment.contains(searchQuery.trim(), ignoreCase = true)
        }
    }

    val totalBytes = remember(activatedVouchers) {
        activatedVouchers.sumOf { it.bytesIn + it.bytesOut }
    }
    val totalGbFormatted = String.format(java.util.Locale.US, "%.2f GB", totalBytes / (1024.0 * 1024.0 * 1024.0))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = profileName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = strings.profileActivatedVouchersTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${activatedVouchers.size} ${strings.vouchersSoldLabel} • ${strings.exactDataConsumedLabel}: $totalGbFormatted",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(strings.enterVoucherCode) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(8.dp))

                if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No activated vouchers found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredList.size) { index ->
                            val v = filteredList[index]
                            val isOnline = activeCodes.contains(v.code) || activeCodes.contains(v.username)
                            val consumedBytes = v.bytesIn + v.bytesOut
                            val consumedStr = if (consumedBytes >= 1024L * 1024L * 1024L) {
                                String.format(java.util.Locale.US, "%.2f GB", consumedBytes / (1024.0 * 1024.0 * 1024.0))
                            } else {
                                String.format(java.util.Locale.US, "%.1f MB", consumedBytes / (1024.0 * 1024.0))
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .background(if (isOnline) Color(0xFF10B981) else MaterialTheme.colorScheme.outline, CircleShape)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = v.code,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (isOnline) {
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = "Online",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF047857)
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                text = "Used: $consumedStr",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            if (v.uptime.isNotBlank() && v.uptime != "0s") {
                                                Text(
                                                    text = "Uptime: ${v.uptime}",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(v.code))
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(strings.close)
            }
        }
    )
}

@Composable
fun CustomerQuotaLookupDialog(
    viewModel: MainViewModel,
    strings: com.example.utils.AppStrings,
    onDismiss: () -> Unit
) {
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var query by remember { mutableStateOf("") }
    var resultVoucher by remember { mutableStateOf<com.example.domain.models.Voucher?>(null) }
    val activeUsers by viewModel.activeUsers.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SupportAgent, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(strings.customerLookup, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            resultVoucher = viewModel.searchVoucherDetails(it)
                        },
                        placeholder = { Text(strings.enterVoucherCode) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(Modifier.width(6.dp))
                    Button(onClick = {
                        resultVoucher = viewModel.searchVoucherDetails(query)
                    }) {
                        Text(strings.checkQuota)
                    }
                }

                Spacer(Modifier.height(12.dp))

                resultVoucher?.let { v ->
                    val isOnline = activeUsers.any { it.user.equals(v.code, ignoreCase = true) || it.user.equals(v.username, ignoreCase = true) }
                    val activeSession = activeUsers.firstOrNull { it.user.equals(v.code, ignoreCase = true) || it.user.equals(v.username, ignoreCase = true) }
                    val totalConsumedBytes = v.bytesIn + v.bytesOut
                    val consumedMb = totalConsumedBytes / (1024.0 * 1024.0)

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(v.code, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isOnline) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = if (isOnline) "🟢 Active Online" else "⚪ Offline",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isOnline) Color(0xFF047857) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(6.dp))
                            Text("Profile: ${v.profileName.ifBlank { "1000Ks" }}", style = MaterialTheme.typography.bodySmall)

                            if (v.dataLimitMb > 0) {
                                val remainingMb = (v.dataLimitMb - consumedMb).coerceAtLeast(0.0)
                                Text("Remaining Quota: ${String.format(java.util.Locale.US, "%.1f MB / %d MB", remainingMb, v.dataLimitMb)}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text("Data Consumed: ${String.format(java.util.Locale.US, "%.1f MB (Unlimited)", consumedMb)}", style = MaterialTheme.typography.bodySmall)
                            }

                            if (v.uptime.isNotBlank()) {
                                Text("Uptime: ${v.uptime}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            activeSession?.let { s ->
                                Spacer(Modifier.height(4.dp))
                                Text("Connected IP: ${s.address}", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp))
                                Text("Client MAC: ${s.macAddress}", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp))
                            }
                        }
                    }
                } ?: run {
                    if (query.isNotBlank()) {
                        Text("No matching voucher found in system.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(strings.close)
            }
        }
    )
}

@Composable
fun VoucherDispensedDialog(
    voucher: com.example.domain.models.Voucher,
    strings: com.example.utils.AppStrings,
    onDismiss: () -> Unit
) {
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981))
                Spacer(Modifier.width(8.dp))
                Text(strings.voucherSoldSuccess, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = voucher.profileName.ifBlank { "1000Ks" },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = voucher.code,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Copied to Clipboard ✓",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(strings.close)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(voucher.code))
            }) {
                Text(strings.sellAndCopy)
            }
        }
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes / (1024 * 1024)
    return if (mb > 1024) {
        String.format("%.1f GB", mb / 1024.0)
    } else {
        "$mb MB"
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.padding(11.dp)) {
            Icon(imageVector = icon, contentDescription = title, tint = color, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun TopologyStatCard(
    modifier: Modifier = Modifier,
    title: String,
    apCount: Int,
    uptime: String,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = modifier.clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(11.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Hub,
                    contentDescription = title,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(20.dp)
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "$apCount APs",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(uptime, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun CpuStatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    history: List<Float>
) {
    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.padding(11.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = color, modifier = Modifier.size(20.dp))
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(color, CircleShape)
                )
            }
            Spacer(modifier = Modifier.height(5.dp))
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)

            Spacer(modifier = Modifier.height(6.dp))

            // Real-time sparkline graph decoration
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
            ) {
                val strokeWidth = 2.dp.toPx()
                val points = if (history.size < 2) listOf(10f, 10f) else history
                val maxVal = maxOf(100f, points.maxOrNull() ?: 100f)
                val minVal = 0f
                val range = (maxVal - minVal).coerceAtLeast(1f)

                val w = size.width
                val h = size.height
                val stepX = w / (points.size - 1).coerceAtLeast(1)

                val path = androidx.compose.ui.graphics.Path()
                val fillPath = androidx.compose.ui.graphics.Path()

                points.forEachIndexed { i, pt ->
                    val x = i * stepX
                    val y = h - ((pt - minVal) / range * (h - strokeWidth)) - strokeWidth / 2
                    if (i == 0) {
                        path.moveTo(x, y)
                        fillPath.moveTo(x, h)
                        fillPath.lineTo(x, y)
                    } else {
                        path.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                fillPath.lineTo(w, h)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(color.copy(alpha = 0.25f), Color.Transparent)
                    )
                )

                drawPath(
                    path = path,
                    color = color,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = strokeWidth,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                    )
                )

                val lastX = (points.size - 1) * stepX
                val lastY = h - (((points.lastOrNull() ?: 0f) - minVal) / range * (h - strokeWidth)) - strokeWidth / 2
                drawCircle(
                    color = color,
                    radius = 2.5.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(lastX, lastY)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardMenuCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badgeCount: Int? = null,
    onClick: () -> Unit
) {
    GlassCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = title, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (badgeCount != null && badgeCount > 0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(
                        text = "$badgeCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Open", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

