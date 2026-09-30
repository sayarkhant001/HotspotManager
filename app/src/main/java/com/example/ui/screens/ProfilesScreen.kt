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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (voucherCount > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = if (voucherCount > 0) "$voucherCount ${strings.vouchersCountSuffix}" else "0 ${strings.vouchersCountSuffix} (${strings.statusUnused})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (voucherCount > 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = if (profile.price > 0) "${"%,d".format(java.util.Locale.US, profile.price.toLong())} Ks" else "Free",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = strings.edit,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (voucherCount == 0 && !profile.name.equals("default", ignoreCase = true)) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = strings.delete,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${strings.rateLimit}: ${if (profile.rateLimit.isNotBlank()) profile.rateLimit else "Unlimited"}", style = MaterialTheme.typography.bodySmall)
                Text("${strings.sharedUsers}: ${profile.sharedUsers}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${strings.validity}: ${profile.validityDays} Day(s)", style = MaterialTheme.typography.bodySmall)
                if (profile.dataLimitMb > 0) {
                    Text("${strings.dataLimitMb}: ${profile.dataLimitMb} MB", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("${strings.dataLimitMb}: Unlimited", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProfileDialog(
    strings: com.example.utils.AppStrings,
    onDismiss: () -> Unit,
    onAdd: (String, String, Int, Int, Int, Double, Double, Int) -> Unit
) {
    var name by remember { mutableStateOf("1D_1000K") }
    var speedVal by remember { mutableStateOf("10") }
    var speedUnit by remember { mutableStateOf("Mbps") }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.GroupAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(strings.addProfileTitle, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                    Text("Package & Rate Limiter Studio", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. LIVE PROFILE PREVIEW CARD
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
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
                                if (name.isNotBlank()) name else "New Profile",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val priceLong = price.trim().toLongOrNull() ?: 0L
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = if (priceLong > 0) "%,d Ks".format(priceLong) else "FREE",
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
                        "အသင့်သုံး ပုံစံများ (Quick Presets):",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
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
                                    speedUnit = p.speedUnit
                                    validityVal = p.valVal
                                    validityUnit = p.valUnit
                                    isUnlimitedData = p.dataMb == 0
                                    dataVal = if (p.dataMb == 0) "0" else (p.dataMb / 1024).toString()
                                    price = p.price.toString()
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
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
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${strings.rateLimit}:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("Symmetric Tx/Rx", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
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
                                Text("${speedVal.ifBlank { "0" }}M/${speedVal.ifBlank { "0" }}M", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("2", "5", "10", "15", "20", "50").forEach { spd ->
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
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Text("${strings.validity}:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = validityVal,
                            onValueChange = { input -> if (input.all { it.isDigit() }) validityVal = input },
                            label = { Text("Duration") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, autoCorrectEnabled = false),
                            modifier = Modifier.weight(1.2f),
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

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        (if (validityUnit == "Hours") listOf("1", "2", "6", "12", "24") else listOf("1", "3", "7", "14", "30")).forEach { dur ->
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
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("${strings.dataLimitMb}:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(if (isUnlimitedData) "Unlimited Data ♾️" else "Capped Quota in GB 📦", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isUnlimitedData,
                            onCheckedChange = { isUnlimitedData = it },
                            modifier = Modifier.height(26.dp)
                        )
                    }

                    if (!isUnlimitedData) {
                        OutlinedTextField(
                            value = dataVal,
                            onValueChange = { input -> if (input.all { it.isDigit() }) dataVal = input },
                            label = { Text("Quota in GB (e.g. 5)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("1", "2", "3", "5", "10", "30", "50").forEach { qGb ->
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
                        label = { Text("Users") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, autoCorrectEnabled = false),
                        modifier = Modifier.weight(0.8f),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                }

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
        },
        confirmButton = {
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
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("${strings.editProfileTitle}: ${profile.name}", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                    Text("Modify Router Profile & Limits", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
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
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Text("${strings.rateLimit}:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = speedVal,
                        onValueChange = { input -> if (input.all { it.isDigit() }) speedVal = input },
                        label = { Text("Speed (Mbps)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("2", "5", "10", "15", "20", "50").forEach { spd ->
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
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Text("${strings.validity} (Days):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = validityVal,
                        onValueChange = { input -> if (input.all { it.isDigit() }) validityVal = input },
                        label = { Text("Days") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                }

                // Data Quota Limit
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${strings.dataLimitMb}:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (isUnlimitedData) "Unlimited ♾️" else "Capped 📦", style = MaterialTheme.typography.labelSmall)
                            Switch(
                                checked = isUnlimitedData,
                                onCheckedChange = { isUnlimitedData = it },
                                modifier = Modifier.height(24.dp).padding(start = 4.dp)
                            )
                        }
                    }
                    if (!isUnlimitedData) {
                        OutlinedTextField(
                            value = dataVal,
                            onValueChange = { input -> if (input.all { it.isDigit() }) dataVal = input },
                            label = { Text("Quota in GB (e.g. 5)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                    }
                }

                // Price and Shared Users
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
                        label = { Text("Users") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, autoCorrectEnabled = false),
                        modifier = Modifier.weight(0.8f),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
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
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
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
