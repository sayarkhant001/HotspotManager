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
    val repository = AppRepository(database.routerDao(), mikrotikClient)

    setContent {
      MyApplicationTheme {
        com.example.ui.components.LiquidGlassBackground {
          AppNavigation(repository = repository)
        }
      }
    }
  }
}

