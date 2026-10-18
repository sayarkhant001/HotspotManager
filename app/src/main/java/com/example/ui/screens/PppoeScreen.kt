package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.ui.components.GlassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PppoeScreen(viewModel: MainViewModel, navController: NavController) {
    val subscribers   by viewModel.pppoeSubscribers.collectAsStateWithLifecycle()
    val activeSessions by viewModel.pppoeActiveSessions.collectAsStateWithLifecycle()
    val isLoading     by viewModel.isPppoeLoading.collectAsStateWithLifecycle()
    val userMsg       by viewModel.userMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog     by remember { mutableStateOf(false) }
    var showEnableDialog  by remember { mutableStateOf(false) }
    var selectedTab       by remember { mutableIntStateOf(0) } // 0=Subscribers, 1=Active
    var actionUser        by remember { mutableStateOf<PppoeSubscriber?>(null) }
    var showActionSheet   by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.fetchPppoeData() }
    LaunchedEffect(userMsg) {
        userMsg?.let { snackbarHostState.showSnackbar(it); viewModel.clearUserMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("PPPoE Subscribers", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("${subscribers.size} users · ${activeSessions.size} online",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    IconButton(onClick = { viewModel.fetchPppoeData() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.PersonAdd, "Add subscriber", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // ── Tab Bar ──────────────────────────────────────────────────────
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    text = { Text("Subscribers (${subscribers.size})") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    text = { Text("Active (${activeSessions.size})", color = if (activeSessions.isNotEmpty()) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface) })
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            // ── Tab 0: Subscriber List ────────────────────────────────────────
            if (selectedTab == 0) {
                if (subscribers.isEmpty()) {
                    EmptyPppoeState(onAdd = { showAddDialog = true })
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(subscribers, key = { it.name }) { sub ->
                            PppoeSubscriberCard(
                                sub = sub,
                                onAction = { actionUser = sub; showActionSheet = true }
                            )
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }

            // ── Tab 1: Active Sessions ────────────────────────────────────────
            if (selectedTab == 1) {
                if (activeSessions.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CloudOff, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp))
                            Text("No active PPPoE sessions", color = MaterialTheme.colorScheme.outline)
                        }
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(activeSessions, key = { it.name }) { session ->
                            PppoeActiveCard(session)
                        }
                    }
                }
            }
        }
    }

    // ── Add Subscriber Dialog ─────────────────────────────────────────────────
    if (showAddDialog) {
        AddPppoeSubscriberDialog(
            onDismiss = { showAddDialog = false },
            onCreate = { username, password, dl, ul, dataGb, days ->
                viewModel.createPppoeUser(username, password, dl, ul, dataGb, days)
                showAddDialog = false
            }
        )
    }

    // ── Subscriber Action Sheet ───────────────────────────────────────────────
    if (showActionSheet && actionUser != null) {
        val sub = actionUser!!
        PppoeActionSheet(
            sub = sub,
            onDismiss = { showActionSheet = false },
            onRenew = { days -> viewModel.renewPppoeUser(sub.name, days); showActionSheet = false },
            onToggleDisable = { viewModel.disablePppoeUser(sub.name, !sub.disabled); showActionSheet = false },
            onDelete = { viewModel.deletePppoeUser(sub.name); showActionSheet = false }
        )
    }
}

// ── Subscriber Card ───────────────────────────────────────────────────────────
@Composable
private fun PppoeSubscriberCard(sub: PppoeSubscriber, onAction: () -> Unit) {
    val onlineColor = if (sub.isOnline) Color(0xFF10B981) else Color(0xFF94A3B8)
    val expiryColor = when {
        sub.expiryDays <= 3  -> Color(0xFFEF4444)
        sub.expiryDays <= 7  -> Color(0xFFF59E0B)
        else                 -> Color(0xFF10B981)
    }

    GlassCard(modifier = Modifier.fillMaxWidth().clickable { onAction() }) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            // Online dot + avatar
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(sub.name.take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary)
                }
                Box(
                    Modifier.size(12.dp).clip(CircleShape)
                        .background(onlineColor)
                        .background(MaterialTheme.colorScheme.surface, CircleShape) // border
                        .padding(2.dp)
                        .background(onlineColor, CircleShape)
                )
            }

            Spacer(Modifier.width(12.dp))

            // Name + info
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(sub.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (sub.disabled) {
                        Surface(color = Color(0xFFEF4444).copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                            Text("DISABLED", fontSize = 9.sp, color = Color(0xFFEF4444),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("↓${sub.downloadMbps}M ↑${sub.uploadMbps}M",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    if (sub.dataLimitGb > 0)
                        Text("${sub.dataLimitGb}GB/mo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                if (sub.isOnline) {
                    Text("🟢 ${sub.currentIp} · ${sub.uptimeStr}",
                        style = MaterialTheme.typography.labelSmall, color = Color(0xFF10B981))
                }
            }

            Spacer(Modifier.width(8.dp))

            // Expiry badge
            if (sub.expiryDate.isNotBlank()) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (sub.expiryDays > 0) "${sub.expiryDays}d left" else "Expired",
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = expiryColor)
                    Text(sub.expiryDate, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                }
            }

            Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp).padding(start = 4.dp))
        }
    }
}

