package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import com.example.domain.models.IpBinding
import com.example.ui.components.GlassCard
import com.example.utils.LanguageManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionsScreen(viewModel: MainViewModel, navController: NavController) {
    val users by viewModel.activeUsers.collectAsStateWithLifecycle()
    val whitelisted by viewModel.whitelistedClients.collectAsStateWithLifecycle()
    val banned by viewModel.bannedClients.collectAsStateWithLifecycle()
    val strings = LanguageManager.strings

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var userToBan by remember { mutableStateOf<ActiveUser?>(null) }
    var userToKick by remember { mutableStateOf<ActiveUser?>(null) }
    var userToWhitelist by remember { mutableStateOf<ActiveUser?>(null) }
    var clientToUndoWhitelist by remember { mutableStateOf<IpBinding?>(null) }
    var clientToUnban by remember { mutableStateOf<IpBinding?>(null) }

    LaunchedEffect(Unit) {
        viewModel.fetchIpBindings()
        while (true) {
            viewModel.fetchRouterData()
            kotlinx.coroutines.delay(3000)
        }
    }

    val filteredUsers = remember(users, searchQuery) {
        if (searchQuery.isBlank()) users
        else users.filter {
            it.user.contains(searchQuery, ignoreCase = true) ||
            it.address.contains(searchQuery, ignoreCase = true) ||
            it.macAddress.contains(searchQuery, ignoreCase = true) ||
            it.hostName.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredWhitelisted = remember(whitelisted, searchQuery) {
        if (searchQuery.isBlank()) whitelisted
        else whitelisted.filter {
            it.comment.contains(searchQuery, ignoreCase = true) ||
            it.address.contains(searchQuery, ignoreCase = true) ||
            it.macAddress.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredBanned = remember(banned, searchQuery) {
        if (searchQuery.isBlank()) banned
        else banned.filter {
            it.comment.contains(searchQuery, ignoreCase = true) ||
            it.address.contains(searchQuery, ignoreCase = true) ||
            it.macAddress.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(strings.activeSessionsTitle, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        viewModel.fetchRouterData()
                        viewModel.fetchIpBindings()
                    }) {
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
            // Tabs: Active, Whitelisted, Banned
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("${strings.activeClientsTab} (${users.size})", fontWeight = FontWeight.Bold, maxLines = 1) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("${strings.whitelistedClients} (${whitelisted.size})", fontWeight = FontWeight.Bold, maxLines = 1) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("${strings.bannedClients} (${banned.size})", fontWeight = FontWeight.Bold, maxLines = 1) }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
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

            when (selectedTab) {
                0 -> {
                    // Active Sessions Tab
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        items(filteredUsers, key = { it.id }) { user ->
                            ActiveUserCard(
                                user = user,
                                strings = strings,
                                onWhitelist = { userToWhitelist = user },
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
                1 -> {
                    // Whitelisted Clients Tab
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        items(filteredWhitelisted, key = { it.id.ifBlank { it.macAddress } }) { item ->
                            WhitelistedUserCard(
                                item = item,
                                strings = strings,
                                onUndo = { clientToUndoWhitelist = item }
                            )
                        }
                        if (filteredWhitelisted.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No whitelisted clients found.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // Banned Clients Tab
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        items(filteredBanned, key = { it.id.ifBlank { it.macAddress } }) { item ->
                            BannedUserCard(
                                item = item,
                                strings = strings,
                                onUnban = { clientToUnban = item }
                            )
                        }
                        if (filteredBanned.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No banned clients.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Whitelist Confirmation Dialog
        if (userToWhitelist != null) {
            val u = userToWhitelist!!
            AlertDialog(
                onDismissRequest = { userToWhitelist = null },
                icon = {
                    Icon(
                        Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = { Text(strings.whitelistAction, fontWeight = FontWeight.Bold) },
                text = {
                    Text("Allow \"${u.user}\" (${u.address} / ${u.macAddress}) to use the internet without voucher authentication?")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.whitelistDevice(
                                mac = u.macAddress,
                                ip = u.address,
                                comment = "Whitelisted: ${u.user}"
                            )
                            userToWhitelist = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text(strings.whitelistAction, color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { userToWhitelist = null }) { Text(strings.cancel) }
                }
            )
        }

        // Undo Whitelist Dialog
        if (clientToUndoWhitelist != null) {
            val item = clientToUndoWhitelist!!
            AlertDialog(
                onDismissRequest = { clientToUndoWhitelist = null },
                title = { Text(strings.undoAction) },
                text = {
                    Text("Remove whitelist for MAC \"${item.macAddress}\"? They will be required to authenticate via captive portal again.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.removeIpBinding(item.id, item.macAddress)
                            clientToUndoWhitelist = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(strings.undoAction)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { clientToUndoWhitelist = null }) { Text(strings.cancel) }
                }
            )
        }

        // Undo Ban / Unban Dialog
        if (clientToUnban != null) {
            val item = clientToUnban!!
            AlertDialog(
                onDismissRequest = { clientToUnban = null },
                title = { Text(strings.undoAction) },
                text = {
                    Text("Unban MAC \"${item.macAddress}\" and allow connecting to network again?")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.unbanMac(item.macAddress, item.id)
                            clientToUnban = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text("Unban", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { clientToUnban = null }) { Text(strings.cancel) }
                }
            )
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
    onWhitelist: () -> Unit,
    onKick: () -> Unit,
    onBan: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = user.user,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            if (user.profileName.isNotBlank()) {
                                Spacer(modifier = Modifier.width(5.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = user.profileName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        if (user.hostName.isNotBlank()) {
                            Text(
                                text = "📱 ${user.hostName}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "IP: ${user.address}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Whitelist button (Allow internet without voucher)
                    IconButton(onClick = onWhitelist, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.VerifiedUser,
                            contentDescription = strings.whitelistAction,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    // Kick button
                    IconButton(onClick = onKick, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = strings.kickClient,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    // Ban MAC button
                    IconButton(onClick = onBan, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Block,
                            contentDescription = strings.banMac,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            Spacer(modifier = Modifier.height(8.dp))

            // Quota usage & remaining
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
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(5.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    color = if (progress > 0.85f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
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
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Unlimited Quota",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // MAC, Uptime and Real-time Traffic Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MAC: ${user.macAddress}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${strings.uptime}: ${user.uptime}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun WhitelistedUserCard(
    item: IpBinding,
    strings: com.example.utils.AppStrings,
    onUndo: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = item.comment.ifBlank { "Whitelisted Device" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "MAC: ${item.macAddress}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (item.address.isNotBlank()) {
                        Text(
                            text = "IP: ${item.address}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Button(
                onClick = onUndo,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = strings.undoAction,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun BannedUserCard(
    item: IpBinding,
    strings: com.example.utils.AppStrings,
    onUnban: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Block,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = item.comment.ifBlank { "Banned Device" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "MAC: ${item.macAddress}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Button(
                onClick = onUnban,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981)
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Unban",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
