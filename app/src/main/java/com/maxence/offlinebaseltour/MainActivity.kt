package com.maxence.offlinebaseltour

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.maxence.offlinebaseltour.managers.MapManager
import com.maxence.offlinebaseltour.routing.RoutingManager
import com.maxence.offlinebaseltour.screens.HistoryScreen
import com.maxence.offlinebaseltour.screens.MapScreen
import com.maxence.offlinebaseltour.screens.PreparationScreen
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var routingManager: RoutingManager
    private lateinit var mapManager: MapManager
    
    private val requiredPermissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    // Observé par l'écran Carte : sans cela, le GPS ne démarrait jamais au tout premier
    // lancement (la permission est accordée après la composition initiale).
    private var locationGranted by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> refreshLocationPermission() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        mapManager = MapManager(this)
        routingManager = RoutingManager(this)

        refreshLocationPermission()
        checkAndRequestPermissions()

        setContent {
            MaterialTheme {
                MainNavigation(routingManager, mapManager, locationGranted)
            }
        }
    }

    private fun refreshLocationPermission() {
        locationGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkAndRequestPermissions() {
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }
}

@Composable
fun MainNavigation(routingManager: RoutingManager, mapManager: MapManager, locationGranted: Boolean) {
    val navController = rememberNavController()
    var selectedItem by remember { mutableStateOf(0) }
    
    data class NavItem(val title: String, val route: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
    
    val items = listOf(
        NavItem("Carte", "map", Icons.Default.Map),
        NavItem("Préparation", "prep", Icons.Default.Build),
        NavItem("Historique", "history", Icons.Default.List)
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                        selected = selectedItem == index,
                        onClick = {
                            selectedItem = index
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId)
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController, startDestination = "map", Modifier.padding(innerPadding)) {
            composable("map") { MapScreen(routingManager, mapManager, locationGranted) }
            composable("prep") { PreparationScreen(routingManager, mapManager) }
            composable("history") { HistoryScreen() }
        }
    }
}