// ── Active Session Card ───────────────────────────────────────────────────────
@Composable
private fun PppoeActiveCard(session: PppoeActiveSession) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF10B981)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(session.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text("${session.address} · ${session.uptime}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline)
            }
            Text(session.service, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ── Empty State ───────────────────────────────────────────────────────────────
@Composable
private fun EmptyPppoeState(onAdd: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Router, null, modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline)
            Text("No PPPoE Subscribers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Add your first PPPoE subscriber\nto start managing internet accounts",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Button(onClick = onAdd) {
                Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add Subscriber")
            }
        }
    }
}

// ── Add Subscriber Dialog ─────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPppoeSubscriberDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, Int, Int, Int, Int) -> Unit
) {
    var username     by remember { mutableStateOf("") }
    var password     by remember { mutableStateOf("") }
    var showPass     by remember { mutableStateOf(false) }
    var downloadMbps by remember { mutableStateOf("10") }
    var uploadMbps   by remember { mutableStateOf("5") }
    var dataLimitGb  by remember { mutableStateOf("0") }
    var validityDays by remember { mutableStateOf("30") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add PPPoE Subscriber", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = username, onValueChange = { username = it.trim() },
                    label = { Text("Username") },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    trailingIcon = {
                        IconButton(onClick = { showPass = !showPass }) {
                            Icon(if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                        }
                    },
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = downloadMbps, onValueChange = { downloadMbps = it.filter { c -> c.isDigit() } },
                        label = { Text("DL (Mbps)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uploadMbps, onValueChange = { uploadMbps = it.filter { c -> c.isDigit() } },
                        label = { Text("UL (Mbps)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dataLimitGb, onValueChange = { dataLimitGb = it.filter { c -> c.isDigit() } },
                        label = { Text("Data GB (0=∞)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = validityDays, onValueChange = { validityDays = it.filter { c -> c.isDigit() } },
                        label = { Text("Days") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                }
                Text("Data limit 0 = unlimited. Speed 0 = use profile default.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (username.isNotBlank() && password.isNotBlank()) {
                        onCreate(
                            username, password,
                            downloadMbps.toIntOrNull() ?: 10,
                            uploadMbps.toIntOrNull() ?: 5,
                            dataLimitGb.toIntOrNull() ?: 0,
                            validityDays.toIntOrNull() ?: 30
                        )
                    }
                },
                enabled = username.isNotBlank() && password.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── Action Sheet ──────────────────────────────────────────────────────────────
@Composable
private fun PppoeActionSheet(
    sub: PppoeSubscriber,
    onDismiss: () -> Unit,
    onRenew: (Int) -> Unit,
    onToggleDisable: () -> Unit,
    onDelete: () -> Unit
) {
    var showRenewDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(sub.name, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ListItem(
                    headlineContent = { Text("Renew Subscription") },
                    leadingContent = { Icon(Icons.Default.Autorenew, null, tint = Color(0xFF10B981)) },
                    modifier = Modifier.clickable { showRenewDialog = true }.clip(RoundedCornerShape(8.dp))
                )
                ListItem(
                    headlineContent = { Text(if (sub.disabled) "Enable Account" else "Disable Account") },
                    leadingContent = { Icon(
                        if (sub.disabled) Icons.Default.PlayArrow else Icons.Default.Pause,
                        null, tint = Color(0xFFF59E0B)) },
                    modifier = Modifier.clickable(onClick = onToggleDisable).clip(RoundedCornerShape(8.dp))
                )
                ListItem(
                    headlineContent = { Text("Delete Account", color = Color(0xFFEF4444)) },
                    leadingContent = { Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444)) },
                    modifier = Modifier.clickable { showDeleteConfirm = true }.clip(RoundedCornerShape(8.dp))
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )

    if (showRenewDialog) {
        RenewDialog(onDismiss = { showRenewDialog = false }, onRenew = { days -> onRenew(days); showRenewDialog = false })
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ${sub.name}?") },
            text = { Text("This will permanently remove the PPPoE account and kick the subscriber immediately.") },
            confirmButton = { Button(onClick = onDelete, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun RenewDialog(onDismiss: () -> Unit, onRenew: (Int) -> Unit) {
    var days by remember { mutableStateOf("30") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renew Subscription") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Add how many days to this subscriber's account?")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("7", "30", "60", "90").forEach { preset ->
                        FilterChip(selected = days == preset, onClick = { days = preset }, label = { Text("${preset}d") })
                    }
                }
                OutlinedTextField(
                    value = days, onValueChange = { days = it.filter { c -> c.isDigit() } },
                    label = { Text("Custom days") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { Button(onClick = { onRenew(days.toIntOrNull() ?: 30) }) { Text("Renew") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
