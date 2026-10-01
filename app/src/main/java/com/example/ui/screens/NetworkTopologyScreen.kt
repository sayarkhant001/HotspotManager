package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.example.ui.components.GlassCard
import com.example.utils.LanguageManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkTopologyScreen(
    viewModel: MainViewModel,
    navController: NavController
) {
    val topology by viewModel.networkTopology.collectAsStateWithLifecycle()
    val isLoading by viewModel.isTopologyLoading.collectAsStateWithLifecycle()
    val strings = LanguageManager.strings

    var apToAllowlist by remember { mutableStateOf<AccessPointDevice?>(null) }
    var apToUndo by remember { mutableStateOf<AccessPointDevice?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    // Zoom state
    var scale by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(Unit) {
        viewModel.fetchNetworkTopology()
        viewModel.fetchIpBindings()
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
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Zoom Controls
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                    shadowElevation = 4.dp
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        IconButton(
                            onClick = { scale = (scale + 0.15f).coerceAtMost(2.0f) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(20.dp))
                        }
                        HorizontalDivider(
                            modifier = Modifier.width(24.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        IconButton(
                            onClick = { scale = (scale - 0.15f).coerceAtLeast(0.6f) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // Add Device Button (Matches green button from screenshot)
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981) // Ruijie/Emerald green
                    ),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = strings.addDevice,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
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
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                // 1. Internet Cloud Node
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFE0F2FE),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = "Internet",
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Internet",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Connecting Line (Internet -> Gateway) with 3 green dots
                ConnectingVerticalLine(dots = 3, color = Color(0xFF10B981))

                // 2. Gateway Router Node (RB4011iGS+)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp)
                    ) {
                        // Router Icon & Port LEDs representation
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Router,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Visual Ethernet Ports
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                repeat(6) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 6.dp, height = 8.dp)
                                            .clip(RoundedCornerShape(1.dp))
                                            .background(if (it < 3) Color(0xFF10B981) else Color(0xFF64748B))
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Model Name
                        Text(
                            text = topology.routerModel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Gateway Badge
                        Text(
                            text = strings.gateway,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0284C7)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // IP Details
                        Text(
                            text = "Manage IP: ${topology.manageIp}  •  Hotspot IP: ${topology.hotspotIp}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 3. Branching Lines down to Connected APs
                val aps = topology.accessPoints

                if (aps.isNotEmpty()) {
                    BranchingLines(apCount = aps.size)

                    // Connected APs Grid / Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Top
                    ) {
                        aps.forEach { ap ->
                            ApDeviceNode(
                                ap = ap,
                                strings = strings,
                                onAllowlist = { apToAllowlist = ap },
                                onUndo = { apToUndo = ap }
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(24.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiTetheringOff,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No APs Detected Automatically",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Connect Ruijie APs (EST310, EST350, EW series) to Hotspot bridge or tap 'Add Device' below to manually allowlist an AP.",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(90.dp))
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
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

        // Add Device Dialog
        if (showAddDialog) {
            AddDeviceDialog(
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
    }
}

@Composable
fun ApDeviceNode(
    ap: AccessPointDevice,
    strings: com.example.utils.AppStrings,
    onAllowlist: () -> Unit,
    onUndo: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(160.dp)
            .padding(4.dp)
    ) {
        // Circular AP Icon with online/allowlist badge
        Box(contentAlignment = Alignment.TopEnd) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(
                    2.dp,
                    if (ap.isWhitelisted) Color(0xFF10B981) else Color(0xFFEF4444)
                ),
                modifier = Modifier.size(68.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = "AP",
                        tint = if (ap.isWhitelisted) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            // Top-right status dot (Green for allowlisted/online, Red for un-whitelisted/needs action)
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (ap.isWhitelisted) Color(0xFF10B981) else Color(0xFFEF4444))
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Device Title
        Text(
            text = ap.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        // Subtitle "Access Point"
        Text(
            text = strings.accessPoint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF10B981)
        )

        Spacer(modifier = Modifier.height(2.dp))

        // IP Address and MAC Address
        Text(
            text = "${ap.ipAddress.ifBlank { "DHCP" }} · ${ap.macAddress.take(8)}...",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Allowlist Action Button or Whitelisted Status
        if (!ap.isWhitelisted) {
            Button(
                onClick = onAllowlist,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981)
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text(
                    text = "Allowlist",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        } else {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF10B981).copy(alpha = 0.15f),
                modifier = Modifier.clickable { onUndo() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Allowed",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
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
fun BranchingLines(apCount: Int) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = 40.dp)
    ) {
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        )
        val color = Color(0xFF94A3B8)
        val centerX = size.width / 2f

        // Draw vertical center stem
        drawLine(
            color = color,
            start = Offset(centerX, 0f),
            end = Offset(centerX, size.height * 0.45f),
            strokeWidth = 2.dp.toPx(),
            pathEffect = stroke.pathEffect
        )

        if (apCount > 1) {
            // Draw horizontal crossbar
            drawLine(
                color = color,
                start = Offset(size.width * 0.2f, size.height * 0.45f),
                end = Offset(size.width * 0.8f, size.height * 0.45f),
                strokeWidth = 2.dp.toPx(),
                pathEffect = stroke.pathEffect
            )
            // Left branch
            drawLine(
                color = color,
                start = Offset(size.width * 0.2f, size.height * 0.45f),
                end = Offset(size.width * 0.2f, size.height),
                strokeWidth = 2.dp.toPx(),
                pathEffect = stroke.pathEffect
            )
            // Right branch
            drawLine(
                color = color,
                start = Offset(size.width * 0.8f, size.height * 0.45f),
                end = Offset(size.width * 0.8f, size.height),
                strokeWidth = 2.dp.toPx(),
                pathEffect = stroke.pathEffect
            )
        } else {
            drawLine(
                color = color,
                start = Offset(centerX, size.height * 0.45f),
                end = Offset(centerX, size.height),
                strokeWidth = 2.dp.toPx(),
                pathEffect = stroke.pathEffect
            )
        }
    }
}

@Composable
fun AddDeviceDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, ip: String, mac: String) -> Unit
) {
    var deviceName by remember { mutableStateOf("EST310-AP") }
    var ipAddress by remember { mutableStateOf("192.168.100.") }
    var macAddress by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add AP Device", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Enter the Ruijie/Reyee AP details to allowlist and track in network topology:",
                    style = MaterialTheme.typography.bodySmall
                )
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (macAddress.isNotBlank()) {
                        onAdd(deviceName.trim(), ipAddress.trim(), macAddress.trim())
                    }
                },
                enabled = macAddress.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                Text("Allowlist Device", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
