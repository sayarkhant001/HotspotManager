package com.example.ui.components

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
    // PRINTER STATE
    // -------------------------------------------------------------
    var pairedPrinters by remember { mutableStateOf(BluetoothThermalPrinter.getPairedPrinters(context)) }
    var savedPrinter by remember { mutableStateOf(BluetoothThermalPrinter.getSavedPrinter(context)) }
    var selectedWidth by remember { mutableStateOf(BluetoothThermalPrinter.getSavedPaperWidth(context)) }
    var autoCutEnabled by remember { mutableStateOf(BluetoothThermalPrinter.getSavedAutoCut(context)) }
    var selectedStyle by remember { mutableIntStateOf(BluetoothThermalPrinter.getSavedDefaultStyle(context)) }
    var isPrintingTest by remember { mutableStateOf(false) }

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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Paired Bluetooth Printers",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(
                                    onClick = {
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
                                    modifier = Modifier.fillMaxWidth(),
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
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(8.dp))
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
                                            Spacer(Modifier.width(6.dp))
                                            Text("Pair in Bluetooth Settings")
                                        }
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    pairedPrinters.forEach { device ->
                                        val isSelected = savedPrinter?.address == device.address
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
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
                                                    Text(device.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
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
                                        text = "Advance paper past cutter & partial cut each ticket",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

                    } else {
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
