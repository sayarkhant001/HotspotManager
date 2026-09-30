package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.domain.models.ActiveUser
import com.example.ui.components.GlassCard

import com.example.utils.LanguageManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionsScreen(viewModel: MainViewModel, navController: NavController) {
    val users by viewModel.activeUsers.collectAsStateWithLifecycle()
    val strings = LanguageManager.strings
    var searchQuery by remember { mutableStateOf("") }
    var userToBan by remember { mutableStateOf<ActiveUser?>(null) }
    var userToKick by remember { mutableStateOf<ActiveUser?>(null) }

    LaunchedEffect(Unit) {
        viewModel.fetchRouterData()
    }

    val filteredUsers = remember(users, searchQuery) {
        if (searchQuery.isBlank()) users
        else users.filter {
            it.user.contains(searchQuery, ignoreCase = true) ||
            it.address.contains(searchQuery, ignoreCase = true) ||
            it.macAddress.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("${strings.activeSessionsTitle} (${users.size})", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.fetchRouterData() }) {
                        Text(strings.refresh, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                placeholder = { Text(strings.searchSessionsHint) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = strings.search) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = strings.close)
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(filteredUsers) { user ->
                    ActiveUserCard(
                        user = user,
                        strings = strings,
                        onKick = { userToKick = user },
                        onBan = { userToBan = user }
                    )
                }
                if (filteredUsers.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isEmpty()) strings.noActiveSessions else "\"$searchQuery\" နှင့် ကိုက်ညီသော စက်မရှိပါ။",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Kick Confirmation Dialog
        if (userToKick != null) {
            AlertDialog(
                onDismissRequest = { userToKick = null },
                title = { Text(strings.kickConfirmTitle) },
                text = { Text("\"${userToKick?.user}\" (${userToKick?.address})\n\n${strings.kickConfirmMsg}") },
                confirmButton = {
                    Button(
                        onClick = {
                            userToKick?.id?.let { viewModel.kickUser(it) }
                            userToKick = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(strings.kickAction)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { userToKick = null }) { Text(strings.cancel) }
                }
            )
        }

        // Ban MAC Confirmation Dialog
        if (userToBan != null) {
            AlertDialog(
                onDismissRequest = { userToBan = null },
                title = { Text(strings.banConfirmTitle) },
                text = {
                    Text("MAC: \"${userToBan?.macAddress}\"\n\n${strings.banConfirmMsg}")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            userToBan?.macAddress?.let { viewModel.banMac(it) }
                            userToBan = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(strings.banAction)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { userToBan = null }) { Text(strings.cancel) }
                }
            )
        }
    }
}

@Composable
fun ActiveUserCard(
    user: ActiveUser,
    strings: com.example.utils.AppStrings,
    onKick: () -> Unit,
    onBan: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = user.user,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (user.profileName.isNotBlank()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = user.profileName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        if (user.hostName.isNotBlank()) {
                            Text(
                                text = "📱 ${user.hostName}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "IP: ${user.address}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row {
                    // Kick button
                    IconButton(onClick = onKick) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = strings.kickClient,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    // Ban MAC button
                    IconButton(onClick = onBan) {
                        Icon(
                            Icons.Default.Block,
                            contentDescription = strings.banMac,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(10.dp))

            // Quota usage & remaining (varies with user's profile)
            if (user.quotaTotalMb > 0) {
                val progress = (user.quotaUsedMb / user.quotaTotalMb).toFloat().coerceIn(0f, 1f)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${strings.totalDataUsage}: ${user.quotaUsedMb.toInt()} MB / %,d MB".format(user.quotaTotalMb),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (progress > 0.85f) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "%,d MB left".format(user.quotaRemainingMb.toInt()),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (progress > 0.85f) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = if (progress > 0.85f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${strings.totalDataUsage}: ${user.quotaUsedMb.toInt()} MB",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "Unlimited Quota",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "MAC: ${user.macAddress}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${strings.uptime}: ${user.uptime}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            val bytesIn = user.bytesIn.toLongOrNull() ?: 0L
            val bytesOut = user.bytesOut.toLongOrNull() ?: 0L
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${strings.downloadRx}: ${formatBytesVal(bytesOut)}",
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = "${strings.uploadTx}: ${formatBytesVal(bytesIn)}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

private fun formatBytesVal(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        kb >= 1.0 -> String.format("%.0f KB", kb)
        else -> "$bytes B"
    }
}

