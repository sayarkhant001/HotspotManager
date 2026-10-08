package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.CloudApiClient
import com.example.ui.components.GlassCard
import com.example.utils.AppLanguage
import com.example.utils.LanguageManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: MainViewModel,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val currentLang by LanguageManager.currentLanguage
    val strings = LanguageManager.strings
    val prefs = remember { context.getSharedPreferences("hotspot_login_prefs", Context.MODE_PRIVATE) }

    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val cloudLoginStatus by viewModel.cloudLoginStatus.collectAsStateWithLifecycle()

    // Tab 0 = Cloud Account (Admin Assigned), Tab 1 = Direct Router (MikroTik IP)
    var selectedTab by remember { mutableStateOf(prefs.getInt("login_tab", 0)) }

    // Cloud Credentials (prefill from prefs or saved user)
    val savedCloudUser = prefs.getString("cloud_username", CloudApiClient.getSavedUser(context)?.username ?: "") ?: ""
    val savedCloudPass = prefs.getString("cloud_password", "") ?: ""
    var cloudUser by remember { mutableStateOf(savedCloudUser) }
    var cloudPass by remember { mutableStateOf(savedCloudPass) }
    var rememberCloudPassword by remember { mutableStateOf(prefs.getBoolean("remember_cloud_password", true)) }
    var isCloudPassVisible by remember { mutableStateOf(false) }

    // Direct Router Credentials
    var ip by remember { mutableStateOf(prefs.getString("router_ip", "10.10.10.1") ?: "10.10.10.1") }
    var user by remember { mutableStateOf(prefs.getString("router_user", "admin") ?: "admin") }
    var pass by remember { mutableStateOf(prefs.getString("router_pass", "Khant1234@") ?: "Khant1234@") }
    var rememberDirectPassword by remember { mutableStateOf(prefs.getBoolean("remember_password", true)) }
    var isDirectPassVisible by remember { mutableStateOf(false) }

    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            if (selectedTab == 0) {
                val editor = prefs.edit()
                    .putInt("login_tab", 0)
                    .putString("cloud_username", cloudUser)
                    .putBoolean("remember_cloud_password", rememberCloudPassword)
                if (rememberCloudPassword) {
                    editor.putString("cloud_password", cloudPass)
                } else {
                    editor.remove("cloud_password")
                }
                editor.apply()
            } else {
                val editor = prefs.edit()
                    .putInt("login_tab", 1)
                    .putString("router_ip", ip)
                    .putString("router_user", user)
                    .putBoolean("remember_password", rememberDirectPassword)
                if (rememberDirectPassword) {
                    editor.putString("router_pass", pass)
                } else {
                    editor.remove("router_pass")
                }
                editor.apply()
            }
            onLoginSuccess()
        }
    }

    Scaffold(
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top language switcher
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        FilledTonalButton(
                            onClick = { LanguageManager.toggleLanguage(context) },
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                text = if (currentLang == AppLanguage.MYANMAR) "🇲🇲 မြန်မာ" else "🇬🇧 English",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // App Logo & Title
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.app_logo),
                        contentDescription = "Hotspot Manager Logo",
                        modifier = Modifier
                            .size(62.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = strings.appName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (selectedTab == 0) strings.cloudLoginSub else strings.directConnectSub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
                    )

                    // Modern Pill Segmented Tab Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(4.dp)
                    ) {
                        Surface(
                            onClick = { selectedTab = 0 },
                            shape = RoundedCornerShape(9.dp),
                            color = if (selectedTab == 0) MaterialTheme.colorScheme.primary else Color.Transparent,
                            contentColor = if (selectedTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = strings.cloudLoginTab,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }

                        Surface(
                            onClick = { selectedTab = 1 },
                            shape = RoundedCornerShape(9.dp),
                            color = if (selectedTab == 1) MaterialTheme.colorScheme.primary else Color.Transparent,
                            contentColor = if (selectedTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Router, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = strings.directRouterTab,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (selectedTab == 0) {
                        // ====================================================
                        // TAB 0: CLOUD ACCOUNT LOGIN (Admin Assigned)
                        // ====================================================
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = strings.cloudAccountBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        OutlinedTextField(
                            value = cloudUser,
                            onValueChange = { cloudUser = it },
                            label = { Text(strings.cloudUsernameLabel, style = MaterialTheme.typography.bodySmall) },
                            placeholder = { Text("e.g. kolwinmaung", style = MaterialTheme.typography.bodySmall) },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp))
                            },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = cloudPass,
                            onValueChange = { cloudPass = it },
                            label = { Text(strings.cloudPasswordLabel, style = MaterialTheme.typography.bodySmall) },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                            },
                            trailingIcon = {
                                IconButton(onClick = { isCloudPassVisible = !isCloudPassVisible }) {
                                    Icon(
                                        imageVector = if (isCloudPassVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (isCloudPassVisible) "Hide password" else "Show password",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            visualTransformation = if (isCloudPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                autoCorrectEnabled = false
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = rememberCloudPassword,
                                onCheckedChange = { rememberCloudPassword = it }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = strings.rememberPassword,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                    } else {
                        // ====================================================
                        // TAB 1: DIRECT ROUTEROS LOGIN (IP / Admin User)
                        // ====================================================
                        OutlinedTextField(
                            value = ip,
                            onValueChange = { ip = it },
                            label = { Text(strings.routerIp, style = MaterialTheme.typography.bodySmall) },
                            placeholder = { Text("10.10.10.1", style = MaterialTheme.typography.bodySmall) },
                            leadingIcon = {
                                Icon(Icons.Default.Router, contentDescription = null, modifier = Modifier.size(20.dp))
                            },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = user,
                            onValueChange = { user = it },
                            label = { Text(strings.username, style = MaterialTheme.typography.bodySmall) },
                            placeholder = { Text("admin", style = MaterialTheme.typography.bodySmall) },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp))
                            },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = pass,
                            onValueChange = { pass = it },
                            label = { Text(strings.password, style = MaterialTheme.typography.bodySmall) },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                            },
                            trailingIcon = {
                                IconButton(onClick = { isDirectPassVisible = !isDirectPassVisible }) {
                                    Icon(
                                        imageVector = if (isDirectPassVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (isDirectPassVisible) "Hide password" else "Show password",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            visualTransformation = if (isDirectPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                autoCorrectEnabled = false
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = rememberDirectPassword,
                                onCheckedChange = { rememberDirectPassword = it }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = strings.rememberPassword,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Progress Status Message
                    if (authState is AuthState.Loading && cloudLoginStatus != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = cloudLoginStatus ?: strings.cloudAuthenticating,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Error Message
                    if (authState is AuthState.Error) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = (authState as AuthState.Error).message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    // Main Action Button
                    Button(
                        onClick = {
                            if (selectedTab == 0) {
                                viewModel.loginToCloud(context, cloudUser, cloudPass)
                            } else {
                                viewModel.connectToRouter(ip, user, pass)
                            }
                        },
                        enabled = authState !is AuthState.Loading && (
                            if (selectedTab == 0) cloudUser.isNotBlank() && cloudPass.isNotBlank()
                            else ip.isNotBlank() && user.isNotBlank()
                        ),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (authState is AuthState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.connecting, style = MaterialTheme.typography.labelLarge)
                        } else {
                            Icon(
                                if (selectedTab == 0) Icons.Default.Cloud else Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (selectedTab == 0) strings.cloudLoginButton else strings.directConnectButton,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    if (selectedTab == 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Cloud Controller: 3.84.81.152:8750",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
