package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.domain.models.ActiveUser
import com.example.domain.models.IpBinding
import com.example.ui.components.GlassCard
import com.example.utils.DeviceModelDetector
import com.example.utils.LanguageManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionsScreen(viewModel: MainViewModel, navController: NavController) {
    val users by viewModel.activeUsers.collectAsStateWithLifecycle()
    val whitelisted by viewModel.whitelistedClients.collectAsStateWithLifecycle()
    val strings = LanguageManager.strings

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var userToRelease by remember { mutableStateOf<ActiveUser?>(null) }
    var userToKick by remember { mutableStateOf<ActiveUser?>(null) }
    var userToWhitelist by remember { mutableStateOf<ActiveUser?>(null) }
    var clientToUndoWhitelist by remember { mutableStateOf<IpBinding?>(null) }

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
            // Tabs: Active, Whitelisted
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
                    val unwhitelistedAps = remember(filteredUsers, whitelisted) {
                        val whitelistedMacs = whitelisted.map { it.macAddress.trim().uppercase() }.toSet()
                        filteredUsers.filter { user ->
                            val info = DeviceModelDetector.detectApOrClient(user.user, user.hostName, user.profileName, user.macAddress)
                            info.isApOrBridge && !whitelistedMacs.contains(user.macAddress.trim().uppercase())
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        if (unwhitelistedAps.isNotEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.12f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.SettingsInputAntenna,
                                            contentDescription = null,
                                            tint = Color(0xFF059669),
                                            modifier = Modifier.size(26.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${unwhitelistedAps.size} AP/Bridge Detected",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = Color(0xFF059669)
                                            )
                                            Text(
                                                text = "Connected Ruijie/TP-Link hardware detected. Tap Whitelist on the card below to bypass captive portal.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        items(filteredUsers, key = { it.id }) { user ->
                            ActiveUserCard(
                                user = user,
                                strings = strings,
                                onWhitelist = { userToWhitelist = user },
                                onKick = { userToKick = user },
                                onReleaseMac = { userToRelease = user }
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
            }
        }

        // Whitelist Confirmation Dialog
        if (userToWhitelist != null) {
            val u = userToWhitelist!!
            val modelInfo = remember(u) {
                DeviceModelDetector.detectApOrClient(u.user, u.hostName, u.profileName, u.macAddress)
            }
            val defaultComment = if (modelInfo.isApOrBridge) {
                "${modelInfo.modelName}: ${u.hostName.ifBlank { u.user }}"
            } else {
                "Whitelisted: ${u.hostName.ifBlank { u.user }}"
            }

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
                title = {
                    Text(
                        text = if (modelInfo.isApOrBridge) "Whitelist ${modelInfo.modelName}" else strings.whitelistAction,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        if (modelInfo.isApOrBridge && modelInfo.imageResId != 0) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                shadowElevation = 3.dp,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .size(76.dp)
                                    .padding(bottom = 10.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(6.dp)) {
                                    Image(
                                        painter = painterResource(id = modelInfo.imageResId),
                                        contentDescription = modelInfo.modelName,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (modelInfo.isApOrBridge)
                                "Allow ${modelInfo.brand} ${modelInfo.modelName} to bypass Hotspot authentication without voucher?"
                            else
                                "Allow \"${u.user}\" (${u.address} / ${u.macAddress}) to use the internet without voucher authentication?",
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                if (modelInfo.isApOrBridge) {
                                    Text(
                                        text = "Model: ${modelInfo.modelName} (${modelInfo.deviceType})",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF059669)
                                    )
                                }
                                Text(
                                    text = "IP: ${u.address}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "MAC: ${u.macAddress}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (u.hostName.isNotBlank()) {
                                    Text(
                                        text = "Hostname: ${u.hostName}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.whitelistDevice(
                                mac = u.macAddress,
                                ip = u.address,
                                comment = defaultComment
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

        // Release Device from Voucher Confirmation Dialog (Ownerless Voucher)
        if (userToRelease != null) {
            val u = userToRelease!!
            AlertDialog(
                onDismissRequest = { userToRelease = null },
                icon = {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.RemoveCircleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                },
                title = { Text(strings.releaseVoucherTitle, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = strings.releaseVoucherMsg,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Voucher: ${u.user}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "MAC: ${u.macAddress}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "IP: ${u.address}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.releaseVoucherFromDevice(u.user, u.macAddress)
                            userToRelease = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(strings.releaseAction, color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { userToRelease = null }) { Text(strings.cancel) }
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
    onReleaseMac: () -> Unit
) {
    val modelInfo = remember(user.macAddress, user.hostName, user.user) {
        DeviceModelDetector.detectApOrClient(
            name = user.user,
            hostName = user.hostName,
            comment = user.profileName,
            macAddress = user.macAddress
        )
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
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
                    // Left image/icon: Real photo if AP, else device/user icon
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (modelInfo.isApOrBridge) Color.White else MaterialTheme.colorScheme.primaryContainer,
                        shadowElevation = if (modelInfo.isApOrBridge) 2.dp else 0.dp,
                        border = if (modelInfo.isApOrBridge) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)) else null,
                        modifier = Modifier.size(if (modelInfo.isApOrBridge) 48.dp else 36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(3.dp)) {
                            if (modelInfo.isApOrBridge && modelInfo.imageResId != 0) {
                                Image(
                                    painter = painterResource(id = modelInfo.imageResId),
                                    contentDescription = modelInfo.modelName,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Icon(
                                    imageVector = if (user.hostName.isNotBlank()) Icons.Default.PhoneAndroid else Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
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
                        if (modelInfo.isApOrBridge) {
                            // AP Brand & Model Badge
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.15f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.SettingsInputAntenna,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = Color(0xFF059669)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "${modelInfo.modelName} • ${modelInfo.deviceType}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF059669)
                                        )
                                    }
                                }
                            }
                        } else if (user.hostName.isNotBlank()) {
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
                    if (modelInfo.isApOrBridge) {
                        // Prominent Whitelist button for AP
                        Button(
                            onClick = onWhitelist,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = strings.whitelistAction,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    } else {
                        // Whitelist button icon for normal client
                        IconButton(onClick = onWhitelist, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.VerifiedUser,
                                contentDescription = strings.whitelistAction,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(18.dp)
                            )
                        }
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
                    // Release Device / Remove MAC from Voucher (Red circle button)
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                        modifier = Modifier.size(32.dp)
                    ) {
                        IconButton(onClick = onReleaseMac, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                Icons.Default.RemoveCircleOutline,
                                contentDescription = strings.releaseAction,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(17.dp)
                            )
                        }
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

            if (user.sessionTimeLeft.isNotBlank() && !user.sessionTimeLeft.equals("none", ignoreCase = true)) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = "⏳ Time Left: ${user.sessionTimeLeft}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
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
    val modelInfo = remember(item.macAddress, item.comment) {
        DeviceModelDetector.detectApOrClient(
            name = item.comment,
            hostName = "",
            comment = item.comment,
            macAddress = item.macAddress
        )
    }

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
                    color = if (modelInfo.isApOrBridge) Color.White else Color(0xFF10B981).copy(alpha = 0.15f),
                    shadowElevation = if (modelInfo.isApOrBridge) 2.dp else 0.dp,
                    border = if (modelInfo.isApOrBridge) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)) else null,
                    modifier = Modifier.size(if (modelInfo.isApOrBridge) 44.dp else 36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(3.dp)) {
                        if (modelInfo.isApOrBridge && modelInfo.imageResId != 0) {
                            Image(
                                painter = painterResource(id = modelInfo.imageResId),
                                contentDescription = modelInfo.modelName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Icon(
                                Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = item.comment.ifBlank { "Whitelisted Device" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    if (modelInfo.isApOrBridge) {
                        Text(
                            text = "📡 ${modelInfo.modelName} • ${modelInfo.deviceType}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF059669)
                        )
                    }
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
