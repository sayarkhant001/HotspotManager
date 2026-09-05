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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.domain.models.UserProfile
import com.example.domain.models.Voucher
import com.example.ui.components.GlassCard
import com.example.utils.VoucherPrinter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VouchersScreen(viewModel: MainViewModel, navController: NavController) {
    val vouchers by viewModel.vouchers.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    var showGenerateDialog by remember { mutableStateOf(false) }
    var showPrintDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedProfileFilter by remember { mutableStateOf<String?>(null) }

    val filteredVouchers = remember(vouchers, searchQuery, selectedProfileFilter) {
        vouchers.filter { v ->
            val matchesSearch = searchQuery.isBlank() ||
                v.code.contains(searchQuery, ignoreCase = true) ||
                v.profileName.contains(searchQuery, ignoreCase = true)
            val matchesProfile = selectedProfileFilter == null || v.profileName == selectedProfileFilter
            matchesSearch && matchesProfile
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Vouchers & Accounts (${vouchers.size})", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showPrintDialog = true }, enabled = vouchers.isNotEmpty()) {
                        Icon(Icons.Default.Print, contentDescription = "Print Vouchers")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showGenerateDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Generate") }
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
                placeholder = { Text("Search vouchers by code or profile...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp, top = 4.dp)
            ) {
                items(filteredVouchers) { voucher ->
                    VoucherItemCard(voucher)
                }
                if (filteredVouchers.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 50.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (vouchers.isEmpty()) "No vouchers generated yet. Tap 'Generate' to create some!" else "No vouchers match your search.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Mikhmon Bulk Generator Dialog
        if (showGenerateDialog) {
            MikhmonGenerateVouchersDialog(
                profiles = profiles,
                onDismiss = { showGenerateDialog = false },
                onGenerate = { profile, qty, length, mode, isAccount, prefix ->
                    viewModel.generateVouchers(profile, qty, length, mode, isAccount, prefix)
                    showGenerateDialog = false
                }
            )
        }

        // Print Format and Style Dialog
        if (showPrintDialog) {
            PrintOptionsDialog(
                onDismiss = { showPrintDialog = false },
                onPrint = { style, format ->
                    VoucherPrinter.printVouchers(navController.context, filteredVouchers, style, format)
                    showPrintDialog = false
                }
            )
        }
    }
}

@Composable
fun VoucherItemCard(voucher: Voucher) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (voucher.isAccount) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (voucher.isAccount) Icons.Default.Person else Icons.Default.ConfirmationNumber,
                        contentDescription = "Voucher",
                        tint = if (voucher.isAccount) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = voucher.code,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    if (voucher.isAccount) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "(Pass: ${voucher.password})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Text(
                    text = "${voucher.profileName} • ${if (voucher.dataLimitMb > 0) "${voucher.dataLimitMb} MB" else "Unlim"} • ${voucher.validityDays} Day(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (voucher.price > 0) {
                    Text(
                        text = "Price: \$${voucher.price}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            IconButton(onClick = {
                clipboardManager.setText(AnnotatedString(voucher.code))
                copied = true
            }) {
                Icon(
                    imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = "Copy Code",
                    tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MikhmonGenerateVouchersDialog(
    profiles: List<UserProfile>,
    onDismiss: () -> Unit,
    onGenerate: (UserProfile, Int, Int, String, Boolean, String) -> Unit
) {
    var selectedProfile by remember { mutableStateOf<UserProfile?>(profiles.firstOrNull()) }
    var isAccountMode by remember { mutableStateOf(false) } // false: Voucher (Code only), true: Account (User + Pass)
    var quantity by remember { mutableStateOf("10") }
    var length by remember { mutableStateOf("8") }
    var prefix by remember { mutableStateOf("") }
    var charMode by remember { mutableStateOf("Alphanumeric (A-Z + 0-9)") }

    val charModes = listOf(
        "Numbers Only",
        "Letters Only (a-z)",
        "Uppercase (A-Z)",
        "Alphanumeric (A-Z + 0-9)",
        "Mixed (a-zA-Z0-9)"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate Vouchers & Accounts", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (profiles.isEmpty()) {
                    Text("No profiles configured. Please create a user profile first.", color = MaterialTheme.colorScheme.error)
                } else {
                    // Mode Selector: Voucher vs Account
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !isAccountMode,
                            onClick = { isAccountMode = false },
                            label = { Text("Voucher (Code)") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = isAccountMode,
                            onClick = { isAccountMode = true },
                            label = { Text("Account (User+Pass)") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Profile Selection
                    Text("Select Profile:", style = MaterialTheme.typography.labelMedium)
                    profiles.forEach { p ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            RadioButton(selected = p == selectedProfile, onClick = { selectedProfile = p })
                            Text(
                                text = "${p.name} (${if (p.rateLimit.isNotBlank()) p.rateLimit else "Unlim"} • ${p.validityDays}D)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = quantity,
                            onValueChange = { quantity = it },
                            label = { Text("Qty (e.g. 10)") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = length,
                            onValueChange = { length = it },
                            label = { Text("Length (>= 8)") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = prefix,
                        onValueChange = { prefix = it },
                        label = { Text("Prefix (Optional, e.g. HS-)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Character Set:", style = MaterialTheme.typography.labelMedium)
                    charModes.forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
                        ) {
                            RadioButton(selected = mode == charMode, onClick = { charMode = mode })
                            Text(mode, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val q = quantity.toIntOrNull() ?: 0
                    val l = (length.toIntOrNull() ?: 8).coerceAtLeast(8)
                    if (selectedProfile != null && q > 0) {
                        onGenerate(selectedProfile!!, q, l, charMode, isAccountMode, prefix)
                    }
                },
                enabled = profiles.isNotEmpty()
            ) {
                Text("Generate & Push")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun PrintOptionsDialog(
    onDismiss: () -> Unit,
    onPrint: (Int, VoucherPrinter.PaperFormat) -> Unit
) {
    var selectedStyle by remember { mutableIntStateOf(1) } // 1: Voucher | Profile, 2: Compact, 3: QR Scannable
    var selectedFormat by remember { mutableStateOf(VoucherPrinter.PaperFormat.A4_PAGE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Print Vouchers", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Paper Format:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedFormat == VoucherPrinter.PaperFormat.THERMAL_58MM,
                        onClick = { selectedFormat = VoucherPrinter.PaperFormat.THERMAL_58MM }
                    )
                    Text("58 mm Thermal Printer")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedFormat == VoucherPrinter.PaperFormat.THERMAL_80MM,
                        onClick = { selectedFormat = VoucherPrinter.PaperFormat.THERMAL_80MM }
                    )
                    Text("80 mm POS Printer")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedFormat == VoucherPrinter.PaperFormat.A4_PAGE,
                        onClick = { selectedFormat = VoucherPrinter.PaperFormat.A4_PAGE }
                    )
                    Text("A4 Sheet Grid (Cut Sheet)")
                }

                Spacer(modifier = Modifier.height(14.dp))
                Divider()
                Spacer(modifier = Modifier.height(14.dp))

                Text("Voucher Style:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedStyle == 1, onClick = { selectedStyle = 1 })
                    Column {
                        Text("Style 1: Voucher | Profile", fontWeight = FontWeight.SemiBold)
                        Text("Standard ticket with login code, limits, and price", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedStyle == 2, onClick = { selectedStyle = 2 })
                    Column {
                        Text("Style 2: One-Line Compact", fontWeight = FontWeight.SemiBold)
                        Text("Dense horizontal strip saving maximum paper", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedStyle == 3, onClick = { selectedStyle = 3 })
                    Column {
                        Text("Style 3: QR Scannable Voucher", fontWeight = FontWeight.SemiBold)
                        Text("Includes QR code for instant camera scan login", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onPrint(selectedStyle, selectedFormat) }) {
                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Print Document")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

