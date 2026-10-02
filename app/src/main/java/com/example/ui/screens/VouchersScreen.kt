package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.domain.models.UserProfile
import com.example.domain.models.Voucher
import com.example.ui.components.GlassCard
import com.example.utils.BluetoothPrinterDevice
import com.example.utils.BluetoothThermalPrinter
import com.example.utils.LanguageManager
import com.example.utils.VoucherPrinter
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
    val strings = LanguageManager.strings
    val currentLang by LanguageManager.currentLanguage

    LaunchedEffect(userMsg) {
        userMsg?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.syncVouchers()
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
                title = { Text("${strings.vouchersHeader} (${vouchers.size})", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                },
                actions = {
                    // Language Switcher Chip
                    Surface(
                        onClick = { LanguageManager.toggleLanguage(context) },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(
                            text = "${currentLang.flag} ${currentLang.displayName}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                    // Separate Printer Setup & Test Button as explicitly requested
                    IconButton(onClick = { showPrinterSetupDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = strings.thermalPrinterSetupTitle)
                    }
                    IconButton(onClick = { viewModel.syncVouchers() }, enabled = !isSyncing) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = strings.refresh)
                        }
                    }
                    IconButton(
                        onClick = {
                            vouchersToPrint = filteredVouchers
                            showPrintDialog = true
                        },
                        enabled = filteredVouchers.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Print, contentDescription = strings.printAction)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showGenerateDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(strings.generateVouchers) }
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
                placeholder = { Text(strings.searchVouchersHint) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = strings.search) },
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
                        label = { Text("${strings.filterAll} (${vouchers.size})") }
                    )
                }
                item {
                    FilterChip(
                        selected = printFilterMode == "USED",
                        onClick = {
                            printFilterMode = if (printFilterMode == "USED") "ALL" else "USED"
                        },
                        label = { Text("${strings.filterUsed} ($usedCount)") },
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
                        label = { Text("${strings.filterUnprinted} ($unprintedCount)") },
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
                        label = { Text("${strings.filterPrinted} ($printedCount)") },
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
                    Text(strings.printerSetupBtn)
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
                            text = "${strings.usedVouchersBanner} ($usedCount)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { showRenewAllUsedDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            ) {
                                Icon(Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(strings.renewAll, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            }
                            Button(
                                onClick = { showDeleteAllUsedDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(strings.deleteAll, style = MaterialTheme.typography.labelMedium, maxLines = 1)
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
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
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
                            Text("${strings.printAction} (${selectedVoucherCodes.size})", maxLines = 1)
                        }
                        Button(
                            onClick = { showDeleteSelectedDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${strings.deleteAction} (${selectedVoucherCodes.size})", maxLines = 1)
                        }
                    }
                    TextButton(onClick = { selectedVoucherCodes = emptySet() }) {
                        Text(strings.deselectAll, maxLines = 1)
                    }
                } else {
                    FilledTonalButton(
                        onClick = {
                            val unprintedList = vouchers.filter { !it.isPrinted }
                            vouchersToPrint = unprintedList
                            showPrintDialog = true
                        },
                        enabled = unprintedCount > 0,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${strings.printOnlyUnprinted} ($unprintedCount)",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            selectedVoucherCodes = filteredVouchers.map { it.code }.toSet()
                        },
                        enabled = filteredVouchers.isNotEmpty()
                    ) {
                        Text(strings.selectAll, maxLines = 1)
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
            val targetList = if (vouchersToPrint.isNotEmpty()) vouchersToPrint else filteredVouchers
            val detectedLoginUrl by viewModel.hotspotLoginUrl.collectAsStateWithLifecycle()
            val detectedSsid by viewModel.hotspotSsid.collectAsStateWithLifecycle()
            PrintOptionsDialog(
                sampleVoucher = targetList.firstOrNull(),
                defaultSsid = detectedSsid,
                defaultLoginUrl = detectedLoginUrl,
                onDismiss = { showPrintDialog = false },
                onPrint = { style, format, autoCut, ssid, url ->
                    if (format == VoucherPrinter.PaperFormat.THERMAL_58MM || format == VoucherPrinter.PaperFormat.THERMAL_80MM) {
                        val activePrinter = BluetoothThermalPrinter.getActivePrinter(context) ?: BluetoothThermalPrinter.getSavedPrinter(context)
                        val paperWidth = if (format == VoucherPrinter.PaperFormat.THERMAL_80MM)
                            BluetoothThermalPrinter.PaperWidth.WIDTH_80MM
                        else
                            BluetoothThermalPrinter.PaperWidth.WIDTH_58MM

                        if (activePrinter != null && activePrinter.address.isNotBlank()) {
                            scope.launch {
                                val printTargetDesc = if (activePrinter.isConnected) "${activePrinter.name} (Connected)" else activePrinter.name
                                Toast.makeText(context, "Printing ${targetList.size} vouchers to $printTargetDesc...", Toast.LENGTH_SHORT).show()
                                val res = BluetoothThermalPrinter.printVouchersDirect(
                                    context = context,
                                    vouchers = targetList,
                                    deviceAddress = activePrinter.address,
                                    paperWidth = paperWidth,
                                    style = style,
                                    autoCutEachVoucher = autoCut,
                                    routerSsid = ssid,
                                    loginUrl = url
                                )
                                if (res.isSuccess) {
                                    viewModel.markVouchersAsPrinted(targetList.map { it.code })
                                    Toast.makeText(context, "✓ Printed to ${activePrinter.name} successfully!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "✗ Bluetooth Print Error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    showPrinterSetupDialog = true
                                }
                            }
                        } else {
                            Toast.makeText(context, "Please connect or select your Bluetooth Thermal Printer", Toast.LENGTH_SHORT).show()
                            showPrinterSetupDialog = true
                        }
                    } else {
                        VoucherPrinter.printVouchers(navController.context, targetList, style, format, routerSsid = ssid, loginUrl = url)
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
                title = { Text(strings.deleteAction) },
                text = { Text("Are you sure you want to delete voucher '${v.code}'?") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteVoucher(v)
                            voucherToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(strings.delete)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { voucherToDelete = null }) { Text(strings.cancel) }
                }
            )
        }

        // Voucher Renew Confirmation Dialog
        if (voucherToRenew != null) {
            val v = voucherToRenew!!
            AlertDialog(
                onDismissRequest = { voucherToRenew = null },
                title = { Text(strings.renewAction) },
                text = { Text("Renew voucher '${v.code}'?") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.renewVoucher(v)
                            voucherToRenew = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Text(strings.renewAction)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { voucherToRenew = null }) { Text(strings.cancel) }
                }
            )
        }

        // Renew All Used Vouchers Dialog
        if (showRenewAllUsedDialog) {
            AlertDialog(
                onDismissRequest = { showRenewAllUsedDialog = false },
                title = { Text(strings.renewAll) },
                text = { Text("Renew all $usedCount used vouchers on the router?") },
                confirmButton = {
                    Button(
                        onClick = {
                            vouchers.filter { it.isUsed }.forEach { viewModel.renewVoucher(it) }
                            showRenewAllUsedDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Text(strings.renewAll)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenewAllUsedDialog = false }) { Text(strings.cancel) }
                }
            )
        }

        // Delete All Used Vouchers Dialog
        if (showDeleteAllUsedDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteAllUsedDialog = false },
                title = { Text(strings.deleteAllUsedConfirmTitle) },
                text = { Text(strings.deleteAllUsedConfirmMsg) },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteUsedVouchers()
                            showDeleteAllUsedDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(strings.deleteAll)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAllUsedDialog = false }) { Text(strings.cancel) }
                }
            )
        }

        // Delete Selected Vouchers Dialog
        if (showDeleteSelectedDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteSelectedDialog = false },
                title = { Text(strings.deleteSelectedConfirmTitle) },
                text = { Text(strings.deleteSelectedConfirmMsg) },
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
                        Text(strings.delete)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteSelectedDialog = false }) { Text(strings.cancel) }
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
    val strings = LanguageManager.strings

    GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (voucher.isAccount) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (voucher.isAccount) Icons.Default.Person else Icons.Default.ConfirmationNumber,
                        contentDescription = "Voucher",
                        tint = if (voucher.isAccount) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = voucher.code,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(4.dp))

                    val badgeColor = when {
                        voucher.isUsed -> Color(0xFFE65100)
                        voucher.isPrinted -> Color(0xFF2E7D32)
                        else -> MaterialTheme.colorScheme.outline
                    }
                    val badgeText = when {
                        voucher.isUsed -> strings.statusUsed
                        voucher.isPrinted -> strings.statusPrinted
                        else -> strings.statusUnprinted
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = badgeColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    if (voucher.isAccount) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "(P: ${voucher.password})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                val quotaDisplay = when {
                    voucher.dataLimitMb <= 0 -> "Unlim"
                    voucher.dataLimitMb >= 1024 && voucher.dataLimitMb % 1024 == 0 -> "${voucher.dataLimitMb / 1024} GB"
                    voucher.dataLimitMb >= 1024 -> "${"%.1f".format(voucher.dataLimitMb / 1024.0)} GB"
                    else -> "${voucher.dataLimitMb} MB"
                }
                val validityDisplay = when {
                    voucher.durationMinutes in 1..59 -> "${voucher.durationMinutes} Mins"
                    voucher.durationMinutes in 60..1439 && voucher.durationMinutes % 60 == 0 -> "${voucher.durationMinutes / 60} Hour(s)"
                    voucher.durationMinutes in 60..1439 -> "${voucher.durationMinutes} Mins"
                    voucher.durationMinutes >= 1440 && voucher.durationMinutes % 1440 == 0 -> "${voucher.durationMinutes / 1440} Day(s)"
                    voucher.durationMinutes >= 1440 -> "${voucher.durationMinutes} Mins"
                    voucher.validityDays > 0 -> "${voucher.validityDays} Day(s)"
                    else -> "Unlimited"
                }
                Text(
                    text = "${voucher.profileName} • $quotaDisplay • $validityDisplay",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (voucher.comment.startsWith("EXP:", ignoreCase = true)) {
                    val expInfo = voucher.comment.removePrefix("EXP:").trim()
                    Text(
                        text = "Expires: $expInfo",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF1565C0),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (voucher.price > 0) {
                    Text(
                        text = "Price: ${"%,d".format(java.util.Locale.US, voucher.price.toLong())} Ks",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (voucher.isUsed) {
                    val totalBytes = voucher.bytesIn + voucher.bytesOut
                    val usedMb = (totalBytes / (1024 * 1024))
                    val limitMb = voucher.dataLimitMb
                    val progress = if (limitMb > 0) (usedMb.toFloat() / limitMb.toFloat()).coerceIn(0f, 1f) else 1f

                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFE65100).copy(alpha = 0.08f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE65100).copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (limitMb > 0) "Data Used: $usedMb MB / $limitMb MB" else "Data Used: $usedMb MB",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE65100)
                                )
                                if (voucher.uptime.isNotBlank() && voucher.uptime != "0s") {
                                    Text(
                                        text = "⏱ ${voucher.uptime}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (limitMb > 0) {
                                Spacer(modifier = Modifier.height(3.dp))
                                LinearProgressIndicator(
                                    progress = progress,
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = Color(0xFFE65100),
                                    trackColor = Color(0xFFE65100).copy(alpha = 0.2f)
                                )
                            }
                        }
                    }
                }
            }

            if (voucher.isUsed && onRenew != null) {
                IconButton(onClick = onRenew, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Default.Autorenew,
                        contentDescription = strings.renewAction,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            IconButton(onClick = {
                clipboardManager.setText(AnnotatedString(voucher.code))
                copied = true
            }, modifier = Modifier.size(30.dp)) {
                Icon(
                    imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = "Copy Code",
                    tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp)
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = strings.deleteAction,
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(17.dp)
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
    val strings = LanguageManager.strings

    val numberStyles = listOf(
        "Numbers Only",
        "Alphabet Only",
        "Numbers + Alphabet"
    )
    var selectedNumberStyle by remember { mutableStateOf(numberStyles[0]) }
    var profileExpanded by remember { mutableStateOf(false) }

    // Live preview code computation
    val previewCode = remember(prefix, length, selectedNumberStyle) {
        val len = (length.toIntOrNull() ?: 8).coerceIn(4, 16)
        val sampleDigits = "8492015372189420"
        val sampleAlpha = "ABCDEFGHJKLMNPQR"
        val sampleAlphanumeric = "A3B8K9M2PX7Y4W6Q"
        val raw = when (selectedNumberStyle) {
            "Numbers Only" -> sampleDigits.take(len)
            "Alphabet Only" -> sampleAlpha.take(len)
            "Numbers + Alphabet" -> sampleAlphanumeric.take(len)
            else -> sampleDigits.take(len)
        }
        prefix + raw
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .imePadding(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Pinned Header
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
                                    Icons.Default.ConfirmationNumber,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                strings.generateVouchers,
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "Mikhmon POS Engine",
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
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (profiles.isEmpty()) {
                        Text("No profiles configured. Please create a user profile first.", color = MaterialTheme.colorScheme.error)
                    } else {
                        // 1. LIVE TICKET PREVIEW CARD (Matches physical 58mm 2-compartment box)
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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ReceiptLong,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "TICKET LIVE PREVIEW (58mm Box)",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Text(
                                            text = selectedProfile?.name ?: "Default",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                // 2-Compartment Box Render
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Left Compartment: Big Bold Code
                                        Column(
                                            modifier = Modifier
                                                .weight(1.05f)
                                                .padding(8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = if (isAccountMode) "ACCOUNT CODE" else "VOUCHER CODE",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 9.sp
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = previewCode,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Black,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (isAccountMode) {
                                                Text(
                                                    text = "P: " + previewCode.reversed().take(previewCode.length.coerceAtMost(6)),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            } else {
                                                Text(
                                                    text = "(User = Pass)",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 9.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        // Vertical Box Divider
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .width(1.dp)
                                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                                        )

                                        // Right Compartment: Profile & Limits
                                        Column(
                                            modifier = Modifier
                                                .weight(0.95f)
                                                .padding(8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = selectedProfile?.name ?: "Profile",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            val quotaStr = when {
                                                (selectedProfile?.dataLimitMb ?: 0) <= 0 -> "Unlim"
                                                (selectedProfile?.dataLimitMb ?: 0) >= 1024 && (selectedProfile?.dataLimitMb ?: 0) % 1024 == 0 -> "${(selectedProfile?.dataLimitMb ?: 0) / 1024}GB"
                                                (selectedProfile?.dataLimitMb ?: 0) >= 1024 -> "${"%.1f".format((selectedProfile?.dataLimitMb ?: 0) / 1024.0)}GB"
                                                else -> "${selectedProfile?.dataLimitMb}MB"
                                            }
                                            val validityStr = when {
                                                (selectedProfile?.durationMinutes ?: 0) in 1..59 -> "${selectedProfile?.durationMinutes}M"
                                                (selectedProfile?.durationMinutes ?: 0) in 60..1439 && (selectedProfile?.durationMinutes ?: 0) % 60 == 0 -> "${(selectedProfile?.durationMinutes ?: 0) / 60}H"
                                                (selectedProfile?.durationMinutes ?: 0) in 60..1439 -> "${selectedProfile?.durationMinutes}M"
                                                (selectedProfile?.durationMinutes ?: 0) >= 1440 && (selectedProfile?.durationMinutes ?: 0) % 1440 == 0 -> "${(selectedProfile?.durationMinutes ?: 0) / 1440}D"
                                                (selectedProfile?.durationMinutes ?: 0) >= 1440 -> "${selectedProfile?.durationMinutes}M"
                                                (selectedProfile?.validityDays ?: 0) > 0 -> "${selectedProfile?.validityDays}D"
                                                else -> "1D"
                                            }
                                            Text(
                                                text = "$quotaStr • $validityStr",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            val price = selectedProfile?.price ?: 0.0
                                            Text(
                                                text = if (price > 0) "%,d Ks".format(price.toInt()) else "FREE",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Black,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 2. SEGMENTED MODE SELECTOR (Voucher vs Account)
                        Column {
                            Text(
                                text = "Generation Mode (ထုတ်ယူမည့် ပုံစံ):",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Voucher Option (DEFAULT)
                                Surface(
                                    onClick = { isAccountMode = false },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (!isAccountMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        if (!isAccountMode) 2.dp else 1.dp,
                                        if (!isAccountMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.ConfirmationNumber,
                                                contentDescription = null,
                                                modifier = Modifier.size(17.dp),
                                                tint = if (!isAccountMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(Modifier.width(5.dp))
                                            Text(
                                                text = "Voucher",
                                                fontWeight = FontWeight.ExtraBold,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = if (!isAccountMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (!isAccountMode) {
                                                Spacer(Modifier.width(4.dp))
                                                Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 12.sp)
                                            }
                                        }
                                        Spacer(Modifier.height(3.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (!isAccountMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
                                        ) {
                                            Text(
                                                text = "Username = Password",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (!isAccountMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = "Single Code (ကုတ်တစ်ခုတည်း)",
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    }
                                }

                                // Account Option
                                Surface(
                                    onClick = { isAccountMode = true },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isAccountMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        if (isAccountMode) 2.dp else 1.dp,
                                        if (isAccountMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Person,
                                                contentDescription = null,
                                                modifier = Modifier.size(17.dp),
                                                tint = if (isAccountMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(Modifier.width(5.dp))
                                            Text(
                                                text = "Account",
                                                fontWeight = FontWeight.ExtraBold,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = if (isAccountMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (isAccountMode) {
                                                Spacer(Modifier.width(4.dp))
                                                Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 12.sp)
                                            }
                                        }
                                        Spacer(Modifier.height(3.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (isAccountMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
                                        ) {
                                            Text(
                                                text = "Username + Password",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isAccountMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = "Separate (အမည် + စကားဝှက်)",
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }

                        // 3. PROFILE / PACKAGE SELECTION (Clean Cards)
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Profile / Package (ပရိုဖိုင်):",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (profiles.size > 3) {
                                    TextButton(
                                        onClick = { profileExpanded = true },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                    ) {
                                        Text(
                                            text = if (profileExpanded) "Close" else "All (${profiles.size})...",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))

                            if (profiles.size <= 3) {
                                // Equal-width cards filling row evenly (Fixes unstyled look)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    profiles.forEach { p ->
                                        val isSelected = selectedProfile?.name == p.name
                                        val quotaStr = when {
                                            p.dataLimitMb <= 0 -> ""
                                            p.dataLimitMb >= 1024 && p.dataLimitMb % 1024 == 0 -> "${p.dataLimitMb / 1024}GB"
                                            p.dataLimitMb >= 1024 -> "${"%.1f".format(p.dataLimitMb / 1024.0)}GB"
                                            else -> "${p.dataLimitMb}MB"
                                        }
                                        val valStr = when {
                                            p.durationMinutes in 1..59 -> "${p.durationMinutes}M"
                                            p.durationMinutes in 60..1439 && p.durationMinutes % 60 == 0 -> "${p.durationMinutes / 60}H"
                                            p.durationMinutes in 60..1439 -> "${p.durationMinutes}M"
                                            p.durationMinutes >= 1440 && p.durationMinutes % 1440 == 0 -> "${p.durationMinutes / 1440}D"
                                            p.validityDays > 0 -> "${p.validityDays}D"
                                            else -> ""
                                        }
                                        val specSummary = listOf(quotaStr, valStr).filter { it.isNotEmpty() }.joinToString(" • ").ifEmpty { p.rateLimit.substringBefore("/") }

                                        Surface(
                                            onClick = { selectedProfile = p },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                            border = androidx.compose.foundation.BorderStroke(
                                                if (isSelected) 2.dp else 1.dp,
                                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                            ),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center
                                                ) {
                                                    Text(
                                                        text = p.name,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (isSelected) {
                                                        Spacer(Modifier.width(3.dp))
                                                        Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 11.sp)
                                                    }
                                                }
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    text = specSummary,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 9.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                                Spacer(Modifier.height(2.dp))
                                                val priceFmt = if (p.price > 0) "%,d Ks".format(p.price.toInt()) else "Free"
                                                Text(
                                                    text = priceFmt,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Scrollable Row with fixed-width cards for 4+ profiles
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(profiles) { p ->
                                        val isSelected = selectedProfile?.name == p.name
                                        val quotaStr = when {
                                            p.dataLimitMb <= 0 -> ""
                                            p.dataLimitMb >= 1024 && p.dataLimitMb % 1024 == 0 -> "${p.dataLimitMb / 1024}GB"
                                            p.dataLimitMb >= 1024 -> "${"%.1f".format(p.dataLimitMb / 1024.0)}GB"
                                            else -> "${p.dataLimitMb}MB"
                                        }
                                        val valStr = when {
                                            p.durationMinutes in 1..59 -> "${p.durationMinutes}M"
                                            p.durationMinutes in 60..1439 && p.durationMinutes % 60 == 0 -> "${p.durationMinutes / 60}H"
                                            p.durationMinutes in 60..1439 -> "${p.durationMinutes}M"
                                            p.durationMinutes >= 1440 && p.durationMinutes % 1440 == 0 -> "${p.durationMinutes / 1440}D"
                                            p.validityDays > 0 -> "${p.validityDays}D"
                                            else -> ""
                                        }
                                        val specSummary = listOf(quotaStr, valStr).filter { it.isNotEmpty() }.joinToString(" • ").ifEmpty { p.rateLimit.substringBefore("/") }

                                        Surface(
                                            onClick = { selectedProfile = p },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                            border = androidx.compose.foundation.BorderStroke(
                                                if (isSelected) 2.dp else 1.dp,
                                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                            ),
                                            modifier = Modifier.width(115.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center
                                                ) {
                                                    Text(
                                                        text = p.name,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (isSelected) {
                                                        Spacer(Modifier.width(3.dp))
                                                        Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 11.sp)
                                                    }
                                                }
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    text = specSummary,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 9.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                                Spacer(Modifier.height(2.dp))
                                                val priceFmt = if (p.price > 0) "%,d Ks".format(p.price.toInt()) else "Free"
                                                Text(
                                                    text = priceFmt,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (profileExpanded) {
                                Spacer(Modifier.height(6.dp))
                                ExposedDropdownMenuBox(
                                    expanded = profileExpanded,
                                    onExpandedChange = { profileExpanded = !profileExpanded },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = selectedProfile?.name ?: "Select",
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("All Profiles") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = profileExpanded) },
                                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    ExposedDropdownMenu(
                                        expanded = profileExpanded,
                                        onDismissRequest = { profileExpanded = false }
                                    ) {
                                        profiles.forEach { p ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(p.name, fontWeight = FontWeight.Bold)
                                                        Text("${p.rateLimit} • ${p.validityDays}D • %,d Ks".format(p.price.toInt()), style = MaterialTheme.typography.bodySmall)
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
                            }
                        }

                        // 4. THREE CHARACTER GENERATION OPTIONS (Numbers Only, Alphabet Only, Numbers + Alphabet)
                        Column {
                            Text(
                                text = "Character Type (စာလုံး ပုံစံ - ၃ မျိုး):",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val options = listOf(
                                    Triple("Numbers Only", "12345678", "0-9 ဂဏန်း"),
                                    Triple("Alphabet Only", "ABCDEFGH", "A-Z အက္ခရာ"),
                                    Triple("Numbers + Alphabet", "A3B8K9M2", "0-9 & A-Z ရောရာ")
                                )
                                options.forEach { (key, sample, desc) ->
                                    val isSelected = selectedNumberStyle == key
                                    Surface(
                                        onClick = { selectedNumberStyle = key },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                        border = androidx.compose.foundation.BorderStroke(
                                            if (isSelected) 1.8.dp else 1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = when (key) {
                                                    "Numbers Only" -> "Numbers Only"
                                                    "Alphabet Only" -> "Alphabet Only"
                                                    else -> "Num + Alpha"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                maxLines = 1
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                                            ) {
                                                Text(
                                                    text = sample,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 9.5.sp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = desc,
                                                fontSize = 8.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 5. PREFIX WITH QUICK PRESETS
                        Column {
                            OutlinedTextField(
                                value = prefix,
                                onValueChange = { prefix = it },
                                label = { Text(strings.prefixOptional) },
                                placeholder = { Text("e.g. HS-, VIP-, NET-") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true,
                                trailingIcon = if (prefix.isNotEmpty()) {
                                    {
                                        IconButton(onClick = { prefix = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                } else null
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, start = 2.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Prefix:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                listOf("None", "HS-", "VIP-", "NET-", "WF-").forEach { chipText ->
                                    val isSelected = (chipText == "None" && prefix.isEmpty()) || (chipText != "None" && prefix == chipText)
                                    SuggestionChip(
                                        onClick = { prefix = if (chipText == "None") "" else chipText },
                                        label = { Text(chipText, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                        modifier = Modifier.height(28.dp)
                                    )
                                }
                            }
                        }

                        // 6. QUANTITY & CODE LENGTH (Clean Stepper & Full-Width Code Length Cards)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Quantity Row with Minus / Plus Steppers (Quick row removed per user request)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(strings.quantity, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text("Total vouchers to generate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            val q = (quantity.toIntOrNull() ?: 10) - 5
                                            quantity = q.coerceAtLeast(1).toString()
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Decrease", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    OutlinedTextField(
                                        value = quantity,
                                        onValueChange = { input -> quantity = cleanNumberInput(input) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, autoCorrectEnabled = false),
                                        modifier = Modifier.width(72.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    )
                                    IconButton(
                                        onClick = {
                                            val q = (quantity.toIntOrNull() ?: 10) + 5
                                            quantity = q.toString()
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.AddCircleOutline, contentDescription = "Increase", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            // Code Length Section (Dedicated Header + Full-Width Balanced Selection Cards)
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${strings.codeLength} (ကုဒ် အလျား):",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (length == "8") {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFF2E7D32).copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "⭐ 8-Box Standard",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1B5E20),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val lengthOptions = listOf(
                                        Triple("4", "4 Digits", "အလွယ် (4)"),
                                        Triple("6", "6 Digits", "အလတ် (6)"),
                                        Triple("8", "8 Digits", "စံနှုန်း (8) ⭐"),
                                        Triple("10", "10 Digits", "အရှည် (10)"),
                                        Triple("12", "12 Digits", "အရှည်ဆုံး (12)")
                                    )
                                    lengthOptions.forEach { (lVal, label, badge) ->
                                        val isSelected = length == lVal
                                        val isPortalStandard = lVal == "8"
                                        Surface(
                                            onClick = { length = lVal },
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                            border = androidx.compose.foundation.BorderStroke(
                                                if (isSelected) 1.8.dp else 1.dp,
                                                if (isSelected) MaterialTheme.colorScheme.primary else if (isPortalStandard) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                            ),
                                            modifier = Modifier.weight(if (isPortalStandard) 1.15f else 0.95f)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(vertical = 7.dp, horizontal = 2.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1
                                                )
                                                Spacer(Modifier.height(1.dp))
                                                Text(
                                                    text = badge,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = if (isPortalStandard) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isPortalStandard) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 4-12 Digits Universal Compatibility Indicator
                            val rawCodeLen = previewCode.replace("-", "").length
                            val isSupportedRange = rawCodeLen in 4..12

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSupportedRange) Color(0xFF2E7D32).copy(alpha = 0.12f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSupportedRange) Color(0xFF2E7D32).copy(alpha = 0.4f) else MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (isSupportedRange) Icons.Default.CheckCircle else Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = if (isSupportedRange) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = if (isSupportedRange) "4 - 12 Digits Fully Compatible ✓ (၄ မှ ၁၂ လုံး ကိုက်ညီမှုရှိ)" else "Code Length Warning (ကုဒ်အလျား သတိပေးချက်)",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSupportedRange) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    if (isSupportedRange) {
                                        Text(
                                            text = "ကုဒ်နံပါတ် ${rawCodeLen} လုံးသည် ပရင်တာ စက္ကူလိပ်နှင့် Captive Portal တွင် အလိုအလျောက် အံဝင်ခွင်ကျဖြစ်ပြီး အဆင်ပြေစွာ ရိုက်ထည့်အသုံးပြုနိုင်ပါသည်။ (4 to 12 digits auto-fit on printer & captive portal)",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
                                            color = Color(0xFF1B5E20)
                                        )
                                    } else {
                                        Text(
                                            text = "အဆင်ပြေစွာ အသုံးပြုနိုင်ရန် ကုဒ်အလျားကို ၄ လုံးမှ ၁၂ လုံးအတွင်း ရွေးချယ်ပေးပါရန်။ (Please choose 4 to 12 digits)",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        FilledTonalButton(
                                            onClick = {
                                                length = "8"
                                                prefix = ""
                                                selectedNumberStyle = "Numbers Only"
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("⭐ 8-Digits စံနှုန်းသို့ ပြောင်းမည်", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Action Buttons Bar (Pinned at bottom)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(0.7f)
                    ) {
                        Text(strings.cancel, maxLines = 1)
                    }

                    Button(
                        onClick = {
                            val q = quantity.toIntOrNull() ?: 0
                            val l = (length.toIntOrNull() ?: 8).coerceAtLeast(4)
                            if (selectedProfile != null && q > 0) {
                                onGenerate(selectedProfile!!, q, l, selectedNumberStyle, isAccountMode, prefix, true)
                            }
                        },
                        enabled = profiles.isNotEmpty() && (quantity.toIntOrNull() ?: 0) > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(0.9f)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.printAction, maxLines = 1)
                    }

                    Button(
                        onClick = {
                            val q = quantity.toIntOrNull() ?: 0
                            val l = (length.toIntOrNull() ?: 8).coerceAtLeast(4)
                            if (selectedProfile != null && q > 0) {
                                onGenerate(selectedProfile!!, q, l, selectedNumberStyle, isAccountMode, prefix, false)
                            }
                        },
                        enabled = profiles.isNotEmpty() && (quantity.toIntOrNull() ?: 0) > 0,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.4f)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "${strings.generateVouchers} ($quantity)",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PrintOptionsDialog(
    sampleVoucher: Voucher? = null,
    defaultSsid: String = "",
    defaultLoginUrl: String = "",
    onDismiss: () -> Unit,
    onPrint: (Int, VoucherPrinter.PaperFormat, Boolean, String, String) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("hotspot_login_prefs", Context.MODE_PRIVATE) }
    var routerSsid by remember(defaultSsid) {
        mutableStateOf(if (defaultSsid.isNotBlank()) defaultSsid else (prefs.getString("router_ssid", "AllGood_Wifi") ?: "AllGood_Wifi"))
    }
    var hotspotLoginUrl by remember(defaultLoginUrl) {
        mutableStateOf(if (defaultLoginUrl.isNotBlank()) defaultLoginUrl else (prefs.getString("hotspot_login_url", "http://allgood.lan") ?: "http://allgood.lan"))
    }
    val savedWidth = remember { BluetoothThermalPrinter.getSavedPaperWidth(context) }
    var selectedStyle by remember { mutableIntStateOf(BluetoothThermalPrinter.getSavedDefaultStyle(context)) } // 1: 2-Compartment Box, 2: Ultra-Micro, 3: 3-Tier Full-Width
    var selectedFormat by remember {
        mutableStateOf(
            if (savedWidth == BluetoothThermalPrinter.PaperWidth.WIDTH_80MM)
                VoucherPrinter.PaperFormat.THERMAL_80MM
            else
                VoucherPrinter.PaperFormat.THERMAL_58MM
        )
    }
    var autoCutEachVoucher by remember { mutableStateOf(BluetoothThermalPrinter.getSavedAutoCut(context)) }
    var a4PreviewMode by remember { mutableIntStateOf(0) } // 0: Full A4 Sheet, 1: Single Ticket Zoom
    val strings = LanguageManager.strings

    val v = sampleVoucher ?: Voucher(
        code = "1819-9733",
        username = "1819-9733",
        profileName = "5M_Daily",
        price = 1000.0,
        dataLimitMb = 1024,
        validityDays = 1
    )

    val isThermal = selectedFormat == VoucherPrinter.PaperFormat.THERMAL_58MM || selectedFormat == VoucherPrinter.PaperFormat.THERMAL_80MM
    val paperWidth = if (selectedFormat == VoucherPrinter.PaperFormat.THERMAL_80MM)
        BluetoothThermalPrinter.PaperWidth.WIDTH_80MM
    else
        BluetoothThermalPrinter.PaperWidth.WIDTH_58MM

    // Generate real preview bitmaps matching exact printer/PDF output
    val previewBitmap = remember(v, selectedFormat, selectedStyle, autoCutEachVoucher, a4PreviewMode, routerSsid, hotspotLoginUrl) {
        if (selectedFormat == VoucherPrinter.PaperFormat.A4_PAGE) {
            if (a4PreviewMode == 0) {
                VoucherPrinter.renderA4PreviewBitmap(v, selectedStyle, routerSsid = routerSsid, loginUrl = hotspotLoginUrl)
            } else {
                BluetoothThermalPrinter.renderVoucherExcelBitmap(v, BluetoothThermalPrinter.PaperWidth.WIDTH_80MM, selectedStyle, routerSsid = routerSsid, loginUrl = hotspotLoginUrl)
            }
        } else {
            BluetoothThermalPrinter.renderThermalPreviewBitmap(v, paperWidth, selectedStyle, showAutoCut = autoCutEachVoucher, routerSsid = routerSsid, loginUrl = hotspotLoginUrl)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Print, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(strings.printVouchersTitle, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header with preview mode badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedFormat == VoucherPrinter.PaperFormat.A4_PAGE) "📄 A4 Paper Sheet Preview" else "🖨️ Thermal Roll Preview",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    if (selectedFormat == VoucherPrinter.PaperFormat.A4_PAGE) {
                        // Switch between Full A4 Page and Single Ticket Zoom
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (a4PreviewMode == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable { a4PreviewMode = 0 }
                            ) {
                                Text(
                                    "Full Page",
                                    fontSize = 11.sp,
                                    fontWeight = if (a4PreviewMode == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (a4PreviewMode == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (a4PreviewMode == 1) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable { a4PreviewMode = 1 }
                            ) {
                                Text(
                                    "Zoom",
                                    fontSize = 11.sp,
                                    fontWeight = if (a4PreviewMode == 1) FontWeight.Bold else FontWeight.Normal,
                                    color = if (a4PreviewMode == 1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    } else if (selectedFormat == VoucherPrinter.PaperFormat.THERMAL_80MM) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "⚡ 80mm Big Fonts",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // VISUAL PREVIEW CARD
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF5F5F7),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (selectedFormat == VoucherPrinter.PaperFormat.A4_PAGE && a4PreviewMode == 0) {
                            // Full A4 paper aspect ratio display (1 : 1.414)
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                shadowElevation = 3.dp,
                                color = Color.White,
                                modifier = Modifier
                                    .fillMaxWidth(0.92f)
                                    .aspectRatio(1f / 1.414f)
                            ) {
                                Image(
                                    bitmap = previewBitmap.asImageBitmap(),
                                    contentDescription = "A4 Page Preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            val sheetCapacity = when (selectedStyle) {
                                2 -> "115 vouchers (5 cols × 23 rows)"
                                3 -> "33 vouchers (3 cols × 11 rows)"
                                else -> "68 vouchers (4 cols × 17 rows)"
                            }
                            Text(
                                text = "✂️ Cut lines included • $sheetCapacity per A4 page",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            // Thermal slip display (or single ticket zoom)
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                shadowElevation = 2.dp,
                                color = Color.White,
                                modifier = Modifier
                                    .fillMaxWidth(if (selectedFormat == VoucherPrinter.PaperFormat.THERMAL_80MM) 0.95f else 0.78f)
                                    .padding(vertical = 4.dp)
                            ) {
                                Image(
                                    bitmap = previewBitmap.asImageBitmap(),
                                    contentDescription = "Ticket Preview",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight(),
                                    contentScale = ContentScale.FillWidth
                                )
                            }
                            if (isThermal) {
                                Text(
                                    text = if (autoCutEachVoucher) "✂️ Added spacing between tickets for manual tear or auto-cut" else "🌱 Compact continuous roll (Minimal spacing, saves 80% paper)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (autoCutEachVoucher) Color(0xFFC62828) else Color(0xFF2E7D32)
                                )
                            }
                        }
                    }
                }

                // SPACING & CUT TOGGLE CARD (shown for thermal printers / POS phones)
                if (isThermal) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (autoCutEachVoucher) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (autoCutEachVoucher) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = if (autoCutEachVoucher) "✂️ Tear / Cut Spacing (Each Ticket)" else "🌱 Compact Strip (Saves 80% Paper)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (!autoCutEachVoucher) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (autoCutEachVoucher)
                                        "Feeds paper past printhead to manual tear teeth or triggers desktop POS cutter."
                                    else
                                        "Zero wasted blank space between vouchers. Highly recommended for 58mm portable printers!",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoCutEachVoucher,
                                onCheckedChange = {
                                    autoCutEachVoucher = it
                                    BluetoothThermalPrinter.saveAutoCut(context, it)
                                },
                                modifier = Modifier.scale(0.85f)
                            )
                        }
                    }
                }

                // Wi-Fi SSID Field (Printed directly on tickets)
                OutlinedTextField(
                    value = routerSsid,
                    onValueChange = {
                        routerSsid = it
                        prefs.edit().putString("router_ssid", it).apply()
                    },
                    label = { Text("Wi-Fi SSID (Ticket Header)") },
                    placeholder = { Text("e.g. AllGood_Wifi, A Yeik Sitt Wifi") },
                    leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(6.dp))

                // Hotspot Login URL Field (Printed under voucher card with small text)
                OutlinedTextField(
                    value = hotspotLoginUrl,
                    onValueChange = {
                        hotspotLoginUrl = it
                        prefs.edit().putString("hotspot_login_url", it).apply()
                    },
                    label = { Text("Hotspot Login URL (Printed Under Card)") },
                    placeholder = { Text("e.g. http://allgood.lan, http://ayeiksitt.lan") },
                    leadingIcon = { Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()

                Text("Paper Format (Paper-Saver):", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedFormat == VoucherPrinter.PaperFormat.THERMAL_58MM,
                        onClick = {
                            selectedFormat = VoucherPrinter.PaperFormat.THERMAL_58MM
                            BluetoothThermalPrinter.savePaperWidth(context, BluetoothThermalPrinter.PaperWidth.WIDTH_58MM)
                        }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("58 mm Thermal (Default / Saves 75% Paper)", fontWeight = FontWeight.SemiBold)
                        Text("Direct Bluetooth print. Fits 58mm mobile thermal printers perfectly without cut-offs.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedFormat == VoucherPrinter.PaperFormat.THERMAL_80MM,
                        onClick = {
                            selectedFormat = VoucherPrinter.PaperFormat.THERMAL_80MM
                            BluetoothThermalPrinter.savePaperWidth(context, BluetoothThermalPrinter.PaperWidth.WIDTH_80MM)
                        }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("80 mm POS Thermal (Desktop POS Printers Only)", fontWeight = FontWeight.SemiBold)
                        Text("⚠️ သတိပြုရန် - 80mm စက္ကူအကျယ်သုံး ပရင်တာများအတွက်သာ ဖြစ်ပါသည်။ (58mm ပရင်တာသုံးပါက 58mm ကိုသာ ရွေးပါ)", style = MaterialTheme.typography.bodySmall, color = if (selectedFormat == VoucherPrinter.PaperFormat.THERMAL_80MM) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedFormat == VoucherPrinter.PaperFormat.A4_PAGE,
                        onClick = { selectedFormat = VoucherPrinter.PaperFormat.A4_PAGE }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("A4 Sheet Grid (Paper-Saving 68-115 per sheet)", fontWeight = FontWeight.SemiBold)
                        Text("High-density cut grid with scissor guides. Massive paper savings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                HorizontalDivider()

                Text("Voucher Box Style (Excel Borders):", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedStyle == 1, onClick = { selectedStyle = 1 })
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Style 1: 2-Compartment Box [ Code | Profile ] (Default)", fontWeight = FontWeight.SemiBold)
                        Text("Excel solid black border. Huge code on left, plan details on right.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedStyle == 2, onClick = { selectedStyle = 2 })
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Style 2: Slim Compact Excel Row (Maximum Paper Saving)", fontWeight = FontWeight.SemiBold)
                        Text("Single line code & plan. Absolute minimum paper consumption.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedStyle == 3, onClick = { selectedStyle = 3 })
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Style 3: 3-Tier Full-Width Stacked Excel Box (Giant Code)", fontWeight = FontWeight.SemiBold)
                        Text("Full-width massive code centered, 3 plan columns at bottom.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onPrint(selectedStyle, selectedFormat, autoCutEachVoucher, routerSsid, hotspotLoginUrl) }) {
                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.printAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
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
    var hasBtPerm by remember { mutableStateOf(BluetoothThermalPrinter.hasBluetoothPermission(context)) }
    var isBtOn by remember { mutableStateOf(BluetoothThermalPrinter.isBluetoothEnabled(context)) }
    var pairedPrinters by remember { mutableStateOf(BluetoothThermalPrinter.getPairedPrinters(context)) }
    var savedPrinter by remember { mutableStateOf(BluetoothThermalPrinter.getSavedPrinter(context)) }
    var selectedWidth by remember { mutableStateOf(BluetoothThermalPrinter.getSavedPaperWidth(context)) }
    var isPrintingTest by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        hasBtPerm = BluetoothThermalPrinter.hasBluetoothPermission(context)
        isBtOn = BluetoothThermalPrinter.isBluetoothEnabled(context)
        pairedPrinters = BluetoothThermalPrinter.getPairedPrinters(context)
        savedPrinter = BluetoothThermalPrinter.getSavedPrinter(context)
    }

    LaunchedEffect(Unit) {
        if (!hasBtPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            )
        }
    }

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
                if (!hasBtPerm) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Bluetooth Permission Required", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Android requires Bluetooth permission to discover your paired printer.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        permissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.BLUETOOTH_CONNECT,
                                                Manifest.permission.BLUETOOTH_SCAN
                                            )
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Grant Bluetooth Permission")
                            }
                        }
                    }
                } else if (!isBtOn) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Bluetooth is Turned Off", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("Turn on Bluetooth to view and connect to printers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                            OutlinedButton(
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Open phone Settings > Bluetooth", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Turn On")
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "1. Select Paired Bluetooth Printer",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val activePrinterDesc = when {
                            savedPrinter?.isConnected == true -> "Connected: ${savedPrinter?.name}"
                            savedPrinter != null && savedPrinter!!.address.isNotBlank() -> "Selected: ${savedPrinter?.name}"
                            else -> "No printer connected"
                        }
                        Text(
                            text = activePrinterDesc,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (savedPrinter != null && savedPrinter!!.address.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Direct Bluetooth connection (SPP) with zero third-party print service dependencies.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (pairedPrinters.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "No paired Bluetooth devices found.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Please pair your thermal printer in Android Bluetooth settings.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                border = androidx.compose.foundation.BorderStroke(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                ),
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
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(device.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                            if (device.isConnected) {
                                                Spacer(Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color(0xFF2E7D32).copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = "● Connected",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF2E7D32),
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(device.address, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (isSelected) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        ) {
                                            Text(
                                                text = "ACTIVE",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
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

                if (savedPrinter != null && savedPrinter!!.address.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (savedPrinter!!.isConnected) "Active Connected: ${savedPrinter!!.name} (${savedPrinter!!.address})" else "Active: ${savedPrinter!!.name} (${savedPrinter!!.address})",
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
                    if (savedPrinter == null || savedPrinter!!.address.isBlank()) {
                        Toast.makeText(context, "Please select or connect a Bluetooth printer above first", Toast.LENGTH_SHORT).show()
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
                            Toast.makeText(context, "✓ Print Success", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "✗ Print failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                enabled = !isPrintingTest && (savedPrinter != null && savedPrinter!!.address.isNotBlank()),
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

