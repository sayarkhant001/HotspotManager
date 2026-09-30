package com.example.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.BuildConfig
import com.example.ui.screens.MainViewModel
import com.example.utils.AppStrings
import com.example.utils.BluetoothPrinterDevice
import com.example.utils.BluetoothThermalPrinter
import kotlinx.coroutines.launch

@Composable
fun DashboardSettingsDialog(
    viewModel: MainViewModel,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }

    // -------------------------------------------------------------
    // APP UPDATE STATE
    // -------------------------------------------------------------
    val appUpdateInfo by viewModel.appUpdateInfo.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val isDownloadingUpdate by viewModel.isDownloadingUpdate.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()

    // -------------------------------------------------------------
    // PRINTER STATE
    // -------------------------------------------------------------
    var hasBtPerm by remember { mutableStateOf(BluetoothThermalPrinter.hasBluetoothPermission(context)) }
    var isBtOn by remember { mutableStateOf(BluetoothThermalPrinter.isBluetoothEnabled(context)) }
    var pairedPrinters by remember { mutableStateOf(BluetoothThermalPrinter.getPairedPrinters(context)) }
    var savedPrinter by remember { mutableStateOf(BluetoothThermalPrinter.getSavedPrinter(context)) }
    var selectedWidth by remember { mutableStateOf(BluetoothThermalPrinter.getSavedPaperWidth(context)) }
    var autoCutEnabled by remember { mutableStateOf(BluetoothThermalPrinter.getSavedAutoCut(context)) }
    var selectedStyle by remember { mutableIntStateOf(BluetoothThermalPrinter.getSavedDefaultStyle(context)) }
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

    // -------------------------------------------------------------
    // PASSWORD STATE
    // -------------------------------------------------------------
    val loginPrefs = remember { context.getSharedPreferences("hotspot_login_prefs", Context.MODE_PRIVATE) }
    val currentConnectedUser = viewModel.getConnectedUser().ifBlank { loginPrefs.getString("router_user", "admin") ?: "admin" }
    val currentConnectedIp = viewModel.getConnectedIp().ifBlank { loginPrefs.getString("router_ip", "10.10.10.1") ?: "10.10.10.1" }
    val initialPass = viewModel.getConnectedPass().ifBlank { loginPrefs.getString("router_pass", "") ?: "" }

    var currentPassInput by remember { mutableStateOf(initialPass) }
    var newPassInput by remember { mutableStateOf("") }
    var confirmPassInput by remember { mutableStateOf("") }
    var showCurrentPass by remember { mutableStateOf(false) }
    var showNewPass by remember { mutableStateOf(false) }
    var showConfirmPass by remember { mutableStateOf(false) }
    var isChangingPass by remember { mutableStateOf(false) }
    var passErrorMsg by remember { mutableStateOf<String?>(null) }
    var passSuccessMsg by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .imePadding(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. PINNED HEADER
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                strings.settings,
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFF4CAF50), CircleShape)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "$currentConnectedUser @ $currentConnectedIp",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = strings.close)
                    }
                }

                // 2. TABS SELECTOR
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)) }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Printer & Test", fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LockReset, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Login Password", fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("App Updates", fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }

                // 3. SCROLLABLE TAB CONTENT
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (selectedTab == 0) {
                        // ============================================================
                        // TAB 0: PRINTER SETUP & TEST FUNCTIONS
                        // ============================================================

                        // Active Printer Status Card
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (savedPrinter != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (savedPrinter != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Icon(
                                        imageVector = if (savedPrinter != null) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                                        contentDescription = null,
                                        tint = if (savedPrinter != null) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = strings.activePrinter,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = savedPrinter?.name ?: "No printer selected",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (savedPrinter != null) {
                                            Text(
                                                text = savedPrinter!!.address,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }

                                if (savedPrinter != null) {
                                    FilledTonalButton(
                                        onClick = {
                                            isPrintingTest = true
                                            scope.launch {
                                                Toast.makeText(context, "Sending test receipt to ${savedPrinter!!.name}...", Toast.LENGTH_SHORT).show()
                                                val res = BluetoothThermalPrinter.printTestReceiptDirect(
                                                    context = context,
                                                    deviceAddress = savedPrinter!!.address,
                                                    paperWidth = selectedWidth
                                                )
                                                isPrintingTest = false
                                                if (res.isSuccess) {
                                                    Toast.makeText(context, "✓ Test Print Success!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "✗ Print Failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        },
                                        enabled = !isPrintingTest,
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (isPrintingTest) "Testing..." else "Test Print", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Paired Printers List
                        Column {
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
                                        text = "Paired Bluetooth Printers",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Active Default: ${savedPrinter?.name ?: "Micro (Default)"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        hasBtPerm = BluetoothThermalPrinter.hasBluetoothPermission(context)
                                        isBtOn = BluetoothThermalPrinter.isBluetoothEnabled(context)
                                        pairedPrinters = BluetoothThermalPrinter.getPairedPrinters(context)
                                        savedPrinter = BluetoothThermalPrinter.getSavedPrinter(context)
                                        Toast.makeText(context, "Refreshed: ${pairedPrinters.size} devices", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.refresh, style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            if (pairedPrinters.isEmpty()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "No paired Bluetooth printers found.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Default selected printer: ${savedPrinter?.name ?: "Micro (Default)"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = {
                                                    try {
                                                        val btIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                                        context.startActivity(btIntent)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Open phone Settings > Bluetooth to pair", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Pair in Bluetooth")
                                            }
                                            Button(
                                                onClick = {
                                                    hasBtPerm = BluetoothThermalPrinter.hasBluetoothPermission(context)
                                                    isBtOn = BluetoothThermalPrinter.isBluetoothEnabled(context)
                                                    pairedPrinters = BluetoothThermalPrinter.getPairedPrinters(context)
                                                    savedPrinter = BluetoothThermalPrinter.getSavedPrinter(context)
                                                    Toast.makeText(context, "Refreshed: ${pairedPrinters.size} devices", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Refresh")
                                            }
                                        }
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier.padding(top = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    pairedPrinters.forEach { device ->
                                        val isSelected = savedPrinter?.address == device.address
                                        val isMicro = device.name.contains("micro", ignoreCase = true)
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                            border = androidx.compose.foundation.BorderStroke(
                                                if (isSelected) 1.5.dp else 1.dp,
                                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    BluetoothThermalPrinter.savePrinter(context, device)
                                                    savedPrinter = device
                                                    Toast.makeText(context, "Selected ${device.name}", Toast.LENGTH_SHORT).show()
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = isSelected,
                                                    onClick = {
                                                        BluetoothThermalPrinter.savePrinter(context, device)
                                                        savedPrinter = device
                                                        Toast.makeText(context, "Selected ${device.name}", Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(device.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                                        if (isMicro) {
                                                            Spacer(Modifier.width(6.dp))
                                                            Surface(
                                                                shape = RoundedCornerShape(4.dp),
                                                                color = MaterialTheme.colorScheme.tertiaryContainer
                                                            ) {
                                                                Text(
                                                                    text = "⭐ Default (Micro)",
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
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
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        // Paper Width Options
                        Column {
                            Text(
                                text = strings.paperWidthTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 58mm Option
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            selectedWidth = BluetoothThermalPrinter.PaperWidth.WIDTH_58MM
                                            BluetoothThermalPrinter.savePaperWidth(context, selectedWidth)
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selectedWidth == BluetoothThermalPrinter.PaperWidth.WIDTH_58MM) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.5.dp,
                                        if (selectedWidth == BluetoothThermalPrinter.PaperWidth.WIDTH_58MM) MaterialTheme.colorScheme.primary else Color.Transparent
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selectedWidth == BluetoothThermalPrinter.PaperWidth.WIDTH_58MM,
                                            onClick = {
                                                selectedWidth = BluetoothThermalPrinter.PaperWidth.WIDTH_58MM
                                                BluetoothThermalPrinter.savePaperWidth(context, selectedWidth)
                                            }
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Column {
                                            Text("58 mm", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Text("Portable (32 cols)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }

                                // 80mm Option
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            selectedWidth = BluetoothThermalPrinter.PaperWidth.WIDTH_80MM
                                            BluetoothThermalPrinter.savePaperWidth(context, selectedWidth)
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selectedWidth == BluetoothThermalPrinter.PaperWidth.WIDTH_80MM) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.5.dp,
                                        if (selectedWidth == BluetoothThermalPrinter.PaperWidth.WIDTH_80MM) MaterialTheme.colorScheme.primary else Color.Transparent
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selectedWidth == BluetoothThermalPrinter.PaperWidth.WIDTH_80MM,
                                            onClick = {
                                                selectedWidth = BluetoothThermalPrinter.PaperWidth.WIDTH_80MM
                                                BluetoothThermalPrinter.savePaperWidth(context, selectedWidth)
                                            }
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Column {
                                            Text("80 mm", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Text("Desktop POS (48 cols)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }

                        // Auto-Cut Switch
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = strings.autoCutOption,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (autoCutEnabled) "Adds extra spacing between tickets for manual tearing or cutter" else "Compact mode: Zero blank space between vouchers (Saves 80% Paper)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (!autoCutEnabled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = autoCutEnabled,
                                    onCheckedChange = {
                                        autoCutEnabled = it
                                        BluetoothThermalPrinter.saveAutoCut(context, it)
                                    }
                                )
                            }
                        }

                        // Default Voucher Style
                        Column {
                            Text(
                                text = "Default Voucher Print Style",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            val styles = listOf(
                                Triple(1, "Style 1 (Recommended)", "Compact 2-Compartment Paper-Saver with Giant Code"),
                                Triple(2, "Style 2 (Ultra-Micro)", "Minimalist 1-Line Compact Layout"),
                                Triple(3, "Style 3 (3-Tier Stacked)", "Full-Width Header, Giant Code & 3 Bottom Cells")
                            )
                            styles.forEach { (stId, stTitle, stDesc) ->
                                val isStSelected = selectedStyle == stId
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isStSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedStyle = stId
                                            BluetoothThermalPrinter.saveDefaultStyle(context, stId)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isStSelected,
                                            onClick = {
                                                selectedStyle = stId
                                                BluetoothThermalPrinter.saveDefaultStyle(context, stId)
                                            }
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Column {
                                            Text(stTitle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Text(stDesc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }

                    } else if (selectedTab == 1) {
                        // ============================================================
                        // TAB 1: ROUTER LOGIN PASSWORD CHANGING
                        // ============================================================

                        // Active Account Info
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Target Router User: $currentConnectedUser",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Router IP: $currentConnectedIp",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (passErrorMsg != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Spacer(Modifier.width(8.dp))
                                    Text(passErrorMsg!!, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }

                        if (passSuccessMsg != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFE8F5E9),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                                    Spacer(Modifier.width(8.dp))
                                    Text(passSuccessMsg!!, color = Color(0xFF1B5E20), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Current Password
                        OutlinedTextField(
                            value = currentPassInput,
                            onValueChange = {
                                currentPassInput = it
                                passErrorMsg = null
                            },
                            label = { Text(strings.currentPassword) },
                            singleLine = true,
                            visualTransformation = if (showCurrentPass) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { showCurrentPass = !showCurrentPass }) {
                                    Icon(
                                        imageVector = if (showCurrentPass) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // New Password
                        OutlinedTextField(
                            value = newPassInput,
                            onValueChange = {
                                newPassInput = it
                                passErrorMsg = null
                            },
                            label = { Text(strings.newPassword) },
                            singleLine = true,
                            visualTransformation = if (showNewPass) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { showNewPass = !showNewPass }) {
                                    Icon(
                                        imageVector = if (showNewPass) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Confirm New Password
                        OutlinedTextField(
                            value = confirmPassInput,
                            onValueChange = {
                                confirmPassInput = it
                                passErrorMsg = null
                            },
                            label = { Text(strings.confirmNewPassword) },
                            singleLine = true,
                            visualTransformation = if (showConfirmPass) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { showConfirmPass = !showConfirmPass }) {
                                    Icon(
                                        imageVector = if (showConfirmPass) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Update Password Action Button
                        Button(
                            onClick = {
                                if (currentPassInput.isBlank()) {
                                    passErrorMsg = "Please enter your current password"
                                    return@Button
                                }
                                if (newPassInput.isBlank()) {
                                    passErrorMsg = "Please enter a new password"
                                    return@Button
                                }
                                if (newPassInput != confirmPassInput) {
                                    passErrorMsg = strings.passwordMismatch
                                    return@Button
                                }
                                isChangingPass = true
                                passErrorMsg = null
                                passSuccessMsg = null

                                viewModel.changeLoginPassword(currentPassInput.trim(), newPassInput.trim()) { success, msg ->
                                    isChangingPass = false
                                    if (success) {
                                        // Save to login preferences so future app launches work seamlessly
                                        loginPrefs.edit()
                                            .putString("router_pass", newPassInput.trim())
                                            .apply()
                                        passSuccessMsg = strings.passwordChangedSuccess
                                        currentPassInput = newPassInput.trim()
                                        newPassInput = ""
                                        confirmPassInput = ""
                                    } else {
                                        passErrorMsg = msg
                                    }
                                }
                            },
                            enabled = !isChangingPass && newPassInput.isNotBlank() && confirmPassInput.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            if (isChangingPass) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(8.dp))
                                Text("Updating Password...")
                            } else {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(strings.changePasswordTitle, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        // Disconnect / Logout Section
                        OutlinedButton(
                            onClick = onLogout,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(strings.logoutRouter, fontWeight = FontWeight.Bold)
                        }
                    } else if (selectedTab == 2) {
                        // ============================================================
                        // TAB 2: APP UPDATES & GITHUB RELEASES
                        // ============================================================

                        // App Version Card
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.SystemUpdate,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            "Hotspot Manager",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            "Installed: v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Checking State
                        if (isCheckingUpdate) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Text("Checking GitHub for newer releases...", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }

                        // Release Info Card
                        val info = appUpdateInfo
                        if (info != null) {
                            if (info.isNewer) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFFE8F5E9),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.Celebration,
                                                    contentDescription = null,
                                                    tint = Color(0xFF2E7D32),
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    "New Update: v${info.versionName}",
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF1B5E20),
                                                    style = MaterialTheme.typography.titleMedium
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF4CAF50)
                                            ) {
                                                Text(
                                                    "NEW",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        if (info.releaseTitle.isNotBlank()) {
                                            Text(
                                                info.releaseTitle,
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color(0xFF2E7D32)
                                            )
                                        }

                                        if (info.releaseNotes.isNotBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color.White.copy(alpha = 0.8f),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    info.releaseNotes,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.Black,
                                                    modifier = Modifier.padding(10.dp)
                                                )
                                            }
                                        }

                                        if (isDownloadingUpdate) {
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        "Downloading update APK...",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFF1B5E20)
                                                    )
                                                    Text(
                                                        "${(downloadProgress * 100).toInt()}%",
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFF1B5E20)
                                                    )
                                                }
                                                LinearProgressIndicator(
                                                    progress = { downloadProgress },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(8.dp),
                                                    color = Color(0xFF4CAF50),
                                                    trackColor = Color(0xFFC8E6C9)
                                                )
                                            }
                                        } else {
                                            Button(
                                                onClick = { viewModel.downloadAndInstallUpdate(context) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text("Download & Install Update", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF4CAF50),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text("Up to Date", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                            Text(
                                                "You are currently running the latest released version.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Check For Updates Button
                        Button(
                            onClick = { viewModel.checkForAppUpdate(manual = true) },
                            enabled = !isCheckingUpdate && !isDownloadingUpdate,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Check for Updates Now", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 4. PINNED FOOTER
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(strings.close, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
