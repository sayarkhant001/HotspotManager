package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.domain.models.UserProfile
import com.example.ui.components.GlassCard

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(viewModel: MainViewModel, navController: NavController) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val voucherCounts by viewModel.profileVoucherCounts.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var showCleanupDialog by remember { mutableStateOf(false) }
    var profileToDelete by remember { mutableStateOf<UserProfile?>(null) }
    var profileToEdit by remember { mutableStateOf<UserProfile?>(null) }

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
                title = { Text("User Profiles (${profiles.size})", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (zeroCountProfiles.isNotEmpty()) {
                        IconButton(onClick = { showCleanupDialog = true }) {
                            Icon(
                                Icons.Default.DeleteSweep, 
                                contentDescription = "Clean Up Unused Profiles",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    IconButton(onClick = { 
                        viewModel.syncProfiles() 
                        viewModel.syncVouchers()
                    }) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync Router Profiles")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Profile")
            }
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
                            "No profiles yet. Click Sync or + to add one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (showAddDialog) {
            AddProfileDialog(
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
                title = { Text("Clean Up Unused Profiles") },
                text = { Text("Are you sure you want to remove ${zeroCountProfiles.size} profile(s) with zero vouchers from RouterOS and the database?") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.removeZeroVoucherProfiles()
                            showCleanupDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Remove All Unused")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCleanupDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (profileToDelete != null) {
            val target = profileToDelete!!
            val count = voucherCounts[target.name] ?: 0
            val msg = if (count > 0) {
                "Profile '${target.name}' currently has $count vouchers assigned to it. Deleting this profile will also remove all $count vouchers from RouterOS and this device. Are you sure?"
            } else {
                "Are you sure you want to delete profile '${target.name}' from RouterOS?"
            }
            AlertDialog(
                onDismissRequest = { profileToDelete = null },
                title = { Text("Delete Profile") },
                text = { Text(msg) },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteProfile(target.name)
                            profileToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { profileToDelete = null }) { Text("Cancel") }
                }
            )
        }

        if (profileToEdit != null) {
            val target = profileToEdit!!
            EditProfileDialog(
                profile = target,
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
                            text = if (voucherCount > 0) "$voucherCount vouchers" else "0 vouchers (Unused)",
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
                            contentDescription = "Edit Profile",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (voucherCount == 0 && !profile.name.equals("default", ignoreCase = true)) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete Profile",
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
                Text("Rate Limit: ${if (profile.rateLimit.isNotBlank()) profile.rateLimit else "Unlimited"}", style = MaterialTheme.typography.bodySmall)
                Text("Shared: ${profile.sharedUsers} user(s)", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Validity: ${profile.validityDays} Day(s)", style = MaterialTheme.typography.bodySmall)
                if (profile.dataLimitMb > 0) {
                    Text("Quota: ${profile.dataLimitMb} MB", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Quota: Unlimited", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProfileDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, Int, Int, Int, Double, Double, Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    
    // Speed / Rate Limit
    var speedVal by remember { mutableStateOf("10") }
    var speedUnit by remember { mutableStateOf("Mbps") } // kbps, Mbps, Gbps
    var speedExpanded by remember { mutableStateOf(false) }

    // Validity / Duration
    var validityVal by remember { mutableStateOf("30") }
    var validityUnit by remember { mutableStateOf("Days") } // Minutes, Hours, Days, Months
    var validityExpanded by remember { mutableStateOf(false) }

    // Data Limit
    var dataVal by remember { mutableStateOf("1") }
    var dataUnit by remember { mutableStateOf("GB") } // MB, GB, Unlimited
    var dataExpanded by remember { mutableStateOf(false) }

    var sharedUsers by remember { mutableStateOf("1") }
    var price by remember { mutableStateOf("1000") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Hotspot Profile", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name (e.g. 1GB_1H)") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp)
                )

                // Speed Limit with Unit selector
                Text("Speed Limit (Rate Limit):", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = speedVal,
                        onValueChange = { speedVal = it },
                        label = { Text("Speed") },
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                    ExposedDropdownMenuBox(
                        expanded = speedExpanded,
                        onExpandedChange = { speedExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = speedUnit,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Unit") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = speedExpanded) },
                            modifier = Modifier.menuAnchor(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = speedExpanded,
                            onDismissRequest = { speedExpanded = false }
                        ) {
                            listOf("kbps", "Mbps", "Gbps").forEach { u ->
                                DropdownMenuItem(
                                    text = { Text(u) },
                                    onClick = {
                                        speedUnit = u
                                        speedExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Validity with Unit selector
                Text("Validity (Duration):", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = validityVal,
                        onValueChange = { validityVal = it },
                        label = { Text("Duration") },
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                    ExposedDropdownMenuBox(
                        expanded = validityExpanded,
                        onExpandedChange = { validityExpanded = it },
                        modifier = Modifier.weight(1.2f)
                    ) {
                        OutlinedTextField(
                            value = validityUnit,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Unit") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = validityExpanded) },
                            modifier = Modifier.menuAnchor(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = validityExpanded,
                            onDismissRequest = { validityExpanded = false }
                        ) {
                            listOf("Minutes", "Hours", "Days", "Months").forEach { u ->
                                DropdownMenuItem(
                                    text = { Text(u) },
                                    onClick = {
                                        validityUnit = u
                                        validityExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Data Quota with Unit selector
                Text("Data Limit:", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (dataUnit != "Unlimited") {
                        OutlinedTextField(
                            value = dataVal,
                            onValueChange = { dataVal = it },
                            label = { Text("Quota") },
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                    }
                    ExposedDropdownMenuBox(
                        expanded = dataExpanded,
                        onExpandedChange = { dataExpanded = it },
                        modifier = Modifier.weight(if (dataUnit == "Unlimited") 2f else 1f)
                    ) {
                        OutlinedTextField(
                            value = dataUnit,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Unit") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dataExpanded) },
                            modifier = Modifier.menuAnchor(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = dataExpanded,
                            onDismissRequest = { dataExpanded = false }
                        ) {
                            listOf("MB", "GB", "Unlimited").forEach { u ->
                                DropdownMenuItem(
                                    text = { Text(u) },
                                    onClick = {
                                        dataUnit = u
                                        dataExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sharedUsers,
                        onValueChange = { sharedUsers = it },
                        label = { Text("Shared Users") },
                        modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text("Price (Ks, e.g. 10,000)") },
                        modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) {
                    // Compute rate limit string
                    val speedNum = speedVal.trim().toIntOrNull() ?: 10
                    val suffix = when (speedUnit) {
                        "kbps" -> "k"
                        "Gbps" -> "G"
                        else -> "M" // Mbps
                    }
                    val rateLimitStr = "${speedNum}$suffix/${speedNum}$suffix"

                    // Compute duration in minutes
                    val durVal = validityVal.trim().toIntOrNull() ?: 1
                    val durationMinutes = when (validityUnit) {
                        "Minutes" -> durVal
                        "Hours" -> durVal * 60
                        "Months" -> durVal * 30 * 24 * 60
                        else -> durVal * 24 * 60 // Days
                    }

                    // Compute validity days
                    val validityDays = when (validityUnit) {
                        "Minutes" -> 1
                        "Hours" -> 1
                        "Months" -> durVal * 30
                        else -> durVal // Days
                    }

                    // Compute data limit in MB
                    val dataMb = when (dataUnit) {
                        "Unlimited" -> 0
                        "GB" -> (dataVal.trim().toIntOrNull() ?: 1) * 1024
                        else -> dataVal.trim().toIntOrNull() ?: 0 // MB
                    }

                    val priceVal = price.trim().replace(",", "").toDoubleOrNull() ?: 0.0

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
            }) { Text("Save to Router") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileDialog(
    profile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (UserProfile) -> Unit
) {
    var name by remember { mutableStateOf(profile.name) }
    
    // Parse existing rate limit e.g. "10M/10M" or "5M/5M"
    val parts = profile.rateLimit.split("/")
    val dlPart = parts.getOrNull(0)?.trim() ?: ""
    val initialSpeedVal = when {
        dlPart.endsWith("M", ignoreCase = true) -> dlPart.dropLast(1)
        dlPart.endsWith("k", ignoreCase = true) -> dlPart.dropLast(1)
        dlPart.endsWith("G", ignoreCase = true) -> dlPart.dropLast(1)
        else -> dlPart.ifBlank { "10" }
    }
    val initialSpeedUnit = when {
        dlPart.endsWith("k", ignoreCase = true) -> "kbps"
        dlPart.endsWith("G", ignoreCase = true) -> "Gbps"
        else -> "Mbps"
    }

    var speedVal by remember { mutableStateOf(initialSpeedVal) }
    var speedUnit by remember { mutableStateOf(initialSpeedUnit) }
    var speedExpanded by remember { mutableStateOf(false) }

    var validityVal by remember { mutableStateOf(if (profile.validityDays > 0) profile.validityDays.toString() else "30") }
    var validityUnit by remember { mutableStateOf("Days") }
    var validityExpanded by remember { mutableStateOf(false) }

    val initialDataVal = when {
        profile.dataLimitMb >= 1024 -> (profile.dataLimitMb / 1024).toString()
        profile.dataLimitMb > 0 -> profile.dataLimitMb.toString()
        else -> "1"
    }
    val initialDataUnit = when {
        profile.dataLimitMb == 0 -> "Unlimited"
        profile.dataLimitMb >= 1024 -> "GB"
        else -> "MB"
    }

    var dataVal by remember { mutableStateOf(initialDataVal) }
    var dataUnit by remember { mutableStateOf(initialDataUnit) }
    var dataExpanded by remember { mutableStateOf(false) }

    var sharedUsers by remember { mutableStateOf(profile.sharedUsers.toString()) }
    var price by remember { mutableStateOf("%,d".format(java.util.Locale.US, profile.price.toLong())) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Profile: ${profile.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (!profile.name.equals("default", ignoreCase = true)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Profile Name") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Speed Limit
                Text("Speed Limit (Rate Limit):", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = speedVal,
                        onValueChange = { speedVal = it },
                        label = { Text("Speed") },
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                    ExposedDropdownMenuBox(
                        expanded = speedExpanded,
                        onExpandedChange = { speedExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = speedUnit,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Unit") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = speedExpanded) },
                            modifier = Modifier.menuAnchor(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = speedExpanded,
                            onDismissRequest = { speedExpanded = false }
                        ) {
                            listOf("kbps", "Mbps", "Gbps").forEach { u ->
                                DropdownMenuItem(
                                    text = { Text(u) },
                                    onClick = {
                                        speedUnit = u
                                        speedExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Validity
                Text("Validity (Duration):", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = validityVal,
                        onValueChange = { validityVal = it },
                        label = { Text("Duration") },
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                    ExposedDropdownMenuBox(
                        expanded = validityExpanded,
                        onExpandedChange = { validityExpanded = it },
                        modifier = Modifier.weight(1.2f)
                    ) {
                        OutlinedTextField(
                            value = validityUnit,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Unit") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = validityExpanded) },
                            modifier = Modifier.menuAnchor(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = validityExpanded,
                            onDismissRequest = { validityExpanded = false }
                        ) {
                            listOf("Minutes", "Hours", "Days", "Months").forEach { u ->
                                DropdownMenuItem(
                                    text = { Text(u) },
                                    onClick = {
                                        validityUnit = u
                                        validityExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Data Quota
                Text("Data Limit:", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (dataUnit != "Unlimited") {
                        OutlinedTextField(
                            value = dataVal,
                            onValueChange = { dataVal = it },
                            label = { Text("Quota") },
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                    }
                    ExposedDropdownMenuBox(
                        expanded = dataExpanded,
                        onExpandedChange = { dataExpanded = it },
                        modifier = Modifier.weight(if (dataUnit == "Unlimited") 2f else 1f)
                    ) {
                        OutlinedTextField(
                            value = dataUnit,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Unit") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dataExpanded) },
                            modifier = Modifier.menuAnchor(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = dataExpanded,
                            onDismissRequest = { dataExpanded = false }
                        ) {
                            listOf("MB", "GB", "Unlimited").forEach { u ->
                                DropdownMenuItem(
                                    text = { Text(u) },
                                    onClick = {
                                        dataUnit = u
                                        dataExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sharedUsers,
                        onValueChange = { sharedUsers = it },
                        label = { Text("Shared Users") },
                        modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text("Price (Ks)") },
                        modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rateString = when (speedUnit) {
                        "kbps" -> "${speedVal}k/${speedVal}k"
                        "Gbps" -> "${speedVal}G/${speedVal}G"
                        else -> "${speedVal}M/${speedVal}M"
                    }
                    val speedNum = when (speedUnit) {
                        "kbps" -> (speedVal.toIntOrNull() ?: 5000) / 1000
                        "Gbps" -> (speedVal.toIntOrNull() ?: 1) * 1000
                        else -> speedVal.toIntOrNull() ?: 10
                    }
                    val vNum = validityVal.toIntOrNull() ?: 30
                    val vDays = when (validityUnit) {
                        "Minutes" -> 1
                        "Hours" -> 1
                        "Months" -> vNum * 30
                        else -> vNum
                    }
                    val durMinutes = when (validityUnit) {
                        "Minutes" -> vNum
                        "Hours" -> vNum * 60
                        "Months" -> vNum * 30 * 24 * 60
                        else -> vNum * 24 * 60
                    }
                    val dNum = dataVal.toIntOrNull() ?: 0
                    val totalMb = when (dataUnit) {
                        "Unlimited" -> 0
                        "GB" -> dNum * 1024
                        else -> dNum
                    }
                    val cleanPrice = price.replace(",", "").replace(".", "").trim().toDoubleOrNull() ?: profile.price

                    val updated = profile.copy(
                        name = name.trim().ifBlank { profile.name },
                        sharedUsers = sharedUsers.toIntOrNull() ?: profile.sharedUsers,
                        rateLimit = rateString,
                        downloadLimitMbps = speedNum,
                        uploadLimitMbps = speedNum,
                        dataLimitMb = totalMb,
                        durationMinutes = durMinutes,
                        price = cleanPrice,
                        sellingPrice = cleanPrice,
                        validityDays = vDays
                    )
                    onSave(updated)
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

