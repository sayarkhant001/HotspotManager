package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.domain.models.UserProfile
import com.example.ui.components.GlassCard
import com.example.utils.AppLanguage
import com.example.utils.LanguageManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(viewModel: MainViewModel, navController: NavController) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val voucherCounts by viewModel.profileVoucherCounts.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var showCleanupDialog by remember { mutableStateOf(false) }
    var profileToDelete by remember { mutableStateOf<UserProfile?>(null) }
    var profileToEdit by remember { mutableStateOf<UserProfile?>(null) }

    val currentLang by LanguageManager.currentLanguage
    val strings = LanguageManager.strings
    val context = LocalContext.current

    val zeroCountProfiles = profiles.filter {
        !it.name.equals("default", ignoreCase = true) && (voucherCounts[it.name] ?: 0) == 0
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val userMsg by viewModel.userMessage.collectAsStateWithLifecycle()

    LaunchedEffect(userMsg) {
        userMsg?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.syncProfiles()
        viewModel.syncVouchers()
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("${strings.profilesHeader} (${profiles.size})", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                },
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
                    if (zeroCountProfiles.isNotEmpty()) {
                        IconButton(onClick = { showCleanupDialog = true }) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = strings.cleanUpUnusedProfiles,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    IconButton(onClick = {
                        viewModel.syncProfiles()
                        viewModel.syncVouchers()
                    }) {
                        Icon(Icons.Default.Sync, contentDescription = strings.syncRouterProfiles)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(strings.add) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(profiles) { profile ->
                val vCount = voucherCounts[profile.name] ?: 0
                ProfileItemCard(
                    profile = profile,
                    voucherCount = vCount,
                    strings = strings,
                    onEdit = { profileToEdit = profile },
                    onDelete = { profileToDelete = profile }
                )
            }
            if (profiles.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            strings.noProfilesFound,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (showAddDialog) {
            AddProfileDialog(
                strings = strings,
                onDismiss = { showAddDialog = false },
                onAdd = { name, rateLimit, sharedUsers, dataMb, durMin, price, sellPrice, validity ->
                    viewModel.addProfile(name, rateLimit, sharedUsers, dataMb, durMin, price, sellPrice, validity)
                    showAddDialog = false
                }
            )
        }

        if (showCleanupDialog) {
            AlertDialog(
                onDismissRequest = { showCleanupDialog = false },
                title = { Text(strings.cleanupConfirmTitle) },
                text = { Text("${zeroCountProfiles.size} ${strings.cleanupConfirmMsg}") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.removeZeroVoucherProfiles()
                            showCleanupDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(strings.cleanUpAction)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCleanupDialog = false }) { Text(strings.cancel) }
                }
            )
        }

        if (profileToDelete != null) {
            val target = profileToDelete!!
            AlertDialog(
                onDismissRequest = { profileToDelete = null },
                title = { Text(strings.deleteProfileTitle) },
                text = { Text("\"${target.name}\"\n\n${strings.deleteProfileConfirmMsg}") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteProfile(target.name)
                            profileToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(strings.delete)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { profileToDelete = null }) { Text(strings.cancel) }
                }
            )
        }

        if (profileToEdit != null) {
            val target = profileToEdit!!
            EditProfileDialog(
                profile = target,
                strings = strings,
                onDismiss = { profileToEdit = null },
                onSave = { updated ->
                    viewModel.updateProfile(target.name, updated)
                    profileToEdit = null
                }
            )
        }
    }
}

