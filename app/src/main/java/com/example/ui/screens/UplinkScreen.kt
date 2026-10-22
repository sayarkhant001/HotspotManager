package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.domain.models.LoadBalancePlan
import com.example.domain.models.UplinkAssignment
import com.example.domain.models.UplinkInfo
import com.example.domain.models.UplinkTraffic
import kotlinx.coroutines.delay

internal fun fmtBps(bps: Long): String = when {
    bps >= 1_000_000 -> String.format(java.util.Locale.US, "%.1f Mbps", bps / 1_000_000.0)
    bps >= 1_000 -> String.format(java.util.Locale.US, "%.0f Kbps", bps / 1_000.0)
    else -> "${bps}bps"
}

internal fun fmtBytesTotal(b: Long): String = when {
    b >= 1_073_741_824 -> String.format(java.util.Locale.US, "%.1f GB", b / 1_073_741_824.0)
    b >= 1_048_576 -> String.format(java.util.Locale.US, "%.0f MB", b / 1_048_576.0)
    else -> "${b}B"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UplinkScreen(viewModel: MainViewModel, navController: NavController) {
    val uplinks by viewModel.uplinks.collectAsStateWithLifecycle()
    val traffic by viewModel.uplinkTraffic.collectAsStateWithLifecycle()
    val lbDeployed by viewModel.lbDeployed.collectAsStateWithLifecycle()
    val isLoading by viewModel.isUplinkLoading.collectAsStateWithLifecycle()
    val userMsg by viewModel.userMessage.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    var showConfig by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.fetchUplinks() }
    LaunchedEffect(userMsg) { userMsg?.let { snackbar.showSnackbar(it); viewModel.clearUserMessage() } }
    LaunchedEffect(Unit) { while (true) { delay(3000); viewModel.refreshUplinkTraffic() } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Uplinks / Load Balancing", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("${uplinks.size} WAN · LB " + if (lbDeployed) "ACTIVE" else "off",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (lbDeployed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    IconButton(onClick = { viewModel.fetchUplinks() }) {
                        if (isLoading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, "Refresh")
                    }
                    IconButton(onClick = { showConfig = true }) { Icon(Icons.Default.Tune, "Configure") }
                    if (lbDeployed) {
                        IconButton(onClick = { showRemoveConfirm = true }) {
                            Icon(Icons.Default.DeleteOutline, "Remove LB", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Spacer(Modifier.height(4.dp))
                Surface(shape = RoundedCornerShape(14.dp),
                    color = if (lbDeployed) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(if (lbDeployed) "✓ Load Balancing is ACTIVE" else "Load Balancing not configured",
                            fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text(if (lbDeployed) "New connections spread across the uplinks with automatic failover."
                            else "Tap ⚙ to deploy Dual/Triple WAN (PCC weights or priority failover). Runs fully on the router — no cloud needed.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (uplinks.isEmpty() && !isLoading) {
                item {
                    Surface(shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()) {
                        Text("No WAN-capable interfaces found. Connect an ISP cable / PPPoE uplink and refresh.",
                            Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(uplinks, key = { it.iface }) { up -> UplinkCard(up, traffic[up.iface]) }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    if (showConfig) ConfigureLbDialog(viewModel, uplinks, onDismiss = { showConfig = false })
    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("Remove Load Balancing?") },
            text = { Text("All LB-MANAGED mangle/NAT/route rules will be removed. The router keeps its original default route. Your own manual firewall rules are never touched.") },
            confirmButton = { Button(onClick = { showRemoveConfirm = false; viewModel.removeLoadBalancing() }) { Text("Remove") } },
            dismissButton = { OutlinedButton(onClick = { showRemoveConfirm = false }) { Text("Cancel") } }
        )
    }
}
@Composable
private fun UplinkCard(up: UplinkInfo, t: UplinkTraffic?) {
    Surface(shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape)
                    .background(if (up.isOnline) Color(0xFF10B981) else MaterialTheme.colorScheme.error))
                Spacer(Modifier.width(8.dp))
                Text(up.iface, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                Surface(shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
                    Text(up.kind.uppercase() + if (up.hasDefaultRoute) " · MAIN" else "",
                        Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.weight(1f))
                if (up.disabled) Text("disabled", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(6.dp))
            Text("IP: " + (up.address.ifBlank { "— (no address yet)" }) +
                 (if (up.gateway.isNotBlank()) "   GW: ${up.gateway}" else ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (t != null) {
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("↓ ${fmtBps(t.rxSpeedBps)} · ${fmtBytesTotal(t.totalRxBytes)}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text("↑ ${fmtBps(t.txSpeedBps)} · ${fmtBytesTotal(t.totalTxBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigureLbDialog(viewModel: MainViewModel, uplinks: List<UplinkInfo>, onDismiss: () -> Unit) {
    val candidates = uplinks.filter { !it.disabled }
    var strategy by remember { mutableStateOf("pcc") }
    var count by remember { mutableStateOf(minOf(2, candidates.size)) }
    val slots = remember { mutableStateListOf<String?>().apply {
        candidates.take(3).forEach { add(it.iface) }
        while (size < 3) add(null)
    } }
    val weights = remember { mutableStateListOf(2, 1, 1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure Multi-WAN") },
        text = {
            Column {
                Text("Strategy", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = strategy == "pcc", onClick = { strategy = "pcc" },
                        label = { Text("Load Balance (PCC)") })
                    FilterChip(selected = strategy == "failover", onClick = { strategy = "failover" },
                        label = { Text("Failover Priority") })
                }
                Spacer(Modifier.height(10.dp))
                Text("Uplinks", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 3).forEach { n ->
                        FilterChip(selected = count == n,
                            onClick = { if (candidates.size >= n) count = n },
                            enabled = candidates.size >= n,
                            label = { Text(if (n == 2) "Dual WAN" else "Triple WAN") })
                    }
                }
                Spacer(Modifier.height(8.dp))
                for (i in 0 until count) {
                    var expanded by remember { mutableStateOf(false) }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it },
                            modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = slots[i] ?: "— select —",
                                onValueChange = {}, readOnly = true,
                                label = { Text("Uplink ${i + 1}") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodySmall, singleLine = true)
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                candidates.forEach { c ->
                                    DropdownMenuItem(text = { Text("${c.iface} (${c.kind})") }, onClick = {
                                        slots[i] = c.iface
                                        expanded = false
                                    })
                                }
                            }
                        }
                        if (strategy == "pcc") {
                            Spacer(Modifier.width(8.dp))
                            Row {
                                listOf(1, 2, 3, 4).forEach { w ->
                                    FilterChip(selected = weights[i] == w, onClick = { weights[i] = w },
                                        label = { Text("${w}x") },
                                        modifier = Modifier.padding(horizontal = 1.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Text(if (strategy == "pcc")
                        "Weights spread NEW connections by bandwidth share (e.g. 2x+1x = 2:1). Failover is automatic inside every uplink."
                    else
                        "Order = priority: Uplink 1 carries traffic; the next takes over automatically when check-gateway fails.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = {
                val chosen = (0 until count).mapNotNull { slots[it] }.distinct()
                if (chosen.size >= 2) {
                    viewModel.deployLoadBalancing(LoadBalancePlan(
                        strategy = strategy,
                        uplinks = chosen.map { iface ->
                            val c = candidates.firstOrNull { it.iface == iface }
                            UplinkAssignment(iface, c?.gateway ?: "",
                                weights[slots.indexOf(iface).coerceIn(0, 2)])
                        }))
                    onDismiss()
                }
            }) { Text("Deploy") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}