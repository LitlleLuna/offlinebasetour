package com.maxence.offlinebaseltour.screens

import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.Build
import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.maxence.offlinebaseltour.data.PoiRepository
import com.maxence.offlinebaseltour.data.TouristSpot
import com.maxence.offlinebaseltour.managers.MapManager
import com.maxence.offlinebaseltour.routing.RouteResult
import com.maxence.offlinebaseltour.routing.RoutingManager
import com.maxence.offlinebaseltour.services.GpsTrackingService
import com.maxence.offlinebaseltour.services.TrackingManager
import com.maxence.offlinebaseltour.services.TrackingState
import org.mapsforge.core.graphics.Color
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.datastore.MultiMapDataStore
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.overlay.Marker
import org.mapsforge.map.layer.overlay.Polyline
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.InternalRenderTheme
import kotlinx.coroutines.launch
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(routingManager: RoutingManager, mapManager: MapManager, locationGranted: Boolean) {
    val context = LocalContext.current
    var mapView: MapView? by remember { mutableStateOf(null) }
    var locationMarker: Marker? by remember { mutableStateOf(null) }
    var userLocation by remember { mutableStateOf<LatLong?>(null) }
    var locationAccuracy by remember { mutableStateOf<Float?>(null) }
    var hasOfflineMap by remember { mutableStateOf(false) }
    
    var routingMode by remember { mutableStateOf("car") }
    var currentRoute by remember { mutableStateOf<RouteResult?>(null) }
    var currentInstructionIndex by remember { mutableIntStateOf(0) }
    var routeLayer: Polyline? by remember { mutableStateOf(null) }
    
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<TouristSpot>>(emptyList()) }
    var isSearchActive by remember { mutableStateOf(false) }

    // Observation de l'état global du Tracking
    val trackingState by TrackingManager.trackingState.collectAsState()
    val trackingDistance by TrackingManager.currentDistance.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    
    // Localisation GNSS Android native : aucune donnée mobile n'est requise.
    val locationManager = remember { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    var lastRerouteAt by remember { mutableStateOf(0L) }

    // Calcul d'itinéraire avec retour utilisateur (avant : échec silencieux).
    fun requestRoute(from: LatLong?, to: LatLong) {
        if (from == null) {
            Toast.makeText(context, "Position GPS inconnue : attendez la réception d'un signal GPS.", Toast.LENGTH_LONG).show()
            return
        }
        coroutineScope.launch {
            val res = routingManager.calculateRoute(from, to, routingMode)
            if (res == null) {
                Toast.makeText(
                    context,
                    "Itinéraire impossible : générez le graphe routier (onglet Préparation) et vérifiez que le trajet est couvert.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                currentRoute = res
                currentInstructionIndex = 0
                updateRouteLayer(mapView, res) { routeLayer = it }
            }
        }
    }

    // La vue Mapsforge n'est détruite qu'à la sortie de l'écran (et pas quand la permission change).
    DisposableEffect(Unit) {
        onDispose { mapView?.destroyAll() }
    }

    DisposableEffect(context, locationGranted) {
        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                val latLong = LatLong(loc.latitude, loc.longitude)
                userLocation = latLong
                locationAccuracy = loc.accuracy
                locationMarker?.latLong = latLong
                mapView?.layerManager?.redrawLayers()

                val activeRoute = currentRoute
                if (activeRoute != null && activeRoute.path.isNotEmpty() && routingManager.isReady) {
                    val remainingInstructions = activeRoute.instructions.withIndex()
                        .filter { it.index >= currentInstructionIndex && it.value.path.isNotEmpty() }
                    val closestInstruction = remainingInstructions.minByOrNull {
                        distanceToRoute(latLong, it.value.path)
                    }
                    if (closestInstruction != null && closestInstruction.index >= currentInstructionIndex) {
                        currentInstructionIndex = closestInstruction.index
                    }

                    val now = System.currentTimeMillis()
                    val deviation = distanceToRoute(latLong, activeRoute.path)
                    if (deviation > 50.0 && now - lastRerouteAt > 15000L) {
                        lastRerouteAt = now
                        val destination = activeRoute.path.last()
                        coroutineScope.launch {
                            val newRoute = routingManager.calculateRoute(latLong, destination, routingMode)
                            if (newRoute != null) {
                                currentRoute = newRoute
                                currentInstructionIndex = 0
                                updateRouteLayer(mapView, newRoute) { routeLayer = it }
                            }
                        }
                    }
                }
            }

            @Deprecated("Deprecated by Android")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }

        val hasFine = locationGranted &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine) {
            try {
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER, 3000L, 2f, listener, Looper.getMainLooper()
                    )
                }
            } catch (_: SecurityException) {
            } catch (_: IllegalArgumentException) {
            }
        }

        onDispose {
            try { locationManager.removeUpdates(listener) } catch (_: SecurityException) {}
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            SearchBar(
                query = searchQuery,
                onQueryChange = { 
                    searchQuery = it
                    searchResults = PoiRepository.search(it)
                },
                onSearch = { isSearchActive = false },
                active = isSearchActive,
                onActiveChange = { isSearchActive = it },
                placeholder = { Text("Rechercher un lieu (Hors ligne)") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (isSearchActive) {
                        IconButton(onClick = { 
                            if (searchQuery.isNotEmpty()) searchQuery = "" else isSearchActive = false 
                        }) { Icon(Icons.Default.Close, contentDescription = null) }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            ) {
                if (searchResults.isEmpty() && searchQuery.isNotEmpty()) {
                    Text("Aucun lieu trouvé.", modifier = Modifier.padding(16.dp))
                } else {
                    LazyColumn {
                        items(searchResults) { spot ->
                            ListItem(
                                headlineContent = { Text(spot.name) },
                                supportingContent = { Text(spot.category) },
                                modifier = Modifier.clickable {
                                    isSearchActive = false
                                    mapView?.model?.mapViewPosition?.setCenter(spot.location)
                                    mapView?.model?.mapViewPosition?.setZoomLevel(16.toByte())
                                    
                                    requestRoute(userLocation, spot.location)
                                }
                            )
                        }
                    }
                }
            }
            
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
                SegmentedButtonSingleSelect(
                    options = listOf("Voiture" to Icons.Default.DirectionsCar, "Piéton" to Icons.Default.DirectionsWalk),
                    selectedOption = if (routingMode == "car") 0 else 1
                ) { index ->
                    routingMode = if (index == 0) "car" else "foot"
                    currentRoute = null
                    routeLayer?.let { mapView?.layerManager?.layers?.remove(it) }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            isClickable = true
                            setBuiltInZoomControls(false)
                            
                            val tileCache: TileCache = AndroidUtil.createTileCache(
                                ctx, "mapcache", model.displayModel.tileSize, 1f, model.frameBufferModel.overdrawFactor
                            )
                            
                            val mapFiles = mapManager.getAllDownloadedMapFiles()
                            val multiMapDataStore = MultiMapDataStore(MultiMapDataStore.DataPolicy.RETURN_ALL)
                            var loadedMapCount = 0
                            mapFiles.forEach { file ->
                                try {
                                    multiMapDataStore.addMapDataStore(MapFile(file), false, false)
                                    loadedMapCount++
                                } catch (_: Exception) {
                                    // Une archive invalide ne doit pas empêcher le chargement des autres cartes.
                                }
                            }
                            hasOfflineMap = loadedMapCount > 0
                            if (hasOfflineMap) {
                                val tileRendererLayer = TileRendererLayer(
                                    tileCache, multiMapDataStore, model.mapViewPosition, AndroidGraphicFactory.INSTANCE
                                )
                                tileRendererLayer.setXmlRenderTheme(InternalRenderTheme.DEFAULT)
                                layerManager.layers.add(tileRendererLayer)
                                model.mapViewPosition.setCenter(LatLong(47.5596, 7.5886))
                                model.mapViewPosition.setZoomLevel(13.toByte())
                            }

                            val graphic = AndroidGraphicFactory.convertToBitmap(
                                BitmapDrawable(ctx.resources, createBlueDotBitmap())
                            )
                            locationMarker = Marker(LatLong(0.0, 0.0), graphic, 0, 0)
                            layerManager.layers.add(locationMarker)

                            mapView = this
                        }
                    }
                )
                
                Icon(
                    Icons.Default.Close, 
                    contentDescription = "Centre", 
                    modifier = Modifier.align(Alignment.Center).size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Button(
                    onClick = {
                        val target = mapView?.model?.mapViewPosition?.center
                        if (target != null) requestRoute(userLocation, target)
                    },
                    modifier = Modifier.align(Alignment.Center).padding(top = 80.dp)
                ) {
                    Text("Naviguer vers ce point")
                }

                if (!hasOfflineMap) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f),
                        modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "Aucune carte hors ligne valide. Téléchargez ou importez un fichier Mapsforge .map.",
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Affichage Précision GPS
                locationAccuracy?.let { acc ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("Précision: ${acc.toInt()}m", modifier = Modifier.padding(4.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
                
                FloatingActionButton(
                    onClick = {
                        userLocation?.let {
                            mapView?.model?.mapViewPosition?.setCenter(it)
                            mapView?.model?.mapViewPosition?.setZoomLevel(16.toByte())
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(Icons.Default.LocationSearching, contentDescription = "Ma Position")
                }
            }

            // Panneau Navigation
            currentRoute?.let { route ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Distance: ${"%.1f".format(route.distanceMetres / 1000)} km", fontWeight = FontWeight.Bold)
                            Text("Durée: ${route.timeMillis / 60000} min", fontWeight = FontWeight.Bold)
                        }
                        
                        val nextInstruction = route.instructions.getOrNull(currentInstructionIndex)
                        if (nextInstruction != null) {
                            val remaining = if (nextInstruction.path.isNotEmpty()) {
                                userLocation?.let { remainingDistanceOnPath(it, nextInstruction.path) }
                                    ?: nextInstruction.distanceMetres
                            } else nextInstruction.distanceMetres
                            Text(
                                "${nextInstruction.text} — environ ${remaining.toInt()} m",
                                modifier = Modifier.padding(vertical = 4.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        } else {
                            Text("Itinéraire calculé. Suivez le tracé sur la carte.")
                        }

                        Button(
                            onClick = { 
                                currentRoute = null 
                                routeLayer?.let { mapView?.layerManager?.layers?.remove(it) }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Annuler Navigation")
                        }
                    }
                }
            }

            // Panneau Contrôle de Suivi GPX
            Card(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when(trackingState) {
                        TrackingState.RECORDING -> MaterialTheme.colorScheme.errorContainer
                        TrackingState.PAUSED -> MaterialTheme.colorScheme.tertiaryContainer
                        TrackingState.STOPPED -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = when(trackingState) {
                            TrackingState.RECORDING -> "Enregistrement en cours : ${"%.2f".format(trackingDistance/1000)} km"
                            TrackingState.PAUSED -> "Enregistrement en pause : ${"%.2f".format(trackingDistance/1000)} km"
                            TrackingState.STOPPED -> "Enregistrement GPX arrêté"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        when (trackingState) {
                            TrackingState.STOPPED -> {
                                Button(onClick = { sendServiceCommand(context, GpsTrackingService.ACTION_START) }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Démarrer")
                                }
                            }
                            TrackingState.RECORDING -> {
                                Button(onClick = { sendServiceCommand(context, GpsTrackingService.ACTION_PAUSE) }) {
                                    Icon(Icons.Default.Pause, contentDescription = null)
                                    Text("Pause")
                                }
                                Button(
                                    onClick = { sendServiceCommand(context, GpsTrackingService.ACTION_STOP) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null)
                                    Text("Arrêter")
                                }
                            }
                            TrackingState.PAUSED -> {
                                Button(onClick = { sendServiceCommand(context, GpsTrackingService.ACTION_RESUME) }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Text("Reprendre")
                                }
                                Button(
                                    onClick = { sendServiceCommand(context, GpsTrackingService.ACTION_STOP) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null)
                                    Text("Arrêter")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun sendServiceCommand(context: Context, action: String) {
    if (action == GpsTrackingService.ACTION_START &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
    ) {
        Toast.makeText(context, "L'autorisation de localisation précise est nécessaire pour enregistrer un trajet.", Toast.LENGTH_LONG).show()
        return
    }
    val intent = Intent(context, GpsTrackingService::class.java).apply { this.action = action }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && action == GpsTrackingService.ACTION_START) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

fun updateRouteLayer(mapView: MapView?, route: RouteResult?, onLayerCreated: (Polyline) -> Unit) {
    if (mapView == null) return
    val layersToRemove = mapView.layerManager.layers.filterIsInstance<Polyline>()
    layersToRemove.forEach { mapView.layerManager.layers.remove(it) }
    
    if (route != null) {
        val paint = AndroidGraphicFactory.INSTANCE.createPaint()
        paint.color = AndroidGraphicFactory.INSTANCE.createColor(Color.BLUE)
        paint.strokeWidth = 14f
        paint.setStyle(Style.STROKE)
        val polyline = Polyline(paint, AndroidGraphicFactory.INSTANCE)
        val latLongs = route.path.map { LatLong(it.latitude, it.longitude) }
        polyline.addPoints(latLongs)
        mapView.layerManager.layers.add(polyline)
        onLayerCreated(polyline)
        mapView.layerManager.redrawLayers()
    }
}

fun remainingDistanceOnPath(location: LatLong, path: List<LatLong>): Double {
    if (path.isEmpty()) return 0.0
    var nearestIndex = 0
    var nearestDistance = Double.MAX_VALUE
    path.forEachIndexed { index, point ->
        val distance = haversineMetres(location, point)
        if (distance < nearestDistance) {
            nearestDistance = distance
            nearestIndex = index
        }
    }
    var remaining = nearestDistance
    for (index in nearestIndex until path.lastIndex) {
        remaining += haversineMetres(path[index], path[index + 1])
    }
    return remaining
}

private fun haversineMetres(a: LatLong, b: LatLong): Double {
    val earthRadius = 6371000.0
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2).let { it * it } +
        cos(lat1) * cos(lat2) * sin(dLon / 2).let { it * it }
    return earthRadius * 2.0 * atan2(sqrt(h), sqrt(1.0 - h))
}

fun distanceToRoute(loc: LatLong, path: List<LatLong>): Double {
    if (path.isEmpty()) return Double.MAX_VALUE
    if (path.size == 1) return haversineMetres(loc, path.first())

    // Distance point-segment en projection locale : plus fiable que la seule
    // distance aux sommets, surtout lorsque les points OSM sont espacés.
    val latitudeScale = 111_320.0
    val longitudeScale = latitudeScale * cos(Math.toRadians(loc.latitude)).coerceAtLeast(0.01)
    val px = (loc.longitude) * longitudeScale
    val py = (loc.latitude) * latitudeScale
    var minimum = Double.MAX_VALUE
    for (index in 0 until path.lastIndex) {
        val a = path[index]
        val b = path[index + 1]
        val ax = a.longitude * longitudeScale
        val ay = a.latitude * latitudeScale
        val bx = b.longitude * longitudeScale
        val by = b.latitude * latitudeScale
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared <= 0.0001) 0.0 else
            (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0.0, 1.0)
        val nearestX = ax + t * dx
        val nearestY = ay + t * dy
        minimum = min(minimum, hypot(px - nearestX, py - nearestY))
    }
    return minimum
}

@Composable
fun SegmentedButtonSingleSelect(
    options: List<Pair<String, androidx.compose.ui.graphics.vector.ImageVector>>,
    selectedOption: Int,
    onOptionSelected: (Int) -> Unit
) {
    Row(modifier = Modifier.padding(4.dp)) {
        options.forEachIndexed { index, option ->
            val isSelected = selectedOption == index
            Button(
                onClick = { onOptionSelected(index) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(
                    topStart = if (index == 0) 12.dp else 0.dp,
                    bottomStart = if (index == 0) 12.dp else 0.dp,
                    topEnd = if (index == options.size - 1) 12.dp else 0.dp,
                    bottomEnd = if (index == options.size - 1) 12.dp else 0.dp
                ),
                modifier = Modifier.padding(end = 1.dp)
            ) {
                Icon(option.second, contentDescription = option.first, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(option.first)
            }
        }
    }
}

fun createBlueDotBitmap(): Bitmap {
    val size = 40
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply { color = android.graphics.Color.BLUE; style = Paint.Style.FILL; isAntiAlias = true }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    val paintWhite = Paint().apply { color = android.graphics.Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 4f; isAntiAlias = true }
    canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - 2f, paintWhite)
    return bitmap
}
