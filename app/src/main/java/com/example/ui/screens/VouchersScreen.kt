package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.components.GlassCard
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.domain.models.UserProfile
import com.example.domain.models.Voucher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VouchersScreen(viewModel: MainViewModel, navController: NavController) {
    val vouchers by viewModel.vouchers.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Vouchers & Accounts") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            Column {
                FloatingActionButton(
                    onClick = { 
                        // Default to style 2 for printing
                        com.example.utils.VoucherPrinter.printVouchers(navController.context, vouchers, 2)
                    },
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Print")
                }
                FloatingActionButton(onClick = { showDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Generate Vouchers")
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            items(vouchers) { voucher ->
                VoucherItemCard(voucher)
            }
        }

        if (showDialog) {
            GenerateVouchersDialog(
                profiles = profiles,
                onDismiss = { showDialog = false },
                onGenerate = { profile, qty, length, mode ->
                    viewModel.generateVouchers(profile, qty, length, mode)
                    showDialog = false
                }
            )
        }
    }
}

@Composable
fun VoucherItemCard(voucher: Voucher) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (voucher.isUsed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ConfirmationNumber,
                        contentDescription = "Voucher",
                        tint = if (voucher.isUsed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = voucher.code,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                Text(
                    text = "${voucher.profileName} • ${voucher.dataLimitMb} MB / ${voucher.durationMinutes} Min",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                color = if (voucher.isUsed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = if(voucher.isUsed) "Used" else "Valid",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (voucher.isUsed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateVouchersDialog(
    profiles: List<UserProfile>,
    onDismiss: () -> Unit,
    onGenerate: (UserProfile, Int, Int, String) -> Unit
) {
    var selectedProfile by remember { mutableStateOf<UserProfile?>(profiles.firstOrNull()) }
    var qty by remember { mutableStateOf("10") }
    var length by remember { mutableStateOf("8") }
    var mode by remember { mutableStateOf("Alphanumeric") }

    val modeOptions = listOf("Numbers Only", "Letters Only", "Alphanumeric")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate Vouchers") },
        text = {
            Column {
                if (profiles.isEmpty()) {
                    Text("No profiles available. Please create a profile first.")
                } else {
                    Text("Profile:")
                    // Simplified dropdown using radio or simple selection for brevity
                    profiles.forEach { p ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            RadioButton(selected = p == selectedProfile, onClick = { selectedProfile = p })
                            Text(p.name)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text("Quantity") })
                    OutlinedTextField(value = length, onValueChange = { length = it }, label = { Text("Code Length (>= 8)") })
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Character Mode:")
                    modeOptions.forEach { m ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            RadioButton(selected = m == mode, onClick = { mode = m })
                            Text(m)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val q = qty.toIntOrNull() ?: 0
                val l = length.toIntOrNull() ?: 8
                if (selectedProfile != null && q > 0 && l >= 8) {
                    onGenerate(selectedProfile!!, q, l, mode)
                }
            }) { Text("Generate") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