@Composable
fun ProfileItemCard(
    profile: UserProfile,
    voucherCount: Int,
    strings: com.example.utils.AppStrings,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Name & Vouchers Badge on left, Price & Action icons on right
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
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Router,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (voucherCount > 0) Color(0xFF2E7D32).copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = if (voucherCount > 0) "$voucherCount ${strings.vouchersCountSuffix}" else "0 ${strings.vouchersCountSuffix} (${strings.statusUnused})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (voucherCount > 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = if (profile.price > 0) "${"%,d".format(java.util.Locale.US, profile.price.toLong())} Ks" else "Free",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = strings.edit,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (voucherCount == 0 && !profile.name.equals("default", ignoreCase = true)) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = strings.delete,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            Spacer(modifier = Modifier.height(10.dp))

            // 2x2 Specs Grid - Clean, modern, immune to text wrapping collisions!
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Speed pill
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(strings.rateLimit, style = MaterialTheme.typography.labelSmall, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = if (profile.rateLimit.isNotBlank()) profile.rateLimit else "Unlimited",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Shared Users pill
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Devices, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(strings.sharedUsers, style = MaterialTheme.typography.labelSmall, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${profile.sharedUsers} ${if (profile.sharedUsers > 1) "Devices" else "Device"}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Validity pill
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(strings.validity, style = MaterialTheme.typography.labelSmall, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${profile.validityDays} Day(s)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Data limit pill
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DataUsage, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFE65100))
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(strings.dataLimitMb, style = MaterialTheme.typography.labelSmall, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val quotaText = if (profile.dataLimitMb > 0) {
                                if (profile.dataLimitMb >= 1024) "${profile.dataLimitMb / 1024} GB" else "${profile.dataLimitMb} MB"
                            } else "Unlimited"
                            Text(
                                text = quotaText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddProfileDialog(
    strings: com.example.utils.AppStrings,
    onDismiss: () -> Unit,
    onAdd: (String, String, Int, Int, Int, Double, Double, Int) -> Unit
) {
    var name by remember { mutableStateOf("1D_1000K") }
    var speedVal by remember { mutableStateOf("10") }
    var validityVal by remember { mutableStateOf("1") }
    var validityUnit by remember { mutableStateOf("Days") }
    var dataVal by remember { mutableStateOf("0") }
    var isUnlimitedData by remember { mutableStateOf(true) }
    var sharedUsers by remember { mutableStateOf("1") }
    var price by remember { mutableStateOf("1000") }

    // Quick Presets matching Myanmar ISP Hotspot standards
    val presets = listOf(
        PresetItem("1H_Trial", "3", "Mbps", "1", "Hours", 0, 200, "1H Trial • 200K"),
        PresetItem("1D_1GB", "5", "Mbps", "1", "Days", 1024, 500, "1D / 1GB • 500K"),
        PresetItem("3D_3GB", "5", "Mbps", "3", "Days", 3072, 1000, "3D / 3GB • 1,000K"),
        PresetItem("7D_7GB", "10", "Mbps", "7", "Days", 7168, 3000, "7D / 7GB • 3,000K"),
        PresetItem("30D_30GB", "15", "Mbps", "30", "Days", 30720, 10000, "30D / 30GB • 10,000K"),
        PresetItem("30D_Unlim", "20", "Mbps", "30", "Days", 0, 25000, "30D Unlim • 25,000K")
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f)
                .imePadding(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header (Pinned)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.GroupAdd,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                strings.addProfileTitle,
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "Package & Rate Limiter Studio",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Scrollable Form Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. LIVE PROFILE PREVIEW CARD
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (name.isNotBlank()) name else "New Profile",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(Modifier.width(8.dp))
                                val priceLong = price.trim().toLongOrNull() ?: 0L
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text = if (priceLong > 0) "${"%,d".format(java.util.Locale.US, priceLong)} Ks" else "FREE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // Badges Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = "⚡ ${speedVal.ifBlank { "10" }} Mbps",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = if (isUnlimitedData) "📦 Unlimited" else "📦 ${dataVal} GB",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = "⏳ $validityVal $validityUnit",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 2. QUICK PRESET CAROUSEL
                    Column {
                        Text(
                            text = "အသင့်သုံး ပုံစံများ (Quick Presets):",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(6.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(presets) { p ->
                                val isSelected = name == p.name
                                Surface(
                                    onClick = {
                                        name = p.name
                                        speedVal = p.speedVal
                                        validityVal = p.valVal
                                        validityUnit = p.valUnit
                                        isUnlimitedData = p.dataMb == 0
                                        dataVal = if (p.dataMb == 0) "0" else (p.dataMb / 1024).toString()
                                        price = p.price.toString()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                    )
                                ) {
                                    Text(
                                        text = p.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 3. PROFILE NAME
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(strings.profileName) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        trailingIcon = if (name.isNotEmpty()) {
                            {
                                IconButton(onClick = { name = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                }
                            }
                        } else null
                    )

                    // 4. SPEED LIMIT WITH PRESET CHIPS (RouterOS rx/tx)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(strings.rateLimit, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text("Symmetric Tx/Rx", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = speedVal,
                                onValueChange = { input -> if (input.all { it.isDigit() }) speedVal = input },
                                label = { Text("Speed (Mbps)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, autoCorrectEnabled = false),
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.weight(0.8f).height(54.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${speedVal.ifBlank { "0" }}M/${speedVal.ifBlank { "0" }}M",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(listOf("2", "5", "10", "15", "20", "30", "50")) { spd ->
                                FilterChip(
                                    selected = speedVal == spd,
                                    onClick = { speedVal = spd },
                                    label = { Text("${spd}M", fontSize = 11.sp, fontWeight = if (speedVal == spd) FontWeight.Bold else FontWeight.Normal) },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                    }

                    // 5. VALIDITY DURATION (Hours vs Days)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(strings.validity, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                        Spacer(Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = validityVal,
                                onValueChange = { input -> if (input.all { it.isDigit() }) validityVal = input },
                                label = { Text("Duration") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, autoCorrectEnabled = false),
                                modifier = Modifier.weight(1.1f),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )
                            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilterChip(
                                    selected = validityUnit == "Hours",
                                    onClick = { validityUnit = "Hours" },
                                    label = { Text("Hours", fontSize = 11.sp, fontWeight = if (validityUnit == "Hours") FontWeight.Bold else FontWeight.Normal) },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = validityUnit == "Days",
                                    onClick = { validityUnit = "Days" },
                                    label = { Text("Days", fontSize = 11.sp, fontWeight = if (validityUnit == "Days") FontWeight.Bold else FontWeight.Normal) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        val durationPresets = if (validityUnit == "Hours") listOf("1", "2", "6", "12", "24") else listOf("1", "3", "7", "14", "30")
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(durationPresets) { dur ->
                                FilterChip(
                                    selected = validityVal == dur,
                                    onClick = { validityVal = dur },
                                    label = { Text(dur, fontSize = 11.sp) },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                    }

                    // 6. DATA QUOTA LIMIT
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(strings.dataLimitMb, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (isUnlimitedData) "Unlimited Data ♾️" else "Capped Quota in GB 📦",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isUnlimitedData,
                                onCheckedChange = { isUnlimitedData = it },
                                modifier = Modifier.height(28.dp)
                            )
                        }

                        if (!isUnlimitedData) {
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = dataVal,
                                onValueChange = { input -> if (input.all { it.isDigit() }) dataVal = input },
                                label = { Text("Quota in GB (e.g. 5)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, autoCorrectEnabled = false),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )
                            Spacer(Modifier.height(8.dp))
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(listOf("1", "2", "3", "5", "10", "20", "30", "50")) { qGb ->
                                    FilterChip(
                                        selected = dataVal == qGb,
                                        onClick = { dataVal = qGb },
                                        label = { Text("${qGb}GB", fontSize = 11.sp) },
                                        modifier = Modifier.height(28.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 7. PRICE & SHARED USERS
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = price,
                                onValueChange = { input -> if (input.all { it.isDigit() }) price = input },
                                label = { Text(strings.originalPrice + " (Ks)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, autoCorrectEnabled = false),
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = sharedUsers,
                                onValueChange = { input -> if (input.all { it.isDigit() }) sharedUsers = input },
                                label = { Text("Devices") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, autoCorrectEnabled = false),
                                modifier = Modifier.weight(0.8f),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("+Price:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            listOf(500, 1000, 3000, 5000).forEach { addP ->
                                SuggestionChip(
                                    onClick = {
                                        val current = price.toIntOrNull() ?: 0
                                        price = (current + addP).toString()
                                    },
                                    label = { Text("+$addP", style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Action Buttons Bar (Pinned at bottom)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(strings.cancel)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                val speedNum = speedVal.trim().toIntOrNull() ?: 10
                                val rateLimitStr = "${speedNum}M/${speedNum}M"
                                val durVal = validityVal.trim().toIntOrNull() ?: 1
                                val durationMinutes = if (validityUnit == "Hours") durVal * 60 else durVal * 24 * 60
                                val validityDays = if (validityUnit == "Hours") 1 else durVal
                                val dataMb = if (isUnlimitedData) 0 else (dataVal.trim().toIntOrNull() ?: 1) * 1024
                                val priceVal = price.trim().toDoubleOrNull() ?: 0.0

                                onAdd(
                                    name.trim(),
                                    rateLimitStr,
                                    sharedUsers.toIntOrNull() ?: 1,
                                    dataMb,
                                    durationMinutes,
                                    priceVal,
                                    priceVal,
                                    validityDays
                                )
                            }
                        },
                        enabled = name.isNotBlank(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.save, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun EditProfileDialog(
    profile: UserProfile,
    strings: com.example.utils.AppStrings,
    onDismiss: () -> Unit,
    onSave: (UserProfile) -> Unit
) {
    var name by remember { mutableStateOf(profile.name) }
    val parts = profile.rateLimit.split("/")
    val dlPart = parts.getOrNull(0)?.trim() ?: ""
    val initialSpeedVal = dlPart.filter { it.isDigit() }.ifBlank { "10" }

    var speedVal by remember { mutableStateOf(initialSpeedVal) }
    var validityVal by remember { mutableStateOf(if (profile.validityDays > 0) profile.validityDays.toString() else "1") }
    var isUnlimitedData by remember { mutableStateOf(profile.dataLimitMb <= 0) }
    var dataVal by remember { mutableStateOf(if (profile.dataLimitMb > 0) (profile.dataLimitMb / 1024).toString() else "5") }
    var sharedUsers by remember { mutableStateOf(profile.sharedUsers.toString()) }
    var price by remember { mutableStateOf(profile.price.toLong().toString()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .imePadding(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header (Pinned)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "${strings.editProfileTitle}: ${profile.name}",
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text("Modify Router Profile & Limits", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Scrollable Form Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Live preview badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "⚡ ${speedVal}M • ${if (isUnlimitedData) "Unlim" else "${dataVal}GB"} • ${validityVal}D",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (!profile.name.equals("default", ignoreCase = true)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(strings.profileName) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                    }

                    // Speed Limit
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(strings.rateLimit, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = speedVal,
                            onValueChange = { input -> if (input.all { it.isDigit() }) speedVal = input },
                            label = { Text("Speed (Mbps)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(listOf("2", "5", "10", "15", "20", "30", "50")) { spd ->
                                FilterChip(
                                    selected = speedVal == spd,
                                    onClick = { speedVal = spd },
                                    label = { Text("${spd}M", fontSize = 11.sp) },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                    }

                    // Validity Days
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text("${strings.validity} (Days):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = validityVal,
                            onValueChange = { input -> if (input.all { it.isDigit() }) validityVal = input },
                            label = { Text("Days") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(listOf("1", "3", "7", "14", "30")) { dur ->
                                FilterChip(
                                    selected = validityVal == dur,
                                    onClick = { validityVal = dur },
                                    label = { Text("$dur Days", fontSize = 11.sp) },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                    }

                    // Data Quota Limit
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(strings.dataLimitMb, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (isUnlimitedData) "Unlimited ♾️" else "Capped 📦", style = MaterialTheme.typography.labelSmall)
                                Switch(
                                    checked = isUnlimitedData,
                                    onCheckedChange = { isUnlimitedData = it },
                                    modifier = Modifier.height(26.dp).padding(start = 4.dp)
                                )
                            }
                        }
                        if (!isUnlimitedData) {
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = dataVal,
                                onValueChange = { input -> if (input.all { it.isDigit() }) dataVal = input },
                                label = { Text("Quota in GB (e.g. 5)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, autoCorrectEnabled = false),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )
                            Spacer(Modifier.height(8.dp))
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(listOf("1", "2", "3", "5", "10", "20", "30", "50")) { qGb ->
                                    FilterChip(
                                        selected = dataVal == qGb,
                                        onClick = { dataVal = qGb },
                                        label = { Text("${qGb}GB", fontSize = 11.sp) },
                                        modifier = Modifier.height(28.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Price and Shared Users
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = price,
                                onValueChange = { input -> if (input.all { it.isDigit() }) price = input },
                                label = { Text(strings.originalPrice + " (Ks)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, autoCorrectEnabled = false),
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = sharedUsers,
                                onValueChange = { input -> if (input.all { it.isDigit() }) sharedUsers = input },
                                label = { Text("Devices") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, autoCorrectEnabled = false),
                                modifier = Modifier.weight(0.8f),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Action Buttons Bar (Pinned at bottom)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(strings.cancel)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val speedNum = speedVal.trim().toIntOrNull() ?: 10
                            val rateLimitStr = "${speedNum}M/${speedNum}M"
                            val vDays = validityVal.trim().toIntOrNull() ?: 1
                            val dataMb = if (isUnlimitedData) 0 else (dataVal.trim().toIntOrNull() ?: 1) * 1024
                            val priceVal = price.trim().toDoubleOrNull() ?: 0.0

                            onSave(
                                profile.copy(
                                    name = name.trim(),
                                    rateLimit = rateLimitStr,
                                    sharedUsers = sharedUsers.toIntOrNull() ?: 1,
                                    dataLimitMb = dataMb,
                                    validityDays = vDays,
                                    durationMinutes = vDays * 24 * 60,
                                    price = priceVal,
                                    sellingPrice = priceVal
                                )
                            )
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.save, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

data class PresetItem(
    val name: String,
    val speedVal: String,
    val speedUnit: String,
    val valVal: String,
    val valUnit: String,
    val dataMb: Int,
    val price: Int,
    val label: String
)
