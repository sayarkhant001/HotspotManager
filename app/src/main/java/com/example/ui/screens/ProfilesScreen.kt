package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.components.GlassCard
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.domain.models.UserProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(viewModel: MainViewModel, navController: NavController) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("User Profiles") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .padding(16.dp)
        ) {
            items(profiles) { profile ->
                ProfileItemCard(profile)
            }
        }
        
        if (showDialog) {
            AddProfileDialog(
                onDismiss = { showDialog = false },
                onAdd = { name, dl, ul, data, dur, price, validity ->
                    viewModel.addProfile(name, dl, ul, data, dur, price, validity)
                    showDialog = false
                }
            )
        }
    }
}

@Composable
fun ProfileItemCard(profile: UserProfile) {
    GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(profile.name, style = MaterialTheme.typography.titleMedium)
            Text("Rate: ${profile.downloadLimitMbps}M/${profile.uploadLimitMbps}M")
            Text("Data: ${profile.dataLimitMb} MB")
            Text("Duration: ${profile.durationMinutes} Mins")
            Text("Price: \$${profile.price}")
        }
    }
}

@Composable
fun AddProfileDialog(onDismiss: () -> Unit, onAdd: (String, Int, Int, Int, Int, Double, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var dl by remember { mutableStateOf("") }
    var ul by remember { mutableStateOf("") }
    var data by remember { mutableStateOf("") }
    var dur by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Profile") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                OutlinedTextField(value = dl, onValueChange = { dl = it }, label = { Text("Download (Mbps)") })
                OutlinedTextField(value = ul, onValueChange = { ul = it }, label = { Text("Upload (Mbps)") })
                OutlinedTextField(value = data, onValueChange = { data = it }, label = { Text("Data Limit (MB)") })
                OutlinedTextField(value = dur, onValueChange = { dur = it }, label = { Text("Duration (Minutes)") })
                OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Price") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onAdd(name, dl.toIntOrNull()?:0, ul.toIntOrNull()?:0, data.toIntOrNull()?:0, dur.toIntOrNull()?:0, price.toDoubleOrNull()?:0.0, 30)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
