package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.domain.models.APP_SERVICE_CATEGORIES
import com.example.domain.models.APP_SERVICE_GROUPS
import com.example.utils.LanguageManager

/**
 * Card composable — must be defined BEFORE the screen that uses it.
 */
@Composable
private fun AppFlowCard(
    service: com.example.domain.models.AppServiceCategory,
    isBlocked: Boolean,
    onToggle: () -> Unit
) {
    val cardColor = if (isBlocked) Color(0xFFEF4444).copy(alpha = 0.12f) else Color.Transparent
    val borderColor = if (isBlocked) Color(0xFFEF4444).copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    Surface(
        shape = RoundedCornerShape(12.dp), color = cardColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                    modifier = Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(service.icon, style = MaterialTheme.typography.bodyMedium, fontSize = 18.sp)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(service.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(service.description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Switch(checked = isBlocked, onCheckedChange = { onToggle() })
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppFlowControlScreen(viewModel: MainViewModel, navController: NavController) {
    val strings = LanguageManager.strings
    var blockedSet by remember { mutableStateOf(setOf<String>()) }
    var deployMsg by remember { mutableStateOf<String?>(null) }
    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0), topBar = {
        TopAppBar(title = { Text("App Flow Control", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
            } },
            actions = {
                TextButton(onClick = {
                    viewModel.deployServiceBlocks(blockedSet)
                    deployMsg = "✓ ${blockedSet.size} services blocked"
                }) {
                    Icon(Icons.Default.Shield, contentDescription = "Apply", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Apply Blocks", fontWeight = FontWeight.Bold)
                }
            })
    }) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(text = "Toggle ON = traffic blocked by router firewall.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            if (deployMsg != null) {
                Surface(shape = RoundedCornerShape(10.dp),
                    color = if (deployMsg!!.startsWith("✓")) Color(0xFF00E676).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (deployMsg!!.startsWith("✓")) Icons.Default.CheckCircle else Icons.Default.Close,
                            contentDescription = null,
                            tint = if (deployMsg!!.startsWith("✓")) Color(0xFF00E676) else Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(deployMsg!!, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            APP_SERVICE_GROUPS.forEach { group ->
                Text(text = group, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(6.dp))
                APP_SERVICE_CATEGORIES.filter { it.group == group }.forEach { svc ->
                    AppFlowCard(service = svc, isBlocked = (svc.id in blockedSet), onToggle = {
                        if (svc.id in blockedSet) blockedSet = blockedSet - svc.id
                        else blockedSet = blockedSet + svc.id
                        deployMsg = null
                    })
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}