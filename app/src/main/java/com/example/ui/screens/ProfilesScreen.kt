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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(viewModel: MainViewModel, navController: NavController) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.syncProfiles()
    }

    Scaffold(
        containerColor = Color.Transparent,
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
                    IconButton(onClick = { viewModel.syncProfiles() }) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync Router Profiles")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
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
                ProfileItemCard(profile)
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

        if (showDialog) {
            AddProfileDialog(
                onDismiss = { showDialog = false },
                onAdd = { name, rateLimit, sharedUsers, dataMb, durMin, price, sellPrice, validity ->
                    viewModel.addProfile(name, rateLimit, sharedUsers, dataMb, durMin, price, sellPrice, validity)
                    showDialog = false
                }
            )
        }
    }
}

@Composable
fun ProfileItemCard(profile: UserProfile) {
    GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = if (profile.price > 0) "\$${profile.price}" else "Free",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
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

@Composable
fun AddProfileDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, Int, Int, Int, Double, Double, Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var rateLimit by remember { mutableStateOf("5M/5M") }
    var sharedUsers by remember { mutableStateOf("1") }
    var validityDays by remember { mutableStateOf("1") }
    var dataLimitMb by remember { mutableStateOf("0") }
    var price by remember { mutableStateOf("0.0") }
    var sellingPrice by remember { mutableStateOf("0.0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Hotspot Profile") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name (e.g. 5Mbps_1D)") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = rateLimit,
                    onValueChange = { rateLimit = it },
                    label = { Text("Rate Limit Rx/Tx (e.g. 5M/5M)") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = validityDays,
                        onValueChange = { validityDays = it },
                        label = { Text("Validity (Days)") },
                        modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = sharedUsers,
                        onValueChange = { sharedUsers = it },
                        label = { Text("Shared Users") },
                        modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dataLimitMb,
                        onValueChange = { dataLimitMb = it },
                        label = { Text("Data Limit (MB, 0=Unlim)") },
                        modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text("Price ($)") },
                        modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) {
                    onAdd(
                        name,
                        rateLimit,
                        sharedUsers.toIntOrNull() ?: 1,
                        dataLimitMb.toIntOrNull() ?: 0,
                        0,
                        price.toDoubleOrNull() ?: 0.0,
                        sellingPrice.toDoubleOrNull() ?: 0.0,
                        validityDays.toIntOrNull() ?: 1
                    )
                }
            }) { Text("Save to Router") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

