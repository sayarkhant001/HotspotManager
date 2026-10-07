package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.example.utils.DeviceModelDetector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.domain.models.AccessPointDevice
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.example.domain.models.ActiveUser
import com.example.domain.models.IpBinding
import com.example.domain.models.RouterSessionLog
import com.example.domain.models.NetworkTopologyData
import com.example.ui.components.GlassCard
import com.example.utils.LanguageManager

private fun formatSpeed(bps: Long): String {
    return when {
        bps >= 1_000_000_000L -> String.format(Locale.US, "%.1f Gbps", bps / 1_000_000_000.0)
        bps >= 1_000_000L -> String.format(Locale.US, "%.1f Mbps", bps / 1_000_000.0)
        bps >= 1_000L -> String.format(Locale.US, "%.0f kbps", bps / 1_000.0)
        bps > 0L -> "$bps bps"
        else -> "0 kbps"
    }
}

private fun formatDataBytes(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    val mb = bytes / (1024.0 * 1024.0)
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        bytes > 0L -> "${bytes / 1024} KB"
        else -> "0 MB"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkTopologyScreen(
    viewModel: MainViewModel,
    navController: NavController
) {
    val topology by viewModel.networkTopology.collectAsStateWithLifecycle()
    val isLoading by viewModel.isTopologyLoading.collectAsStateWithLifecycle()
    val activeUsers by viewModel.activeUsers.collectAsStateWithLifecycle()
    val allSessions by viewModel.allSessions.collectAsStateWithLifecycle()
    val ipBindings by viewModel.ipBindings.collectAsStateWithLifecycle()
    val strings = LanguageManager.strings

    var apToAllowlist by remember { mutableStateOf<AccessPointDevice?>(null) }
    var apToUndo by remember { mutableStateOf<AccessPointDevice?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedApForDetails by remember { mutableStateOf<AccessPointDevice?>(null) }
    var apToRename by remember { mutableStateOf<AccessPointDevice?>(null) }
    var isListView by remember { mutableStateOf(false) }
    var isCompactMode by remember { mutableStateOf(true) }
    var expandedApMacs by remember { mutableStateOf(setOf<String>()) }

    fun toggleApExpansion(mac: String) {
        val upper = mac.uppercase()
        expandedApMacs = if (expandedApMacs.contains(upper)) {
            expandedApMacs - upper
        } else {
            expandedApMacs + upper
        }
    }

    val todayKey = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Yangon")
        }.format(Date())
    }
    val todaySessions = remember(allSessions, todayKey) {
        allSessions.filter { it.dateKey == todayKey }
    }

    // Zoom state
    var scale by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(Unit) {
        viewModel.fetchNetworkTopology()
        viewModel.fetchIpBindings()
        viewModel.fetchRouterData()
        while (isActive) {
            delay(25000L)
            viewModel.fetchNetworkTopology()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = strings.networkTopology,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.back
                        )
                    }
                },
                actions = {
                    // Density toggle: Small (Fit Screen / Unscrollable) vs Expanded
                    IconButton(onClick = { isCompactMode = !isCompactMode }) {
                        Icon(
                            imageVector = if (isCompactMode) Icons.Default.UnfoldMore else Icons.Default.FitScreen,
                            contentDescription = if (isCompactMode) "Expand Mode" else "Small Mode",
                            tint = if (isCompactMode) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                        )
                    }
                    // Layout toggle: Tree vs Cards
                    IconButton(onClick = { isListView = !isListView }) {
                        Icon(
                            imageVector = if (isListView) Icons.Default.AccountTree else Icons.Default.ViewAgenda,
                            contentDescription = "Toggle View",
                            tint = Color(0xFF10B981)
                        )
                    }
                    // Add Device
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.AddCircleOutline,
                            contentDescription = strings.addDevice,
                            tint = Color(0xFF10B981)
                        )
                    }
                    // Refresh
                    IconButton(onClick = {
                        viewModel.fetchNetworkTopology()
                        viewModel.fetchIpBindings()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = strings.refresh
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val transformState = rememberTransformableState { zoomChange, _, _ ->
                scale = (scale * zoomChange).coerceIn(0.6f, 2.0f)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .transformable(state = transformState)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isCompactMode) {
                    // COMPACT MODE: Unified Sleek Gateway & Internet Bar (~48dp)
                    CompactGatewayNode(
                        topology = topology,
                        strings = strings
                    )
                } else {
                    // EXPANDED MODE: Full Large Internet Cloud + Large Gateway Card
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(bottom = 2.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFE0F2FE),
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Cloud,
                                    contentDescription = "Internet",
                                    tint = Color(0xFF0284C7),
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Internet",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    ConnectingVerticalLine(dots = 3, color = Color(0xFF10B981))

                    ExpandedGatewayNode(
                        topology = topology,
                        strings = strings
                    )
                }

                // Connected Access Points Section Header & View Switcher
                val aps = topology.accessPoints

                if (aps.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))

                    // Toolbar Strip (Compact & Unscrollable)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${aps.size} Devices Detected",
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Small (Fit Screen) vs Expand Toggle Pill
                                Surface(
                                    onClick = { isCompactMode = !isCompactMode },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isCompactMode) Color(0xFF10B981).copy(alpha = 0.15f) else Color.Transparent
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isCompactMode) Icons.Default.FitScreen else Icons.Default.UnfoldMore,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp),
                                            tint = if (isCompactMode) Color(0xFF059669) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.width(2.dp))
                                        Text(
                                            if (isCompactMode) "Small" else "Expand",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            fontWeight = FontWeight.Bold,
                                            color = if (isCompactMode) Color(0xFF059669) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Tree / Cards Layout Toggle Pill
                                Surface(
                                    onClick = { isListView = !isListView },
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.15f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isListView) Icons.Default.ViewAgenda else Icons.Default.AccountTree,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp),
                                            tint = Color(0xFF059669)
                                        )
                                        Spacer(Modifier.width(2.dp))
                                        Text(
                                            if (isListView) "Cards" else "Tree",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF059669)
                                        )
                                    }
                                }

                                // Add Device Button
                                Surface(
                                    onClick = { showAddDialog = true },
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF10B981)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp),
                                            tint = Color.White
                                        )
                                        Spacer(Modifier.width(2.dp))
                                        Text(
                                            "Add",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (!isListView) {
                        // VISUAL TREE VIEW (Unscrollable & Smallable)
                        BranchingLines(apCount = aps.size, isCompact = isCompactMode)

                        val chunkedAps = aps.chunked(2)
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(if (isCompactMode) 6.dp else 12.dp)
                        ) {
                            chunkedAps.forEachIndexed { rowIndex, rowAps ->
                                if (rowIndex > 0) {
                                    ConnectingVerticalLine(dots = 2, color = Color(0xFF10B981))
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    rowAps.forEach { ap ->
                                        val clientCount = remember(activeUsers, ap, topology) {
                                            if (!ap.isOnline) 0 else {
                                                val onlineAps = topology.accessPoints.filter { it.isOnline }
                                                if (onlineAps.size <= 1) {
                                                    activeUsers.size
                                                } else {
                                                    val apNameNorm = ap.name.replace(" ", "").lowercase()
                                                    val directMatches = activeUsers.filter { u ->
                                                        val uNorm = u.user.lowercase()
                                                        val cNorm = u.comment.lowercase()
                                                        (apNameNorm.length >= 3 && (uNorm.contains(apNameNorm) || cNorm.contains(apNameNorm))) ||
                                                        (ap.connectedClientMacs.isNotEmpty() && u.macAddress.uppercase() in ap.connectedClientMacs.map { it.uppercase() })
                                                    }
                                                    val unassigned = activeUsers.filter { u ->
                                                        onlineAps.none { other ->
                                                            val oNorm = other.name.replace(" ", "").lowercase()
                                                            (oNorm.length >= 3 && (u.user.lowercase().contains(oNorm) || u.comment.lowercase().contains(oNorm))) ||
                                                            (other.connectedClientMacs.isNotEmpty() && u.macAddress.uppercase() in other.connectedClientMacs.map { it.uppercase() })
                                                        }
                                                    }
                                                    val apIdx = onlineAps.indexOfFirst { it.macAddress == ap.macAddress }
                                                    val partitioned = unassigned.filter { u ->
                                                        val h = Math.abs(u.macAddress.ifBlank { u.user }.hashCode())
                                                        (h % onlineAps.size) == apIdx
                                                    }
                                                    directMatches.size + partitioned.size
                                                }
                                            }
                                        }
                                        val isExpanded = expandedApMacs.contains(ap.macAddress.uppercase())
                                        val todayBytes = ap.dailyBytesIn + ap.dailyBytesOut

                                        Box(
                                            modifier = Modifier.weight(1f),
                                            contentAlignment = Alignment.TopCenter
                                        ) {
                                            ApDeviceNode(
                                                ap = ap,
                                                clientCount = clientCount,
                                                isCompact = isCompactMode,
                                                isExpanded = isExpanded,
                                                onToggleExpand = { toggleApExpansion(ap.macAddress) },
                                                todayBytes = todayBytes,
                                                strings = strings,
                                                onClick = { selectedApForDetails = ap },
                                                onAllowlist = { apToAllowlist = ap },
                                                onUndo = { apToUndo = ap },
                                                onRename = { apToRename = ap }
                                            )
                                        }
                                    }
                                    if (rowAps.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    } else {
                        // DETAILED / COMPACT CARD LIST VIEW
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(if (isCompactMode) 6.dp else 10.dp)
                        ) {
                            aps.forEach { ap ->
                                val clientCount = remember(activeUsers, ap, topology) {
                                    if (!ap.isOnline) 0 else {
                                        val onlineAps = topology.accessPoints.filter { it.isOnline }
                                        if (onlineAps.size <= 1) {
                                            activeUsers.size
                                        } else {
                                            val apNameNorm = ap.name.replace(" ", "").lowercase()
                                            val directMatches = activeUsers.filter { u ->
                                                val uNorm = u.user.lowercase()
                                                val cNorm = u.comment.lowercase()
                                                (apNameNorm.length >= 3 && (uNorm.contains(apNameNorm) || cNorm.contains(apNameNorm))) ||
                                                (ap.connectedClientMacs.isNotEmpty() && u.macAddress.uppercase() in ap.connectedClientMacs.map { it.uppercase() })
                                            }
                                            val unassigned = activeUsers.filter { u ->
                                                onlineAps.none { other ->
                                                    val oNorm = other.name.replace(" ", "").lowercase()
                                                    (oNorm.length >= 3 && (u.user.lowercase().contains(oNorm) || u.comment.lowercase().contains(oNorm))) ||
                                                    (other.connectedClientMacs.isNotEmpty() && u.macAddress.uppercase() in other.connectedClientMacs.map { it.uppercase() })
                                                }
                                            }
                                            val apIdx = onlineAps.indexOfFirst { it.macAddress == ap.macAddress }
                                            val partitioned = unassigned.filter { u ->
                                                val h = Math.abs(u.macAddress.ifBlank { u.user }.hashCode())
                                                (h % onlineAps.size) == apIdx
                                            }
                                            directMatches.size + partitioned.size
                                        }
                                    }
                                }
                                val isExpanded = expandedApMacs.contains(ap.macAddress.uppercase())
                                val todayBytes = ap.dailyBytesIn + ap.dailyBytesOut

                                ApListCard(
                                    ap = ap,
                                    clientCount = clientCount,
                                    isCompact = isCompactMode,
                                    isExpanded = isExpanded,
                                    onToggleExpand = { toggleApExpansion(ap.macAddress) },
                                    todayBytes = todayBytes,
                                    strings = strings,
                                    onClick = { selectedApForDetails = ap },
                                    onAllowlist = { apToAllowlist = ap },
                                    onUndo = { apToUndo = ap },
                                    onRename = { apToRename = ap }
                                )
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(20.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiTetheringOff,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No APs Detected Automatically",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Connect Ruijie APs (EST310, EST350, EW series) to Hotspot bridge or tap 'Add' above to manually allowlist an AP.",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            if (isLoading && topology.accessPoints.isEmpty() && topology.routerModel.isBlank()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .align(Alignment.TopCenter),
                    color = Color(0xFF10B981)
                )
            }
        }

        // Allowlist AP Confirmation Dialog
        if (apToAllowlist != null) {
            val ap = apToAllowlist!!
            AlertDialog(
                onDismissRequest = { apToAllowlist = null },
                icon = {
                    Icon(
                        Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text(
                        text = strings.allowlistAp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Text(strings.allowlistApConfirm)
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Device: ${ap.name}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "IP: ${ap.ipAddress}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "MAC: ${ap.macAddress}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.whitelistDevice(
                                mac = ap.macAddress,
                                ip = ap.ipAddress,
                                comment = "Ruijie AP: ${ap.name}"
                            )
                            apToAllowlist = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text(strings.whitelistAction, color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { apToAllowlist = null }) {
                        Text(strings.cancel)
                    }
                }
            )
        }

        // Undo Allowlist Dialog
        if (apToUndo != null) {
            val ap = apToUndo!!
            AlertDialog(
                onDismissRequest = { apToUndo = null },
                title = { Text(strings.undoAction) },
                text = {
                    Text("Remove allowlist bypass for \"${ap.name}\" (${ap.macAddress})?")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.removeIpBinding(
                                id = ap.bindingId ?: "",
                                mac = ap.macAddress
                            )
                            apToUndo = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(strings.undoAction)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { apToUndo = null }) {
                        Text(strings.cancel)
                    }
                }
            )
        }

        // Rename AP Dialog
        if (apToRename != null) {
            val ap = apToRename!!
            var renameText by remember(ap) { mutableStateOf(ap.name) }
            AlertDialog(
                onDismissRequest = { apToRename = null },
                title = { Text("Rename Access Point", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            text = "Set a custom display name for ${ap.model} (${ap.macAddress}).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            label = { Text("AP Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (renameText.isNotBlank()) {
                                viewModel.renameAccessPoint(ap.macAddress, renameText.trim())
                            }
                            apToRename = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { apToRename = null }) {
                        Text(strings.cancel)
                    }
                }
            )
        }

        // Add Device Dialog
        if (showAddDialog) {
            AddDeviceDialog(
                activeUsers = activeUsers,
                onDismiss = { showAddDialog = false },
                onAdd = { name, ip, mac ->
                    viewModel.whitelistDevice(
                        mac = mac,
                        ip = ip,
                        comment = "AP: $name"
                    )
                    showAddDialog = false
                }
            )
        }

        // AP Details Bottom Sheet (Real-time speed, today's data, client list with kick/ban/whitelist)
        if (selectedApForDetails != null) {
            ApDetailsBottomSheet(
                ap = selectedApForDetails!!,
                activeUsers = activeUsers,
                todaySessions = todaySessions,
                topology = topology,
                ipBindings = ipBindings,
                strings = strings,
                onDismiss = { selectedApForDetails = null },
                onAllowlistAp = {
                    val ap = selectedApForDetails!!
                    viewModel.whitelistDevice(ap.macAddress, ap.ipAddress, "AP: ${ap.name}")
                    selectedApForDetails = null
                },
                onUndoApAllowlist = {
                    val ap = selectedApForDetails!!
                    ap.bindingId?.let { id -> viewModel.removeIpBinding(id, ap.macAddress) }
                    selectedApForDetails = null
                },
                onKickUser = { sessionId -> viewModel.kickUser(sessionId) },
                onBanUser = { mac -> viewModel.banMac(mac) },
                onUnbanUser = { mac, bindingId -> viewModel.unbanMac(mac, bindingId) },
                onWhitelistClient = { mac, ip, comment -> viewModel.whitelistDevice(mac, ip, comment) },
                onUndoClientWhitelist = { bindingId, mac -> viewModel.removeIpBinding(bindingId, mac) }
            )
        }
    }
}

@Composable
fun CompactGatewayNode(
    topology: NetworkTopologyData,
    strings: com.example.utils.AppStrings
) {
    val routerInfo = remember(topology.routerModel) {
        DeviceModelDetector.detectRouterGateway(topology.routerModel)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Internet mini pill
            Surface(
                shape = CircleShape,
                color = Color(0xFFE0F2FE),
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = "Internet",
                        tint = Color(0xFF0284C7),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(5.dp))

            // Mini green dots
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(2) {
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                }
            }

            Spacer(modifier = Modifier.width(5.dp))

            // Router thumbnail
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.White,
                shadowElevation = 1.dp,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(2.dp)) {
                    Image(
                        painter = painterResource(id = routerInfo.imageResId),
                        contentDescription = topology.routerModel,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Router name & IPs
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = topology.routerModel,
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.sp),
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF0284C7).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = strings.gateway,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0284C7),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                Text(
                    text = "Manage: ${topology.manageIp} • Hotspot: ${topology.hotspotIp}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ExpandedGatewayNode(
    topology: NetworkTopologyData,
    strings: com.example.utils.AppStrings
) {
    val routerInfo = remember(topology.routerModel) {
        DeviceModelDetector.detectRouterGateway(topology.routerModel)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.padding(horizontal = 12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .height(48.dp)
                    .fillMaxWidth(0.55f)
                    .padding(vertical = 2.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
                    Image(
                        painter = painterResource(id = routerInfo.imageResId),
                        contentDescription = topology.routerModel,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = topology.routerModel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = strings.gateway,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0284C7)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Manage IP: ${topology.manageIp}  •  Hotspot IP: ${topology.hotspotIp}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ApDeviceNode(
    ap: AccessPointDevice,
    clientCount: Int = 0,
    isCompact: Boolean = true,
    isExpanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
    todayBytes: Long = 0L,
    strings: com.example.utils.AppStrings,
    onClick: () -> Unit,
    onAllowlist: () -> Unit,
    onUndo: () -> Unit,
    onRename: () -> Unit = {}
) {
    val modelInfo = remember(ap) {
        DeviceModelDetector.detectApOrClient(
            name = ap.name,
            hostName = ap.name,
            comment = ap.model,
            macAddress = ap.macAddress
        )
    }
    val clipboardManager = LocalClipboardManager.current

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (ap.isOnline) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFFEF4444).copy(alpha = 0.8f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggleExpand() }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp)
        ) {
            // Header Row: Photo + Name + Chevron
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Hardware photo with status dot
                Box(contentAlignment = Alignment.TopEnd) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 2.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (ap.isOnline) Color(0xFF10B981) else Color(0xFFEF4444)
                        ),
                        modifier = Modifier.size(if (isCompact) 36.dp else 46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(2.dp)) {
                            if (modelInfo.imageResId != 0) {
                                Image(
                                    painter = painterResource(id = modelInfo.imageResId),
                                    contentDescription = modelInfo.modelName,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = if (ap.isOnline) Color(0xFF10B981) else Color(0xFFEF4444),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (ap.isOnline) Color(0xFF10B981) else Color(0xFFEF4444))
                            .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = ap.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 11.sp),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (ap.isWhitelisted) {
                            Spacer(Modifier.width(3.dp))
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.15f),
                                modifier = Modifier.clickable { onUndo() }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Allowed",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(9.dp)
                                    )
                                    Spacer(modifier = Modifier.width(1.dp))
                                    Text(
                                        text = strings.allowedBadge,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                                        color = Color(0xFF10B981),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(2.dp))
                        IconButton(
                            onClick = onRename,
                            modifier = Modifier.size(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Rename AP",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                    Text(
                        text = modelInfo.modelName,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF059669),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = ap.ipAddress.ifBlank { "DHCP" },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Pills Row: Clients, Speed, Online
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (ap.isOnline) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (ap.isOnline) "🟢 Online" else "🔴 Offline",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        fontWeight = FontWeight.Bold,
                        color = if (ap.isOnline) Color(0xFF059669) else Color(0xFFEF4444),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "👥 $clientCount",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF059669),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
                if (ap.currentRxBps > 0L || ap.currentTxBps > 0L) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF0284C7).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "↓ ${formatSpeed(ap.currentRxBps)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0284C7),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                if (!ap.isWhitelisted) {
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = onAllowlist,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp),
                        modifier = Modifier.height(18.dp)
                    ) {
                        Text(
                            text = "+Allow",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // INLINE EXPANDABLE DETAILS ("in expandable")
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        thickness = 1.dp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // MAC Address row with quick copy
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MAC: ${ap.macAddress}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(
                            onClick = { clipboardManager.setText(AnnotatedString(ap.macAddress)) },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy MAC",
                                modifier = Modifier.size(12.dp),
                                tint = Color(0xFF10B981)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Live Upload & Download speeds
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.1f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(4.dp)) {
                                Text("↓ Download", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = Color(0xFF059669))
                                Text(formatSpeed(ap.currentRxBps), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF0284C7).copy(alpha = 0.1f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(4.dp)) {
                                Text("↑ Upload", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = Color(0xFF0284C7))
                                Text(formatSpeed(ap.currentTxBps), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), fontWeight = FontWeight.Bold, color = Color(0xFF0369A1))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Today's Consumption
                    if (todayBytes > 0L) {
                        Text(
                            text = "Today: ${formatDataBytes(todayBytes)} (reset at 12 AM)",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = onClick,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.weight(1f).height(26.dp)
                        ) {
                            Text(
                                text = "👥 Clients ($clientCount)",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        if (ap.isWhitelisted) {
                            Button(
                                onClick = onUndo,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.weight(1f).height(26.dp)
                            ) {
                                Text(
                                    text = "Undo",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ApListCard(
    ap: AccessPointDevice,
    clientCount: Int,
    isCompact: Boolean = true,
    isExpanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
    todayBytes: Long = 0L,
    strings: com.example.utils.AppStrings,
    onClick: () -> Unit,
    onAllowlist: () -> Unit,
    onUndo: () -> Unit,
    onRename: () -> Unit = {}
) {
    val modelInfo = remember(ap) {
        DeviceModelDetector.detectApOrClient(
            name = ap.name,
            hostName = ap.name,
            comment = ap.model,
            macAddress = ap.macAddress
        )
    }
    val clipboardManager = LocalClipboardManager.current

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (ap.isOnline) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFFEF4444).copy(alpha = 0.8f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggleExpand() }
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail with Online/Allowlist Badge
                Box(contentAlignment = Alignment.TopEnd) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 2.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (ap.isOnline) Color(0xFF10B981) else Color(0xFFEF4444)
                        ),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(2.dp)) {
                            if (modelInfo.imageResId != 0) {
                                Image(
                                    painter = painterResource(id = modelInfo.imageResId),
                                    contentDescription = modelInfo.modelName,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Icon(Icons.Default.Wifi, contentDescription = null, tint = if (ap.isOnline) Color(0xFF10B981) else Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (ap.isOnline) Color(0xFF10B981) else Color(0xFFEF4444))
                            .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    )
                }

                Spacer(Modifier.width(10.dp))

                // Details Column
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = ap.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.sp),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(2.dp))
                        IconButton(
                            onClick = onRename,
                            modifier = Modifier.size(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Rename AP",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(11.dp)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = modelInfo.modelName,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF059669),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${ap.ipAddress.ifBlank { "DHCP" }} • ${ap.macAddress}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(6.dp))

                // Mini Badges
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (ap.isOnline) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (ap.isOnline) "🟢 Online" else "🔴 Offline",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.Bold,
                            color = if (ap.isOnline) Color(0xFF059669) else Color(0xFFEF4444),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "👥 $clientCount",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF059669),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    if (ap.currentRxBps > 0L || ap.currentTxBps > 0L) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF0284C7).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "↓ ${formatSpeed(ap.currentRxBps)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0284C7),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (ap.isWhitelisted) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                            modifier = Modifier.clickable { onUndo() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(10.dp))
                                Spacer(Modifier.width(2.dp))
                                Text(strings.allowedBadge, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                            }
                        }
                    } else {
                        Button(
                            onClick = onAllowlist,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text(strings.allowlistAction, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // INLINE EXPANDABLE SECTION ("in expandable")
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        thickness = 1.dp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.1f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(6.dp)) {
                                Text("↓ Download Speed", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = Color(0xFF059669))
                                Text(formatSpeed(ap.currentRxBps), style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.sp), fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF0284C7).copy(alpha = 0.1f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(6.dp)) {
                                Text("↑ Upload Speed", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = Color(0xFF0284C7))
                                Text(formatSpeed(ap.currentTxBps), style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.sp), fontWeight = FontWeight.Bold, color = Color(0xFF0369A1))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (todayBytes > 0L) "Today: ${formatDataBytes(todayBytes)} (from 12 AM)" else "Live connected device",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = onClick,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text(
                                    text = "👥 Manage Clients",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            if (ap.isWhitelisted) {
                                Button(
                                    onClick = onUndo,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text(
                                        text = "Undo Whitelist",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectingVerticalLine(dots: Int = 3, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        repeat(dots) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
fun BranchingLines(apCount: Int, isCompact: Boolean = false) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isCompact) 14.dp else 32.dp)
            .padding(horizontal = 30.dp)
    ) {
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
        )
        val color = Color(0xFF94A3B8)
        val centerX = size.width / 2f

        // Draw vertical center stem
        drawLine(
            color = color,
            start = Offset(centerX, 0f),
            end = Offset(centerX, size.height * 0.45f),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = stroke.pathEffect
        )

        if (apCount > 1) {
            // Draw horizontal crossbar
            drawLine(
                color = color,
                start = Offset(size.width * 0.2f, size.height * 0.45f),
                end = Offset(size.width * 0.8f, size.height * 0.45f),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = stroke.pathEffect
            )
            // Left branch
            drawLine(
                color = color,
                start = Offset(size.width * 0.2f, size.height * 0.45f),
                end = Offset(size.width * 0.2f, size.height),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = stroke.pathEffect
            )
            // Right branch
            drawLine(
                color = color,
                start = Offset(size.width * 0.8f, size.height * 0.45f),
                end = Offset(size.width * 0.8f, size.height),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = stroke.pathEffect
            )
        } else {
            drawLine(
                color = color,
                start = Offset(centerX, size.height * 0.45f),
                end = Offset(centerX, size.height),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = stroke.pathEffect
            )
        }
    }
}

@Composable
fun AddDeviceDialog(
    activeUsers: List<com.example.domain.models.ActiveUser> = emptyList(),
    onDismiss: () -> Unit,
    onAdd: (name: String, ip: String, mac: String) -> Unit
) {
    val presets = remember { DeviceModelDetector.HARDWARE_PRESETS }
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = listOf("All", "RG-EW Series", "RAP Ceiling", "RAP Wall-Plate", "Outdoor & OD", "AirMetro & EST", "TP-Link")

    val filteredPresets = remember(selectedCategory) {
        when (selectedCategory) {
            "RG-EW Series" -> presets.filter { it.shortName.startsWith("EW") || it.modelName.contains("EW") || it.shortName.startsWith("RG-M") }
            "RAP Ceiling" -> presets.filter { it.deviceType.contains("Ceiling", ignoreCase = true) }
            "RAP Wall-Plate" -> presets.filter { it.deviceType.contains("Wall", ignoreCase = true) || it.shortName.startsWith("RAP12") }
            "Outdoor & OD" -> presets.filter { it.deviceType.contains("Outdoor", ignoreCase = true) || it.shortName.contains("OD") || it.shortName.startsWith("RAP62") || it.shortName.startsWith("RAP52") }
            "AirMetro & EST" -> presets.filter { it.modelName.contains("AirMetro", ignoreCase = true) || it.modelName.contains("EST", ignoreCase = true) }
            "TP-Link" -> presets.filter { it.brand.contains("TP-Link") }
            else -> presets
        }
    }

    var selectedPreset by remember { mutableStateOf(presets.first()) }
    var deviceName by remember { mutableStateOf(presets.first().shortName) }
    var ipAddress by remember { mutableStateOf("192.168.100.") }
    var macAddress by remember { mutableStateOf(presets.first().defaultPrefix) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AddModerator,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add AP / Device to Topology", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Hardware Preview Card of currently selected model
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            shadowElevation = 2.dp,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
                                Image(
                                    painter = painterResource(id = selectedPreset.imageResId),
                                    contentDescription = selectedPreset.modelName,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedPreset.modelName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${selectedPreset.brand} · ${selectedPreset.deviceType}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF059669),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // 2. Category Filter Chips
                Text("Select Hardware Model:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF10B981),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                // 3. Quick Preset Model Chips with Real Thumbnails
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(filteredPresets) { preset ->
                        val isSelected = selectedPreset.id == preset.id
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                if (isSelected) 2.dp else 1.dp,
                                if (isSelected) Color(0xFF10B981) else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .width(115.dp)
                                .clickable {
                                    selectedPreset = preset
                                    deviceName = preset.shortName
                                    if (macAddress.isBlank() || macAddress == "C0:A4:76:" || macAddress == "E8:48:B8:") {
                                        macAddress = preset.defaultPrefix
                                    }
                                }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.White,
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(2.dp)) {
                                        Image(
                                            painter = painterResource(id = preset.imageResId),
                                            contentDescription = preset.shortName,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = preset.shortName,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // 4. Quick Fill from Active Connected Devices (if any)
                if (activeUsers.isNotEmpty()) {
                    Text("Or Auto-fill from Connected Device:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(activeUsers.take(8)) { u ->
                            SuggestionChip(
                                onClick = {
                                    ipAddress = u.address
                                    macAddress = u.macAddress
                                    val detected = DeviceModelDetector.detectApOrClient(u.user, u.hostName, "", u.macAddress)
                                    val matchedPreset = presets.find { it.modelName == detected.modelName }
                                    if (matchedPreset != null) {
                                        selectedPreset = matchedPreset
                                        deviceName = matchedPreset.shortName
                                    } else {
                                        deviceName = u.hostName.ifBlank { u.user }
                                    }
                                },
                                label = {
                                    Text(
                                        text = "${u.hostName.ifBlank { u.user }} (${u.address})",
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }
                }

                // 5. Input Fields
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    label = { Text("Device Name / Model") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = { ipAddress = it },
                    label = { Text("IP Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = macAddress,
                    onValueChange = { macAddress = it },
                    label = { Text("MAC Address (e.g. C0:A4:76:XX:XX:XX)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Vendor OUI Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = { macAddress = "C0:A4:76:" },
                        label = { Text("Ruijie C0:A4:76", fontSize = 10.sp) }
                    )
                    AssistChip(
                        onClick = { macAddress = "10:5F:02:" },
                        label = { Text("Ruijie 10:5F:02", fontSize = 10.sp) }
                    )
                    AssistChip(
                        onClick = { macAddress = "E8:48:B8:" },
                        label = { Text("TP-Link E8:48:B8", fontSize = 10.sp) }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (macAddress.isNotBlank()) {
                        onAdd(deviceName.trim(), ipAddress.trim(), macAddress.trim())
                    }
                },
                enabled = macAddress.isNotBlank() && macAddress.length >= 11,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Allowlist & Add to Topology", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApDetailsBottomSheet(
    ap: AccessPointDevice,
    activeUsers: List<ActiveUser>,
    todaySessions: List<RouterSessionLog>,
    topology: NetworkTopologyData,
    ipBindings: List<IpBinding>,
    strings: com.example.utils.AppStrings,
    onDismiss: () -> Unit,
    onAllowlistAp: () -> Unit,
    onUndoApAllowlist: () -> Unit,
    onKickUser: (sessionId: String) -> Unit,
    onBanUser: (mac: String) -> Unit,
    onUnbanUser: (mac: String, bindingId: String) -> Unit,
    onWhitelistClient: (mac: String, ip: String, comment: String) -> Unit,
    onUndoClientWhitelist: (bindingId: String, mac: String) -> Unit
) {
    val modelInfo = remember(ap) {
        DeviceModelDetector.detectApOrClient(ap.name, ap.name, ap.model, ap.macAddress)
    }

    val apClients = remember(activeUsers, ap, topology) {
        if (!ap.isOnline) emptyList() else {
            val onlineAps = topology.accessPoints.filter { it.isOnline }
            if (onlineAps.size <= 1) {
                activeUsers
            } else {
                val apNameNorm = ap.name.replace(" ", "").lowercase()
                val directMatches = activeUsers.filter { u ->
                    val uNorm = u.user.lowercase()
                    val cNorm = u.comment.lowercase()
                    (apNameNorm.length >= 3 && (uNorm.contains(apNameNorm) || cNorm.contains(apNameNorm))) ||
                    (ap.connectedClientMacs.isNotEmpty() && u.macAddress.uppercase() in ap.connectedClientMacs.map { it.uppercase() })
                }
                val unassigned = activeUsers.filter { u ->
                    onlineAps.none { other ->
                        val oNorm = other.name.replace(" ", "").lowercase()
                        (oNorm.length >= 3 && (u.user.lowercase().contains(oNorm) || u.comment.lowercase().contains(oNorm))) ||
                        (other.connectedClientMacs.isNotEmpty() && u.macAddress.uppercase() in other.connectedClientMacs.map { it.uppercase() })
                    }
                }
                val apIdx = onlineAps.indexOfFirst { it.macAddress == ap.macAddress }
                val partitioned = unassigned.filter { u ->
                    val h = Math.abs(u.macAddress.ifBlank { u.user }.hashCode())
                    (h % onlineAps.size) == apIdx
                }
                directMatches + partitioned
            }
        }
    }

    // Live real-time speeds
    val speedDown = remember(ap.currentRxBps, apClients) {
        if (ap.currentRxBps > 0L) ap.currentRxBps else apClients.sumOf { (it.bytesIn.toLongOrNull() ?: 0L) * 8 / 3 }
    }
    val speedUp = remember(ap.currentTxBps, apClients) {
        if (ap.currentTxBps > 0L) ap.currentTxBps else apClients.sumOf { (it.bytesOut.toLongOrNull() ?: 0L) * 8 / 3 }
    }

    // Daily consumption (Reset at 12:00 AM midnight)
    val apClientMacs = remember(apClients, ap) {
        (apClients.map { it.macAddress.uppercase() } + ap.macAddress.uppercase() + ap.connectedClientMacs.map { it.uppercase() }).toSet()
    }
    val todayHistoryBytes = remember(todaySessions, apClientMacs) {
        todaySessions.filter { s -> s.macAddress.uppercase() in apClientMacs }.sumOf { it.bytesIn + it.bytesOut }
    }
    val currentActiveBytes = remember(apClients) {
        apClients.sumOf { (it.bytesIn.toLongOrNull() ?: 0L) + (it.bytesOut.toLongOrNull() ?: 0L) }
    }
    val totalTodayBytes = maxOf(todayHistoryBytes + currentActiveBytes, ap.dailyBytesIn + ap.dailyBytesOut)

    var userToKick by remember { mutableStateOf<ActiveUser?>(null) }
    var userToBan by remember { mutableStateOf<ActiveUser?>(null) }
    var userToWhitelist by remember { mutableStateOf<ActiveUser?>(null) }
    var userToUndoWhitelist by remember { mutableStateOf<ActiveUser?>(null) }

    val bindingsMap = remember(ipBindings) {
        ipBindings.associateBy { it.macAddress.uppercase() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header: Model Photo & Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    shadowElevation = 3.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
                        if (modelInfo.imageResId != 0) {
                            Image(
                                painter = painterResource(id = modelInfo.imageResId),
                                contentDescription = modelInfo.modelName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Icon(Icons.Default.Wifi, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(32.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ap.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${modelInfo.brand} · ${modelInfo.modelName}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF059669)
                    )
                    Text(
                        text = "IP: ${ap.ipAddress.ifBlank { "DHCP" }} · MAC: ${ap.macAddress}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Real-Time Speed & Daily Usage Dashboard
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Download Speed Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Download", style = MaterialTheme.typography.labelSmall, color = Color(0xFF059669), fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatSpeed(speedDown),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF047857)
                        )
                        Text("Live passing speed", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Upload Speed Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0284C7).copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.3f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Upload", style = MaterialTheme.typography.labelSmall, color = Color(0xFF0284C7), fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatSpeed(speedUp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0369A1)
                        )
                        Text("Live passing speed", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Daily Data Consumed Banner (12:00 AM Reset)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF8B5CF6).copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = Color(0xFF8B5CF6).copy(alpha = 0.2f), modifier = Modifier.size(36.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.DataUsage, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Data Consumed Today", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF6D28D9))
                            Text("Starts at 12:00 AM • Resets daily at 12:00 AM", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        text = formatDataBytes(totalTodayBytes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF6D28D9)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Connected Clients Section Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Connected Clients (${apClients.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (!ap.isWhitelisted) {
                    TextButton(onClick = onAllowlistAp) {
                        Text("Allowlist this AP", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                } else {
                    TextButton(onClick = onUndoApAllowlist) {
                        Text("Remove AP Whitelist", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            }

            if (apClients.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Icon(Icons.Default.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("No Clients Currently Active", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Text("Users who connect to this AP's Wi-Fi will appear here with kick, ban, and whitelist controls.", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 20.dp)) {
                    apClients.forEach { client ->
                        val clientMac = client.macAddress.uppercase()
                        val binding = bindingsMap[clientMac]
                        val isWhitelisted = binding?.type == "bypassed" && !binding.disabled
                        val isBanned = binding?.type == "blocked"

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = client.hostName.ifBlank { client.user },
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "IP: ${client.address} · MAC: ${client.macAddress}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "⏱️ ${client.uptime}  •  💾 ${client.quotaUsedMb} MB used",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = Color(0xFF0284C7),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    if (isWhitelisted) {
                                        AssistChip(
                                            onClick = { userToUndoWhitelist = client },
                                            label = { Text("Whitelisted", fontSize = 10.sp, color = Color(0xFF059669)) },
                                            leadingIcon = { Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp)) }
                                        )
                                    } else if (isBanned) {
                                        AssistChip(
                                            onClick = { onUnbanUser(client.macAddress, binding?.id ?: "") },
                                            label = { Text("Banned", fontSize = 10.sp, color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp)) }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Action Buttons: Whitelist, Kick, Ban
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (!isWhitelisted) {
                                        Button(
                                            onClick = { userToWhitelist = client },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            modifier = Modifier.weight(1f).height(32.dp)
                                        ) {
                                            Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Whitelist", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = { userToUndoWhitelist = client },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            modifier = Modifier.weight(1f).height(32.dp)
                                        ) {
                                            Text("Undo Whitelist", fontSize = 11.sp)
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = { userToKick = client },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.weight(1f).height(32.dp)
                                    ) {
                                        Icon(Icons.Default.PersonRemove, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Kick", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = { userToBan = client },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.weight(1f).height(32.dp)
                                    ) {
                                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Ban", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Kick Dialog
        if (userToKick != null) {
            val u = userToKick!!
            AlertDialog(
                onDismissRequest = { userToKick = null },
                title = { Text("Kick Active User") },
                text = { Text("Disconnect \"${u.user}\" (${u.address}) from network?") },
                confirmButton = {
                    Button(
                        onClick = {
                            onKickUser(u.id)
                            userToKick = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Kick User") }
                },
                dismissButton = { TextButton(onClick = { userToKick = null }) { Text("Cancel") } }
            )
        }

        // Ban Dialog
        if (userToBan != null) {
            val u = userToBan!!
            AlertDialog(
                onDismissRequest = { userToBan = null },
                title = { Text("Ban Client Device") },
                text = { Text("Ban MAC address \"${u.macAddress}\" (${u.hostName.ifBlank { u.user }}) from network?") },
                confirmButton = {
                    Button(
                        onClick = {
                            onBanUser(u.macAddress)
                            userToBan = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Ban Device") }
                },
                dismissButton = { TextButton(onClick = { userToBan = null }) { Text("Cancel") } }
            )
        }

        // Whitelist Dialog
        if (userToWhitelist != null) {
            val u = userToWhitelist!!
            AlertDialog(
                onDismissRequest = { userToWhitelist = null },
                title = { Text("Allowlist Device") },
                text = { Text("Allow \"${u.hostName.ifBlank { u.user }}\" (${u.address}) to bypass captive portal without voucher?") },
                confirmButton = {
                    Button(
                        onClick = {
                            onWhitelistClient(u.macAddress, u.address, "AP: ${ap.name} - ${u.hostName.ifBlank { u.user }}")
                            userToWhitelist = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) { Text("Allowlist Device", color = Color.White) }
                },
                dismissButton = { TextButton(onClick = { userToWhitelist = null }) { Text("Cancel") } }
            )
        }

        // Undo Whitelist Dialog
        if (userToUndoWhitelist != null) {
            val u = userToUndoWhitelist!!
            val binding = bindingsMap[u.macAddress.uppercase()]
            AlertDialog(
                onDismissRequest = { userToUndoWhitelist = null },
                title = { Text("Remove Allowlist") },
                text = { Text("Remove allowlist for \"${u.macAddress}\"? They will be required to authenticate via captive portal.") },
                confirmButton = {
                    Button(
                        onClick = {
                            binding?.let { onUndoClientWhitelist(it.id, u.macAddress) }
                            userToUndoWhitelist = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Remove") }
                },
                dismissButton = { TextButton(onClick = { userToUndoWhitelist = null }) { Text("Cancel") } }
            )
        }
    }
}
