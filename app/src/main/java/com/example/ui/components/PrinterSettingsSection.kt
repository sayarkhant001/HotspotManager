package com.example.ui.components

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.utils.BluetoothThermalPrinter
import kotlinx.coroutines.launch

/**
 * Reusable Printer Settings Section composable.
 * Used in both SettingsScreen and (formerly) DashboardSettingsDialog.
 */
@Composable
fun PrinterSettingsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasBtPerm     by remember { mutableStateOf(BluetoothThermalPrinter.hasBluetoothPermission(context)) }
    var isBtOn        by remember { mutableStateOf(BluetoothThermalPrinter.isBluetoothEnabled(context)) }
    var pairedPrinters by remember { mutableStateOf(BluetoothThermalPrinter.getPairedPrinters(context)) }
    var savedPrinter  by remember { mutableStateOf(BluetoothThermalPrinter.getSavedPrinter(context)) }
    var selectedWidth by remember { mutableStateOf(BluetoothThermalPrinter.getSavedPaperWidth(context)) }
    var autoCutEnabled by remember { mutableStateOf(BluetoothThermalPrinter.getSavedAutoCut(context)) }
    var selectedStyle by remember { mutableIntStateOf(BluetoothThermalPrinter.getSavedDefaultStyle(context)) }
    var isPrintingTest by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        hasBtPerm      = BluetoothThermalPrinter.hasBluetoothPermission(context)
        isBtOn         = BluetoothThermalPrinter.isBluetoothEnabled(context)
        pairedPrinters = BluetoothThermalPrinter.getPairedPrinters(context)
        savedPrinter   = BluetoothThermalPrinter.getSavedPrinter(context)
    }

    LaunchedEffect(Unit) {
        if (!hasBtPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            ))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // ── Active Printer Card ─────────────────────────────────────────────
        val isPrinterActive = savedPrinter != null && savedPrinter!!.address.isNotBlank()
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isPrinterActive)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(
                1.dp,
                if (isPrinterActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                    Icon(
                        imageVector = if (isPrinterActive) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = if (savedPrinter?.isConnected == true) Color(0xFF2E7D32)
                               else if (isPrinterActive) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Active Printer",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isPrinterActive) savedPrinter!!.name else "No printer connected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isPrinterActive) {
                            Text(
                                text = if (savedPrinter!!.isConnected)
                                    "● Connected (${savedPrinter!!.address})"
                                else savedPrinter!!.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (savedPrinter!!.isConnected) Color(0xFF2E7D32)
                                        else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                if (isPrinterActive) {
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
                                if (res.isSuccess)
                                    Toast.makeText(context, "✓ Test Print Success!", Toast.LENGTH_SHORT).show()
                                else
                                    Toast.makeText(context, "✗ Print Failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
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

        // ── Bluetooth Permission / BT Off warnings ──────────────────────────
        if (!hasBtPerm) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Bluetooth Permission Required", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Android requires Bluetooth permission to discover your paired printer.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            permissionLauncher.launch(arrayOf(
                                Manifest.permission.BLUETOOTH_CONNECT,
                                Manifest.permission.BLUETOOTH_SCAN
                            ))
                        }
                    }, shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Grant Bluetooth Permission")
                    }
                }
            }
        } else if (!isBtOn) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bluetooth is Turned Off", fontWeight = FontWeight.Bold)
                        Text("Turn on Bluetooth to view and connect to printers", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = {
                        try { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                        catch (e: Exception) { Toast.makeText(context, "Open phone Settings > Bluetooth", Toast.LENGTH_SHORT).show() }
                    }, shape = RoundedCornerShape(8.dp)) { Text("Turn On") }
                }
            }
        }

        // ── Paired Printers List ────────────────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Paired Bluetooth Printers", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                val activePrinterDesc = when {
                    savedPrinter?.isConnected == true -> "Connected: ${savedPrinter?.name}"
                    savedPrinter != null && savedPrinter!!.address.isNotBlank() -> "Selected: ${savedPrinter?.name}"
                    else -> "No printer connected"
                }
                Text(activePrinterDesc, style = MaterialTheme.typography.labelSmall,
                    color = if (savedPrinter != null && savedPrinter!!.address.isNotBlank())
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            }
            TextButton(onClick = {
                hasBtPerm      = BluetoothThermalPrinter.hasBluetoothPermission(context)
                isBtOn         = BluetoothThermalPrinter.isBluetoothEnabled(context)
                pairedPrinters = BluetoothThermalPrinter.getPairedPrinters(context)
                savedPrinter   = BluetoothThermalPrinter.getSavedPrinter(context)
                Toast.makeText(context, "Refreshed: ${pairedPrinters.size} devices", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Refresh", style = MaterialTheme.typography.labelMedium)
            }
        }

        if (pairedPrinters.isEmpty()) {
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)) {
                Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No paired Bluetooth printers found.", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("Please pair your thermal printer in Android Bluetooth settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            try { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                            catch (e: Exception) { Toast.makeText(context, "Open phone Settings > Bluetooth to pair", Toast.LENGTH_SHORT).show() }
                        }) {
                            Icon(Icons.Default.Bluetooth, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pair in Bluetooth")
                        }
                        Button(onClick = {
                            hasBtPerm      = BluetoothThermalPrinter.hasBluetoothPermission(context)
                            isBtOn         = BluetoothThermalPrinter.isBluetoothEnabled(context)
                            pairedPrinters = BluetoothThermalPrinter.getPairedPrinters(context)
                            savedPrinter   = BluetoothThermalPrinter.getSavedPrinter(context)
                            Toast.makeText(context, "Refreshed: ${pairedPrinters.size} devices", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Refresh")
                        }
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                pairedPrinters.forEach { device ->
                    val isSelected = savedPrinter?.address == device.address
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent),
                        modifier = Modifier.fillMaxWidth().clickable {
                            BluetoothThermalPrinter.savePrinter(context, device)
                            savedPrinter = device
                            Toast.makeText(context, "Selected ${device.name}", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = isSelected, onClick = {
                                BluetoothThermalPrinter.savePrinter(context, device)
                                savedPrinter = device
                                Toast.makeText(context, "Selected ${device.name}", Toast.LENGTH_SHORT).show()
                            })
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(device.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                    if (device.isConnected) {
                                        Spacer(Modifier.width(6.dp))
                                        Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF2E7D32).copy(alpha = 0.15f)) {
                                            Text("● Connected", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                        }
                                    }
                                }
                                Text(device.address, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (isSelected) {
                                Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primary) {
                                    Text("ACTIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // ── Paper Width ─────────────────────────────────────────────────────
        Text("Paper Width", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                BluetoothThermalPrinter.PaperWidth.WIDTH_58MM to ("58 mm" to "Portable (32 cols)"),
                BluetoothThermalPrinter.PaperWidth.WIDTH_80MM to ("80 mm" to "Desktop POS (48 cols)")
            ).forEach { (pw, info) ->
                Surface(
                    modifier = Modifier.weight(1f).clickable {
                        selectedWidth = pw
                        BluetoothThermalPrinter.savePaperWidth(context, pw)
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selectedWidth == pw) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = BorderStroke(1.5.dp, if (selectedWidth == pw) MaterialTheme.colorScheme.primary else Color.Transparent)
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedWidth == pw, onClick = {
                            selectedWidth = pw
                            BluetoothThermalPrinter.savePaperWidth(context, pw)
                        })
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(info.first, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(info.second, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // ── Auto-Cut ────────────────────────────────────────────────────────
        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-Cut / Feed", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (autoCutEnabled) "Adds extra spacing between tickets for manual tearing or cutter"
                        else "Compact mode: Zero blank space between vouchers (Saves 80% Paper)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (!autoCutEnabled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = autoCutEnabled, onCheckedChange = {
                    autoCutEnabled = it
                    BluetoothThermalPrinter.saveAutoCut(context, it)
                })
            }
        }

        // ── Default Voucher Style ───────────────────────────────────────────
        Text("Default Voucher Print Style", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                Triple(1, "Style 1 (Recommended)", "Compact 2-Compartment Paper-Saver with Giant Code"),
                Triple(2, "Style 2 (Ultra-Micro)", "Minimalist 1-Line Compact Layout"),
                Triple(3, "Style 3 (3-Tier Stacked)", "Full-Width Header, Giant Code & 3 Bottom Cells")
            ).forEach { (stId, stTitle, stDesc) ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (selectedStyle == stId) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent,
                    modifier = Modifier.fillMaxWidth().clickable {
                        selectedStyle = stId
                        BluetoothThermalPrinter.saveDefaultStyle(context, stId)
                    }
                ) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedStyle == stId, onClick = {
                            selectedStyle = stId
                            BluetoothThermalPrinter.saveDefaultStyle(context, stId)
                        })
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(stTitle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(stDesc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
