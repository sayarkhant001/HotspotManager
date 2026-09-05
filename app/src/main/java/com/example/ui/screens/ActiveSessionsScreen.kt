package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.components.GlassCard
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.data.remote.ActiveUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionsScreen(viewModel: MainViewModel, navController: NavController) {
    val users by viewModel.activeUsers.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.fetchRouterData()
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Active Sessions") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.fetchRouterData() }) {
                        Text("Refresh", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            items(users) { user ->
                ActiveUserCard(user, onBan = { viewModel.banMac(user.macAddress) })
            }
            if (users.isEmpty()) {
                item {
                    Text("No active users.", modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
fun ActiveUserCard(user: ActiveUser, onBan: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(user.user, style = MaterialTheme.typography.titleMedium)
                Text("IP: ${user.address}")
                Text("MAC: ${user.macAddress}")
                Text("Uptime: ${user.uptime}")
                Text("In: ${user.bytesIn} B / Out: ${user.bytesOut} B")
            }
            IconButton(onClick = onBan) {
                Icon(Icons.Default.Block, contentDescription = "Ban User", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
