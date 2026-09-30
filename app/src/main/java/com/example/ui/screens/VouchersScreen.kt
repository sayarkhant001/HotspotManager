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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.domain.models.UserProfile
import com.example.domain.models.Voucher
import com.example.ui.components.GlassCard
import com.example.utils.VoucherPrinter

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import com.example.utils.BluetoothPrinterDevice
import com.example.utils.BluetoothThermalPrinter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VouchersScreen(viewModel: MainViewModel, navController: NavController) {
    val vouchers by viewModel.vouchers.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncingVouchers.collectAsStateWithLifecycle()
    val userMsg by viewModel.userMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(userMsg) {
        userMsg?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    LaunchedEffect(Unit) {
        if (vouchers.isEmpty()) {
            viewModel.syncVouchers()
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedProfileFilter by remember { mutableStateOf<String?>(null) }
    var printFilterMode by remember { mutableStateOf("ALL") } // ALL, USED, UNPRINTED, PRINTED
    var selectedVoucherCodes by remember { mutableStateOf(setOf<String>()) }

    var showGenerateDialog by remember { mutableStateOf(false) }
    var showPrintDialog by remember { mutableStateOf(false) }
    var showPrinterSetupDialog by remember { mutableStateOf(false) }
    var vouchersToPrint by remember { mutableStateOf<List<Voucher>>(emptyList()) }
    var voucherToDelete by remember { mutableStateOf<Voucher?>(null) }
    var voucherToRenew by remember { mutableStateOf<Voucher?>(null) }
    var showRenewAllUsedDialog by remember { mutableStateOf(false) }
    var showDeleteAllUsedDialog by remember { mutableStateOf(false) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }

    val usedCount = vouchers.count { it.isUsed }
    val unprintedCount = vouchers.count { !it.isPrinted && !it.isUsed }
    val printedCount = vouchers.count { it.isPrinted && !it.isUsed }

    val filteredVouchers = remember(vouchers, searchQuery, selectedProfileFilter, printFilterMode) {
        vouchers.filter { v ->
            val matchesSearch = searchQuery.isEmpty() ||
                v.code.contains(searchQuery, ignoreCase = true) ||
                v.profileName.contains(searchQuery, ignoreCase = true)
            val matchesProfile = selectedProfileFilter == null || v.profileName == selectedProfileFilter
            val matchesPrint = when (printFilterMode) {
                "USED" -> v.isUsed
                "UNPRINTED" -> !v.isPrinted && !v.isUsed
                "PRINTED" -> v.isPrinted && !v.isUsed
                else -> true
            }
            matchesSearch && matchesProfile && matchesPrint
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Vouchers (${vouchers.size})", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Separate Printer Setup & Test Button as explicitly requested
                    IconButton(onClick = { showPrinterSetupDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Printer Setup & Test")
                    }
                    IconButton(onClick = { viewModel.syncVouchers() }, enabled = !isSyncing) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = "Sync from Router")
                        }
                    }
                    IconButton(
                        onClick = {
                            vouchersToPrint = filteredVouchers
                            showPrintDialog = true
                        },
                        enabled = filteredVouchers.isNotEmpty()
                    ) {
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
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Print status filter chips + Profile filter chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    FilterChip(
                        selected = printFilterMode == "ALL" && selectedProfileFilter == null,
                        onClick = {
                            printFilterMode = "ALL"
                            selectedProfileFilter = null
                        },
                        label = { Text("All (${vouchers.size})") }
                    )
                }
                item {
                    FilterChip(
                        selected = printFilterMode == "USED",
                        onClick = {
                            printFilterMode = if (printFilterMode == "USED") "ALL" else "USED"
                        },
                        label = { Text("Used ($usedCount)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFF9800).copy(alpha = 0.2f),
                            selectedLabelColor = Color(0xFFE65100)
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = printFilterMode == "UNPRINTED",
                        onClick = {
                            printFilterMode = if (printFilterMode == "UNPRINTED") "ALL" else "UNPRINTED"
                        },
                        label = { Text("Unprinted ($unprintedCount)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = printFilterMode == "PRINTED",
                        onClick = {
                            printFilterMode = if (printFilterMode == "PRINTED") "ALL" else "PRINTED"
                        },
                        label = { Text("Printed ($printedCount)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF2E7D32).copy(alpha = 0.2f),
                            selectedLabelColor = Color(0xFF2E7D32)
                        )
                    )
                }

                val activeProfileNames = vouchers.map { it.profileName }.distinct().sorted()
                items(activeProfileNames) { profName ->
                    val count = vouchers.count { it.profileName == profName }
                    FilterChip(
                        selected = selectedProfileFilter == profName,
                        onClick = {
                            selectedProfileFilter = if (selectedProfileFilter == profName) null else profName
                        },
                        label = { Text("$profName ($count)") }
                    )
                }
            }

            // Dedicated Printer Setup & Test Button Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { showPrinterSetupDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Printer Connect & Test Print")
                }
            }

            if (printFilterMode == "USED" && usedCount > 0) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Used Vouchers ($usedCount)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { showRenewAllUsedDialog = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            ) {
                                Icon(Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Renew All", style = MaterialTheme.typography.labelMedium)
                            }
                            Button(
                                onClick = { showDeleteAllUsedDialog = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Delete All", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            // Selective Print Actions Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedVoucherCodes.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = {
                                val selectedList = vouchers.filter { it.code in selectedVoucherCodes }
                                vouchersToPrint = selectedList
                                showPrintDialog = true
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Print (${selectedVoucherCodes.size})")
                        }
                        Button(
                            onClick = { showDeleteSelectedDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete (${selectedVoucherCodes.size})")
                        }
                    }
                    TextButton(onClick = { selectedVoucherCodes = emptySet() }) {
                        Text("Deselect All")
                    }
                } else {
                    FilledTonalButton(
                        onClick = {
                            val unprintedList = vouchers.filter { !it.isPrinted }
                            vouchersToPrint = unprintedList
                            showPrintDialog = true
                        },
                        enabled = unprintedCount > 0,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Print Only Unprinted ($unprintedCount)")
                    }
                    TextButton(
                        onClick = {
                            selectedVoucherCodes = filteredVouchers.map { it.code }.toSet()
                        },
                        enabled = filteredVouchers.isNotEmpty()
                    ) {
                        Text("Select All")
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp, top = 4.dp)
            ) {
                items(filteredVouchers, key = { it.code }) { voucher ->
                    val isSelected = voucher.code in selectedVoucherCodes
                    VoucherItemCard(
                        voucher = voucher,
                        isSelected = isSelected,
                        onToggleSelect = {
                            selectedVoucherCodes = if (isSelected) {
                                selectedVoucherCodes - voucher.code
                            } else {
                                selectedVoucherCodes + voucher.code
                            }
                        },
                        onRenew = { voucherToRenew = voucher },
                        onDelete = { voucherToDelete = voucher }
                    )
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
                                text = if (vouchers.isEmpty()) "No vouchers yet. Tap Sync or 'Generate'!" else "No vouchers match your filter.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Mikhmon Bulk Generator Dialog (Efficient & Error-Free)
        if (showGenerateDialog) {
            MikhmonGenerateVouchersDialog(
                profiles = profiles,
                onDismiss = { showGenerateDialog = false },
                onGenerate = { profile, qty, length, mode, isAccount, prefix, andPrint ->
                    viewModel.generateVouchers(profile, qty, length, mode, isAccount, prefix) { generatedList ->
                        if (andPrint) {
                            vouchersToPrint = generatedList
                            showPrintDialog = true
                        }
                    }
                    showGenerateDialog = false
                }
            )
        }

        // Print Format and Style Dialog
        if (showPrintDialog) {
            PrintOptionsDialog(
                onDismiss = { showPrintDialog = false },
                onPrint = { style, format ->
                    val targetList = if (vouchersToPrint.isNotEmpty()) vouchersToPrint else filteredVouchers
                    if (format == VoucherPrinter.PaperFormat.THERMAL_58MM || format == VoucherPrinter.PaperFormat.THERMAL_80MM) {
                        val savedPrinter = BluetoothThermalPrinter.getSavedPrinter(context)
                        val paperWidth = if (format == VoucherPrinter.PaperFormat.THERMAL_80MM)
                            BluetoothThermalPrinter.PaperWidth.WIDTH_80MM
                        else
                            BluetoothThermalPrinter.PaperWidth.WIDTH_58MM

                        if (savedPrinter != null) {
                            scope.launch {
                                Toast.makeText(context, "Printing ${targetList.size} vouchers to ${savedPrinter.name}...", Toast.LENGTH_SHORT).show()
                                val res = BluetoothThermalPrinter.printVouchersDirect(
                                    context = context,
                                    vouchers = targetList,
                                    deviceAddress = savedPrinter.address,
                                    paperWidth = paperWidth
                                )
                                if (res.isSuccess) {
                                    viewModel.markVouchersAsPrinted(targetList.map { it.code })
                                    Toast.makeText(context, "✓ Printed to ${savedPrinter.name} successfully!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "✗ Bluetooth Print Error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    showPrinterSetupDialog = true
                                }
                            }
                        } else {
                            Toast.makeText(context, "Please select your Bluetooth Thermal Printer", Toast.LENGTH_SHORT).show()
                            showPrinterSetupDialog = true
                        }
                    } else {
                        VoucherPrinter.printVouchers(navController.context, targetList, style, format)
                        viewModel.markVouchersAsPrinted(targetList.map { it.code })
                    }
                    selectedVoucherCodes = emptySet()
                    showPrintDialog = false
                }
            )
        }

        // Separate Dedicated Printer Setup & Test Dialog as requested
        if (showPrinterSetupDialog) {
            PrinterSetupAndTestDialog(
                profiles = profiles,
                onDismiss = { showPrinterSetupDialog = false },
                onRunTest = { format ->
                    VoucherPrinter.printTestReceipt(navController.context, format, profiles)
                    showPrinterSetupDialog = false
                }
            )
        }

        // Voucher Delete Confirmation Dialog
        if (voucherToDelete != null) {
            val v = voucherToDelete!!
            AlertDialog(
                onDismissRequest = { voucherToDelete = null },
                title = { Text("Delete Voucher") },
                text = { Text("Are you sure you want to delete voucher code '${v.code}' from RouterOS and this app?") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteVoucher(v)
                            voucherToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { voucherToDelete = null }) { Text("Cancel") }
                }
            )
        }

        // Voucher Renew Confirmation Dialog
        if (voucherToRenew != null) {
            val v = voucherToRenew!!
            AlertDialog(
                onDismissRequest = { voucherToRenew = null },
                title = { Text("Renew Voucher") },
                text = { Text("Renew voucher code '${v.code}'? This resets its data usage and marks it active again on the router.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.renewVoucher(v)
                            voucherToRenew = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Text("Renew")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { voucherToRenew = null }) { Text("Cancel") }
                }
            )
        }

        // Renew All Used Vouchers Dialog
        if (showRenewAllUsedDialog) {
            AlertDialog(
                onDismissRequest = { showRenewAllUsedDialog = false },
                title = { Text("Renew All Used Vouchers") },
                text = { Text("Are you sure you want to renew all $usedCount used vouchers on the router?") },
                confirmButton = {
                    Button(
                        onClick = {
                            vouchers.filter { it.isUsed }.forEach { viewModel.renewVoucher(it) }
                            showRenewAllUsedDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Text("Renew All")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenewAllUsedDialog = false }) { Text("Cancel") }
                }
            )
        }

        // Delete All Used Vouchers Dialog
        if (showDeleteAllUsedDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteAllUsedDialog = false },
                title = { Text("Delete All Used Vouchers") },
                text = { Text("Permanently delete all $usedCount used vouchers from RouterOS and this app?") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteUsedVouchers()
                            showDeleteAllUsedDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete All Used")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAllUsedDialog = false }) { Text("Cancel") }
                }
            )
        }

        // Delete Selected Vouchers Dialog
        if (showDeleteSelectedDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteSelectedDialog = false },
                title = { Text("Delete Selected Vouchers") },
                text = { Text("Are you sure you want to permanently delete ${selectedVoucherCodes.size} selected vouchers from RouterOS and this app?") },
                confirmButton = {
                    Button(
                        onClick = {
                            val toDelete = selectedVoucherCodes.toSet()
                            selectedVoucherCodes = emptySet()
                            showDeleteSelectedDialog = false
                            viewModel.deleteSelectedVouchers(toDelete)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete Selected")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteSelectedDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun VoucherItemCard(
    voucher: Voucher,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onRenew: (() -> Unit)? = null,
    onDelete: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (voucher.isAccount) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (voucher.isAccount) Icons.Default.Person else Icons.Default.ConfirmationNumber,
                        contentDescription = "Voucher",
                        tint = if (voucher.isAccount) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = voucher.code,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    val badgeColor = when {
                        voucher.isUsed -> Color(0xFFE65100)
                        voucher.isPrinted -> Color(0xFF2E7D32)
                        else -> MaterialTheme.colorScheme.outline
                    }
                    val badgeText = when {
                        voucher.isUsed -> "USED"
                        voucher.isPrinted -> "PRINTED"
                        else -> "UNPRINTED"
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = badgeColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                        )
                    }
                    if (voucher.isAccount) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(P: ${voucher.password})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Text(
                    text = "${voucher.profileName} • ${if (voucher.dataLimitMb > 0) "${voucher.dataLimitMb} MB" else "Unlim"} • ${voucher.validityDays}D",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (voucher.comment.startsWith("EXP:", ignoreCase = true)) {
                    val expInfo = voucher.comment.removePrefix("EXP:").trim()
                    Text(
                        text = "Expires: $expInfo",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF1565C0),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (voucher.price > 0) {
                    Text(
                        text = "Price: ${"%,d".format(java.util.Locale.US, voucher.price.toLong())} Ks",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (voucher.isUsed && onRenew != null) {
                IconButton(onClick = onRenew) {
                    Icon(
                        imageVector = Icons.Default.Autorenew,
                        contentDescription = "Renew Voucher",
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(20.dp)
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
                    tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete Voucher",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
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
    onGenerate: (UserProfile, Int, Int, String, Boolean, String, Boolean) -> Unit
) {
    var selectedProfile by remember { mutableStateOf<UserProfile?>(profiles.firstOrNull()) }
    var isAccountMode by remember { mutableStateOf(false) } // false: Voucher, true: Account
    var quantity by remember { mutableStateOf("10") }
    var length by remember { mutableStateOf("8") }
    var prefix by remember { mutableStateOf("") }

    val numberStyles = listOf(
        "Hyphenated Numbers (1819-9733)",
        "Numbers Only (18199733)",
        "Alphanumeric (AG3HG6Q4)",
        "Hyphenated Alphanumeric (AG3H-G6Q4)",
        "Uppercase Letters (ABCDEFGH)",
        "Lowercase Letters (abcdefgh)",
        "Mixed Letters & Numbers (aB3kE8q9)"
    )
    var selectedNumberStyle by remember { mutableStateOf(numberStyles[0]) }

    var profileExpanded by remember { mutableStateOf(false) }
    var styleExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate Vouchers", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (profiles.isEmpty()) {
                    Text("No profiles configured. Please create a user profile first.", color = MaterialTheme.colorScheme.error)
                } else {
                    // Mode Selector: Voucher vs Account (clean without "(User+ Pass)")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !isAccountMode,
                            onClick = { isAccountMode = false },
                            label = {
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Text("Voucher", fontWeight = if (!isAccountMode) FontWeight.Bold else FontWeight.Normal)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = isAccountMode,
                            onClick = { isAccountMode = true },
                            label = {
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Text("Account", fontWeight = if (isAccountMode) FontWeight.Bold else FontWeight.Normal)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Profile Dropdown Selector
                    ExposedDropdownMenuBox(
                        expanded = profileExpanded,
                        onExpandedChange = { profileExpanded = !profileExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val profileText = selectedProfile?.let { p ->
                            val speed = if (p.rateLimit.isNotBlank()) p.rateLimit else "Unlimited"
                            val price = if (p.price > 0) " • %,d Ks".format(p.price.toInt()) else ""
                            "${p.name} ($speed • ${p.validityDays}D$price)"
                        } ?: "Select Profile"

                        OutlinedTextField(
                            value = profileText,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Profile") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = profileExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = profileExpanded,
                            onDismissRequest = { profileExpanded = false }
                        ) {
                            profiles.forEach { p ->
                                val priceFormatted = if (p.price > 0) " • %,d Ks".format(p.price.toInt()) else ""
                                val speedInfo = if (p.rateLimit.isNotBlank()) p.rateLimit else "Unlimited"
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(p.name, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "$speedInfo • ${p.validityDays} Days$priceFormatted",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedProfile = p
                                        profileExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Number / Code Style Dropdown Selector
                    ExposedDropdownMenuBox(
                        expanded = styleExpanded,
                        onExpandedChange = { styleExpanded = !styleExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedNumberStyle,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Number / Code Style") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = styleExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = styleExpanded,
                            onDismissRequest = { styleExpanded = false }
                        ) {
                            numberStyles.forEach { style ->
                                DropdownMenuItem(
                                    text = { Text(style, style = MaterialTheme.typography.bodyMedium) },
                                    onClick = {
                                        selectedNumberStyle = style
                                        styleExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Prefix with Clear & Quick Chips
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = prefix,
                            onValueChange = { prefix = it },
                            label = { Text("Prefix (Optional)") },
                            placeholder = { Text("e.g. HS-, AG-") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            trailingIcon = if (prefix.isNotEmpty()) {
                                {
                                    IconButton(onClick = { prefix = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear Prefix", modifier = Modifier.size(18.dp))
                                    }
                                }
                            } else null
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Quick:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            listOf("None", "HS-", "AG-", "WIFI-").forEach { chipText ->
                                val isSelected = (chipText == "None" && prefix.isEmpty()) || (chipText != "None" && prefix == chipText)
                                SuggestionChip(
                                    onClick = { prefix = if (chipText == "None") "" else chipText },
                                    label = { Text(chipText, style = MaterialTheme.typography.labelSmall) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                    }

                    // Quantity and Length
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = quantity,
                            onValueChange = { quantity = it },
                            label = { Text("Qty (e.g. 10)") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = length,
                            onValueChange = { length = it },
                            label = { Text("Length (>= 4)") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val q = quantity.toIntOrNull() ?: 0
                        val l = (length.toIntOrNull() ?: 8).coerceAtLeast(4)
                        if (selectedProfile != null && q > 0) {
                            onGenerate(selectedProfile!!, q, l, selectedNumberStyle, isAccountMode, prefix, false)
                        }
                    },
                    enabled = profiles.isNotEmpty()
                ) {
                    Text("Generate")
                }
                Button(
                    onClick = {
                        val q = quantity.toIntOrNull() ?: 0
                        val l = (length.toIntOrNull() ?: 8).coerceAtLeast(4)
                        if (selectedProfile != null && q > 0) {
                            onGenerate(selectedProfile!!, q, l, selectedNumberStyle, isAccountMode, prefix, true)
                        }
                    },
                    enabled = profiles.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Generate & Print")
                }
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
                HorizontalDivider()
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
                        Text("Style 2: Ultra-Compact (58mm × 12mm)", fontWeight = FontWeight.SemiBold)
                        Text("Voucher & profile only. Prints 5 columns per A4 sheet (100+ per page)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
fun PrinterSetupAndTestDialog(
    profiles: List<UserProfile>,
    onDismiss: () -> Unit,
    onRunTest: (VoucherPrinter.PaperFormat) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pairedPrinters by remember { mutableStateOf(BluetoothThermalPrinter.getPairedPrinters(context)) }
    var savedPrinter by remember { mutableStateOf(BluetoothThermalPrinter.getSavedPrinter(context)) }
    var selectedWidth by remember { mutableStateOf(BluetoothThermalPrinter.getSavedPaperWidth(context)) }
    var isPrintingTest by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Print, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Bluetooth Thermal Printer", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "1. Select Paired Bluetooth Printer",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Direct Bluetooth connection (SPP) with zero third-party print service dependencies.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (pairedPrinters.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "No paired Bluetooth devices found.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "Please turn on Bluetooth and pair your thermal printer in Android settings.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        pairedPrinters.forEach { device ->
                            val isSelected = savedPrinter?.address == device.address
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        BluetoothThermalPrinter.savePrinter(context, device)
                                        savedPrinter = device
                                        Toast.makeText(context, "Selected: ${device.name}", Toast.LENGTH_SHORT).show()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            BluetoothThermalPrinter.savePrinter(context, device)
                                            savedPrinter = device
                                            Toast.makeText(context, "Selected: ${device.name}", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(device.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                        Text(device.address, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            pairedPrinters = BluetoothThermalPrinter.getPairedPrinters(context)
                            savedPrinter = BluetoothThermalPrinter.getSavedPrinter(context)
                            Toast.makeText(context, "Refreshed devices (${pairedPrinters.size})", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Refresh", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = {
                            try {
                                val btIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                context.startActivity(btIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Open Settings > Bluetooth to pair", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pair Printer", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "2. Paper Width & Direct Test Print",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedWidth == BluetoothThermalPrinter.PaperWidth.WIDTH_58MM,
                        onClick = {
                            selectedWidth = BluetoothThermalPrinter.PaperWidth.WIDTH_58MM
                            BluetoothThermalPrinter.savePaperWidth(context, selectedWidth)
                        }
                    )
                    Text("58 mm Portable Thermal (32 cols)")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedWidth == BluetoothThermalPrinter.PaperWidth.WIDTH_80MM,
                        onClick = {
                            selectedWidth = BluetoothThermalPrinter.PaperWidth.WIDTH_80MM
                            BluetoothThermalPrinter.savePaperWidth(context, selectedWidth)
                        }
                    )
                    Text("80 mm Desktop POS Thermal (48 cols)")
                }

                if (savedPrinter != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Active: ${savedPrinter!!.name} (${savedPrinter!!.address})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (savedPrinter == null) {
                        Toast.makeText(context, "Please select a paired Bluetooth printer above first", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isPrintingTest = true
                    scope.launch {
                        Toast.makeText(context, "Sending test ticket to ${savedPrinter!!.name}...", Toast.LENGTH_SHORT).show()
                        val res = BluetoothThermalPrinter.printTestReceiptDirect(
                            context = context,
                            profiles = profiles,
                            deviceAddress = savedPrinter!!.address,
                            paperWidth = selectedWidth
                        )
                        isPrintingTest = false
                        if (res.isSuccess) {
                            Toast.makeText(context, "✓ Test ticket printed successfully!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "✗ Print failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                enabled = !isPrintingTest && savedPrinter != null,
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isPrintingTest) "Printing..." else "Instant Test Print")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

