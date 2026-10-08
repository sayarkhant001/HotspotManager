package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.data.local.AppDatabase
import com.example.data.remote.MikrotikClient
import com.example.data.repository.AppRepository
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    val database = AppDatabase.getDatabase(this)
    val mikrotikClient = MikrotikClient()
    val repository = AppRepository(database.routerDao(), mikrotikClient, applicationContext)
    com.example.utils.LanguageManager.init(this)
    com.example.data.remote.CloudApiClient.init(this)

    // Ensure Bluetooth permissions are granted on Android 12+ so paired thermal printers are immediately visible
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        val permissions = arrayOf(
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.BLUETOOTH_SCAN
        )
        val needed = permissions.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            androidx.core.app.ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1001)
        }
    }

    val initialRoute = intent.getStringExtra("route")

    setContent {
      MyApplicationTheme {
        com.example.ui.components.LiquidGlassBackground {
          AppNavigation(repository = repository, startRoute = initialRoute)
        }
      }
    }
  }
}

