package com.maxence.offlinebaseltour.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.maxence.offlinebaseltour.BaselTourApp
import com.maxence.offlinebaseltour.database.Track
import com.maxence.offlinebaseltour.database.TrackPoint
import com.maxence.offlinebaseltour.services.TrackingManager
import com.maxence.offlinebaseltour.services.TrackingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tracks = remember { mutableStateListOf<Track>() }
    var selectedForDelete by remember { mutableStateOf<Track?>(null) }
    var message by remember { mutableStateOf("") }

    suspend fun reload() {
        val result = withContext(Dispatchers.IO) { BaselTourApp.database.trackDao().getAllTracks() }
        tracks.clear()
        tracks.addAll(result)
    }

    LaunchedEffect(Unit) { reload() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Historique des trajets", style = MaterialTheme.typography.headlineMedium)
        if (message.isNotBlank()) {
            Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))
        }
        if (tracks.isEmpty()) {
            Text("Aucun trajet enregistré pour le moment.", modifier = Modifier.padding(top = 16.dp))
        } else {
            LazyColumn {
                items(tracks, key = { it.id }) { track ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(track.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${String.format(Locale.getDefault(), "%.2f", track.distanceMetres / 1000f)} km · " +
                                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(track.startTime)),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                if (track.endTime == null) "Trajet interrompu ou encore ouvert" else "Terminé",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    scope.launch {
                                        try {
                                            val file = withContext(Dispatchers.IO) { exportTrackAsGpx(track) }
                                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/gpx+xml"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Exporter le parcours GPX"))
                                            message = "Fichier GPX prêt à être partagé."
                                        } catch (e: Exception) {
                                            message = "Erreur d'export GPX : ${e.message ?: "inconnue"}"
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.Share, contentDescription = null)
                                    Text("Exporter GPX")
                                }
                                OutlinedButton(onClick = {
                                    if (track.endTime == null && TrackingManager.trackingState.value != TrackingState.STOPPED) {
                                        message = "Arrêtez l'enregistrement avant de supprimer ce trajet."
                                    } else {
                                        selectedForDelete = track
                                    }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                    Text("Supprimer")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedForDelete?.let { track ->
        AlertDialog(
            onDismissRequest = { selectedForDelete = null },
            title = { Text("Supprimer ce trajet ?") },
            text = { Text("Le trajet et tous ses points GPS seront supprimés définitivement.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            val dao = BaselTourApp.database.trackDao()
                            dao.deletePointsForTrack(track.id)
                            dao.deleteTrack(track.id)
                        }
                        selectedForDelete = null
                        reload()
                        message = "Trajet supprimé."
                    }
                }) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { selectedForDelete = null }) { Text("Annuler") }
            }
        )
    }
}

private suspend fun exportTrackAsGpx(track: Track): File = withContext(Dispatchers.IO) {
    val points: List<TrackPoint> = BaselTourApp.database.trackDao().getPointsForTrack(track.id)
    if (points.isEmpty()) error("Ce trajet ne contient aucun point GPS exportable.")
    val app = BaselTourApp.instance
    val exportDir = File(app.getExternalFilesDir(null), "GPX")
    if (!exportDir.exists() && !exportDir.mkdirs()) error("Impossible de créer le dossier GPX")
    val safeName = track.name.replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').ifBlank { "trajet" }
    val file = File(exportDir, "${safeName}_${track.id}.gpx")
    val utc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val xml = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<gpx version=\"1.1\" creator=\"Basel Tour Offline\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        append("<metadata><name>${track.name.xmlEscape()}</name><time>${utc.format(Date(track.startTime))}</time></metadata>\n")
        append("<trk><name>${track.name.xmlEscape()}</name><trkseg>\n")
        points.forEach { point ->
            append("<trkpt lat=\"${String.format(Locale.US, "%.7f", point.latitude)}\" lon=\"${String.format(Locale.US, "%.7f", point.longitude)}\">")
            append("<time>${utc.format(Date(point.time))}</time></trkpt>\n")
        }
        append("</trkseg></trk></gpx>")
    }
    file.writeText(xml, Charsets.UTF_8)
    file
}

private fun String.xmlEscape(): String =
    filter { it == '\t' || it == '\n' || it == '\r' || it >= ' ' }
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")
