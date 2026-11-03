package com.ekko.smartfan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ekko.smartfan.data.FanMqtt
import com.ekko.smartfan.ui.CountdownScreen
import com.ekko.smartfan.ui.HomeScreen
import com.ekko.smartfan.ui.SettingsScreen
import com.ekko.smartfan.ui.TimerScreen
import com.ekko.smartfan.ui.theme.SmartFanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        FanMqtt.loadConfig(this)
        FanMqtt.connect()
        setContent {
            SmartFanTheme {
                AppRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        FanMqtt.loadConfig(this)
        FanMqtt.connect()
    }
}

private data class Tab(val route: String, val label: String, val icon: @Composable () -> Unit)

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route ?: "home"

    val tabs = listOf(
        Tab("home", "控制") { Icon(Icons.Filled.Air, contentDescription = null) },
        Tab("timer", "定时") { Icon(Icons.Filled.Schedule, contentDescription = null) },
        Tab("countdown", "倒计时") { Icon(Icons.Filled.Timer, contentDescription = null) }
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = tab.icon,
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { pad ->
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.padding(pad)
        ) {
            composable("home") {
                HomeScreen(onOpenSettings = { nav.navigate("settings") })
            }
            composable("timer") { TimerScreen() }
            composable("countdown") { CountdownScreen() }
            composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
