package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.domain.models.PortalTheme
import com.example.ui.components.GlassCard
import com.example.utils.LanguageManager

private fun c(l: Long): Color = Color(l)

private val ALL_THEMES = listOf(
    PortalTheme("emerald-glass","Emerald Glass","Premium emerald tones with frosted glass cards",
        "HotspotManager", 0xFF00E676,0xFF10B981,0xFF059669,0xFF1B5E20,0xFFFFFFFF,true,"","bottom-right"),
    PortalTheme("midnight-purple","Midnight Purple","Deep royal purple with gold accents",
        "HotspotManager", 0xFF7B2FFB,0xFF9333EA,0xFFC084FC,0xFF1E1B4B,0xFFFFFFFF,false,"","right"),
    PortalTheme("sunkissed-orange","Sun-Kissed Orange","Warm amber - sunny welcome",
        "HotspotManager", 0xFFF97316,0xFFEA580C,0xFFF59E0B,0xFF7C2D12,0xFFFFFFFF,false,"","bottom-center"),
    PortalTheme("ocean-blue","Ocean Blue","Coral reef blues with white sand",
        "HotspotManager", 0xFF0EA5E9,0xFF0284C7,0xFF38BDF8,0xFF0C4A6E,0xFFFFFFFF,false,"","right"),
    PortalTheme("cherry-blossom","Cherry Blossom","Soft pink - Myanmar floral art",
        "HotspotManager", 0xFFEC4899,0xFFDB2778,0xFFF472B6,0xFF831843,0xFFFFFFFF,false,"","center"),
    PortalTheme("golden-buddha","Golden Buddha","Sacred gold - Myanmar pagoda tones",
        "HotspotManager", 0xFFF59E0B,0xFFD97706,0xFFFBBF24,0xFF451A03,0xFFFFF3E0,true,"","bottom-center"),
    PortalTheme("cyber-neon","Cyber Neon","Electric cyan and magenta - modern tech",
        "HotspotManager", 0xFF06B6D4,0xFFD946EF,0xFFF72585,0xFF111827,0xFFE0E7FF,false,"","right"),
    PortalTheme("forest-canopy","Forest Canopy","Deep greens and earthy browns",
        "HotspotManager", 0xFF22C55E,0xFF15803D,0xFF4ADE80,0xFF14532D,0xFFFFFFFF,false,"","bottom-right"),
    PortalTheme("sunset-mandalay","Sunset Mandalay","Ruby red fade to dusk - Mandalay colors",
        "HotspotManager", 0xFFEF4444,0xFFDC2626,0xFFF87171,0xFF450A0A,0xFFFFFFFF,true,"","center"),
    PortalTheme("classic-light","Classic Light","Clean white - fast, professional",
        "HotspotManager", 0xFF2563EB,0xFF1D4ED8,0xFFE5E7EB,0xFFFFFFFF,0xFF1F2937,false,"","right")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortalThemeScreen(
    viewModel: MainViewModel,
    navController: NavController
) {
    val strings = LanguageManager.strings
    val isUploading by viewModel.isUploadingPortal.collectAsStateWithLifecycle()
    val activeThemeId by viewModel.activePortalThemeId.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var deployMsg by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Portal Themes", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Choose a premium look for your captive portal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Your data plans & prices appear automatically on the portal when connecting.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))

            if (deployMsg != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (deployMsg!!.startsWith("✓")) Color(0xFF00E676).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (deployMsg!!.startsWith("✓")) Icons.Default.CheckCircle else Icons.Default.Close,
                            contentDescription = null,
                            tint = if (deployMsg!!.startsWith("✓")) Color(0xFF00E676) else Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(deployMsg!!, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            Text("⭐ Featured", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ALL_THEMES.filter { it.isFeatured }.forEach { theme ->
                    PortalThemeCard(
                        theme = theme,
                        isActive = (activeThemeId == theme.id),
                        isUploading = isUploading,
                        onDeploy = {
                            viewModel.deployPortalTheme(theme, context)
                            deployMsg = "✓ Theme '${theme.name}' will be applied on next sync"
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))

            Text("🎨 All Themes (${ALL_THEMES.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ALL_THEMES.filterIndexed { idx, _ -> idx % 2 == 0 }.forEach { theme ->
                        PortalThemeCard(
                            theme = theme,
                            isActive = (activeThemeId == theme.id),
                            isUploading = isUploading,
                            onDeploy = {
                                viewModel.deployPortalTheme(theme, context)
                                deployMsg = "✓ Theme '${theme.name}' will be applied on next sync"
                            }
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ALL_THEMES.filterIndexed { idx, _ -> idx % 2 == 1 }.forEach { theme ->
                        PortalThemeCard(
                            theme = theme,
                            isActive = (activeThemeId == theme.id),
                            isUploading = isUploading,
                            onDeploy = {
                                viewModel.deployPortalTheme(theme, context)
                                deployMsg = "✓ Theme '${theme.name}' will be applied on next sync"
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
@Composable
private fun PortalThemeCard(
    theme: PortalTheme,
    isActive: Boolean,
    isUploading: Boolean,
    onDeploy: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Color swatch
            Row(modifier = Modifier.fillMaxWidth().height(8.dp)) {
                Surface(shape = RoundedCornerShape(0.dp), color = c(theme.primaryColor), modifier = Modifier.weight(1f).fillMaxHeight()) { Box(modifier = Modifier.fillMaxSize()) }
                Surface(shape = RoundedCornerShape(0.dp), color = c(theme.secondaryColor), modifier = Modifier.weight(1f).fillMaxHeight()) { Box(modifier = Modifier.fillMaxSize()) }
                Surface(shape = RoundedCornerShape(0.dp), color = c(theme.accentColor), modifier = Modifier.weight(1f).fillMaxHeight()) { Box(modifier = Modifier.fillMaxSize()) }
                Surface(shape = RoundedCornerShape(0.dp), color = c(theme.surfaceColor), modifier = Modifier.weight(1f).fillMaxHeight()) { Box(modifier = Modifier.fillMaxSize()) }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(theme.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (theme.isFeatured) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFF59E0B)) {
                        Text("FEATURED", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                    }
                }
                if (isActive) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF00E676)) {
                        Text("✓ DEPLOYED", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                    }
                }
            }
            Text(theme.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = c(theme.primaryColor), modifier = Modifier.size(10.dp)) { Box(modifier = Modifier) }
                Spacer(modifier = Modifier.width(2.dp))
                Surface(shape = CircleShape, color = c(theme.surfaceColor), modifier = Modifier.size(10.dp)) { Box(modifier = Modifier) }
                Spacer(modifier = Modifier.width(6.dp))
                Text("📊 auto price list • 📶 auto SSID", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onDeploy,
                modifier = Modifier.fillMaxWidth().height(34.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) Color(0xFF059669) else c(theme.primaryColor).copy(alpha = 0.25f)
                ),
                enabled = !isUploading
            ) {
                if (isUploading && isActive) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                }
                Text(
                    if (isActive) "✓ Deployed" else "Deploy on Router",
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) Color.White else c(theme.primaryColor)
                )
            }
        }
    }
}