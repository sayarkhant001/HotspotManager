package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.data.remote.CloudApiClient
import com.example.ui.components.GlassCard
import com.example.ui.components.PrinterSettingsSection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.example.BuildConfig
import com.example.utils.AppLanguage
import com.example.utils.LanguageManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, navController: NavController) {
    val context          = LocalContext.current
    val cloudUser        by viewModel.cloudUser.collectAsStateWithLifecycle()
    val connMode         by viewModel.connectionMode.collectAsStateWithLifecycle()
    val assignedRouters  by viewModel.assignedRouters.collectAsStateWithLifecycle()
    val pppoeEnabled     by viewModel.pppoeEnabled.collectAsStateWithLifecycle()
    val currentLang      by LanguageManager.currentLanguage
    val appUpdateInfo    by viewModel.appUpdateInfo.collectAsStateWithLifecycle()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsStateWithLifecycle()
    val userMsg          by viewModel.userMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showPppoeSetup   by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showServerUrl    by remember { mutableStateOf(false) }

    LaunchedEffect(userMsg) {
        userMsg?.let { snackbarHostState.showSnackbar(it); viewModel.clearUserMessage() }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Account Section ──────────────────────────────────────────────
            item {
                SettingsSectionHeader("Account")
                GlassCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(cloudUser?.fullName ?: "Not logged in", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                Text(cloudUser?.username ?: "", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodySmall)
                                Text(cloudUser?.role?.replaceFirstChar { it.uppercase() } ?: "", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        SettingsRow(icon = Icons.Default.Logout, label = "Logout", color = Color(0xFFEF4444)) {
                            showLogoutConfirm = true
                        }
                    }
                }
            }

            // ── Connection Section ───────────────────────────────────────────
            item {
                SettingsSectionHeader("Connection")
                GlassCard {
                    Column(Modifier.padding(4.dp)) {
                        SettingsRow(
                            icon = Icons.Default.Cloud,
                            label = "Connection Mode",
                            value = when (connMode) {
                                "cloud_remote" -> "☁️ Cloud Remote"
                                "direct"       -> "📶 Direct Wi-Fi"
                                else           -> "🔗 Local"
                            }
                        ) {}
                        HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        SettingsRow(icon = Icons.Default.Dns, label = "Server URL",
                            value = CloudApiClient.serverUrl.removePrefix("http://")) {
                            showServerUrl = true
                        }
                    }
                }
            }

            // ── Routers Section ──────────────────────────────────────────────
            if (assignedRouters.isNotEmpty()) {
                item {
                    SettingsSectionHeader("Routers (${assignedRouters.size})")
                    GlassCard {
                        Column(Modifier.padding(4.dp)) {
                            assignedRouters.forEachIndexed { index, router ->
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).also {
                                        // color set below
                                    }) {
                                        Surface(
                                            Modifier.size(8.dp),
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (router.status == "online") Color(0xFF10B981) else Color(0xFF94A3B8)
                                        ) {}
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(router.name, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                                        Text("${router.wgIp} · ${router.status}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                    if (router.status == "online") {
                                        Surface(color = Color(0xFF10B981).copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                                            Text("ONLINE", fontSize = 9.sp, color = Color(0xFF10B981),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                                if (index < assignedRouters.size - 1)
                                    HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            }
                        }
                    }
                }
            }

            // ── PPPoE Section ────────────────────────────────────────────────
            item {
                SettingsSectionHeader("PPPoE Server")
                GlassCard {
                    Column(Modifier.padding(4.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Router, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("PPPoE Server", fontWeight = FontWeight.Medium)
                                Text(if (pppoeEnabled) "Configured and active" else "Not configured on this router",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (pppoeEnabled) Color(0xFF10B981) else MaterialTheme.colorScheme.outline)
                            }
                            if (!pppoeEnabled) {
                                FilledTonalButton(onClick = { showPppoeSetup = true }, modifier = Modifier.height(34.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp)) {
                                    Text("Enable", style = MaterialTheme.typography.labelMedium)
                                }
                            } else {
                                Surface(color = Color(0xFF10B981).copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) {
                                    Text("Active", fontSize = 11.sp, color = Color(0xFF10B981),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                                }
                            }
                        }
                    }
                }
            }

            // ── App Section ──────────────────────────────────────────────────
            item {
                SettingsSectionHeader("App")
                GlassCard {
                    Column(Modifier.padding(4.dp)) {
                        SettingsRow(
                            icon = Icons.Default.Language,
                            label = "Language",
                            value = if (currentLang == AppLanguage.MYANMAR) "🇲🇲 မြန်မာ" else "🇬🇧 English"
                        ) { LanguageManager.toggleLanguage(context) }

                        HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                        SettingsRow(
                            icon = Icons.Default.SystemUpdate,
                            label = "Check for Updates",
                            value = if (appUpdateInfo?.isNewer == true) "v${BuildConfig.VERSION_NAME} (Update: v${appUpdateInfo?.versionName})" else "v${BuildConfig.VERSION_NAME}",
                            valueColor = if (appUpdateInfo?.isNewer == true) Color(0xFF10B981) else null
                        ) {
                            if (appUpdateInfo?.isNewer == true) {
                                viewModel.openUpdateDialog()
                            } else {
                                viewModel.checkForAppUpdate(manual = true)
                            }
                        }

                        HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                        SettingsRow(icon = Icons.Default.Wifi, label = "Network Topology") {
                            navController.navigate("network_topology")
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(4.dp)) }

            // ── Bluetooth Printer Section ─────────────────────────────────────
            item {
                SettingsSectionHeader("Bluetooth Printer")
                GlassCard {
                    Column(Modifier.padding(16.dp)) {
                        PrinterSettingsSection()
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    // ── Enable PPPoE Dialog ───────────────────────────────────────────────────
    if (showPppoeSetup) {
        var serviceName by remember { mutableStateOf("pppoe-server") }
        AlertDialog(
            onDismissRequest = { showPppoeSetup = false },
            title = { Text("Enable PPPoE Server", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("This will configure a PPPoE server on your router. Client IPs will be auto-assigned from 192.168.200.0/24.")
                    OutlinedTextField(
                        value = serviceName,
                        onValueChange = { serviceName = it.replace(" ", "-") },
                        label = { Text("Service Name") },
                        supportingText = { Text("Letters, numbers, hyphens only") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.enablePppoeServer(serviceName)
                    showPppoeSetup = false
                }) { Text("Enable PPPoE") }
            },
            dismissButton = { TextButton(onClick = { showPppoeSetup = false }) { Text("Cancel") } }
        )
    }

    // ── Logout Confirm ────────────────────────────────────────────────────────
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Logout?") },
            text = { Text("You will be logged out of the cloud account. Local router connection will also be cleared.") },
            confirmButton = {
                Button(
                    onClick = {
                        CloudApiClient.clearAuth(context)
                        viewModel.cloudUser.value = null
                        viewModel.assignedRouters.value = emptyList()
                        viewModel.pppoeEnabled.value = false
                        navController.navigate("login") { popUpTo(0) { inclusive = true } }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) { Text("Logout") }
            },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel") } }
        )
    }
}

// ── Helper Composables ────────────────────────────────────────────────────────
@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    value: String? = null,
    color: Color? = null,
    valueColor: Color? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, tint = color ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        Text(label, Modifier.weight(1f), color = color ?: MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodySmall, color = valueColor ?: MaterialTheme.colorScheme.outline,
                maxLines = 1)
        }
        if (value != null || onClick != {}) {
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
        }
    }
}
