package com.maxence.offlinebaseltour.screens

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.maxence.offlinebaseltour.managers.MapManager
import com.maxence.offlinebaseltour.routing.RoutingManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.DataInputStream

private data class PendingDownload(val label: String, val regionId: String, val isMap: Boolean)

@Composable
fun PreparationScreen(routingManager: RoutingManager, mapManager: MapManager) {
    val context = LocalContext.current
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var isCompilingGraph by remember { mutableStateOf(false) }
    var graphStatus by remember { mutableStateOf(if (routingManager.isReady) "Graphe routier chargé" else "Aucun graphe routier chargé") }
    var importStatus by remember { mutableStateOf("") }
    val pendingDownloads = remember { mutableStateMapOf<Long, PendingDownload>() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(routingManager) {
        graphStatus = if (routingManager.loadExistingGraph()) {
            "Graphe routier chargé et prêt."
        } else {
            "Aucun graphe routier prêt."
        }
    }

    fun refreshUI() { refreshTrigger++ }

    val mapPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    copyFileToInternalStorage(context, it, File(mapManager.mapsDir, "imported.map"), "map")
                }
                importStatus = if (ok) "Carte importée. Vérifiez qu'il s'agit d'un fichier Mapsforge .map valide." else "Échec de l'importation de la carte."
                refreshUI()
            }
        }
    }
    val routingPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    copyFileToInternalStorage(context, it, File(mapManager.routingDir, "custom.osm.pbf"), "pbf")
                }
                importStatus = if (ok) "PBF importé. Vous pouvez générer le graphe." else "Échec de l'importation du PBF."
                refreshUI()
            }
        }
    }

    // Suit les téléchargements DownloadManager et actualise l'état seulement après leur fin.
    LaunchedEffect(pendingDownloads.keys.toList()) {
        while (pendingDownloads.isNotEmpty()) {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val completed = mutableListOf<Long>()
            pendingDownloads.keys.toList().forEach { id ->
                val query = DownloadManager.Query().setFilterById(id)
                dm.query(query)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val statusColumn = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        if (statusColumn >= 0) {
                            val status = cursor.getInt(statusColumn)
                            if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                                val reasonColumn = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                                val reason = if (reasonColumn >= 0) cursor.getInt(reasonColumn) else 0
                                val pending = pendingDownloads[id]
                                if (pending != null) {
                                    val success = status == DownloadManager.STATUS_SUCCESSFUL &&
                                        mapManager.finalizeDownload(pending.regionId, pending.isMap, true)
                                    if (!success) mapManager.finalizeDownload(pending.regionId, pending.isMap, false)
                                    importStatus = if (success) {
                                        "${pending.label} téléchargé et enregistré."
                                    } else {
                                        "Échec du téléchargement ${pending.label} (code $reason)."
                                    }
                                }
                                completed.add(id)
                            }
                        }
                    } else {
                        pendingDownloads[id]?.let { mapManager.finalizeDownload(it.regionId, it.isMap, false) }
                        completed.add(id)
                    }
                }
            }
            completed.forEach { pendingDownloads.remove(it) }
            if (completed.isNotEmpty()) refreshUI()
            delay(1200)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Préparation du voyage", style = MaterialTheme.typography.headlineMedium)
        Text("Téléchargez les cartes Mapsforge et les données routières avant le départ.", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(12.dp))

        key(refreshTrigger) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(mapManager.availableRegions, key = { it.id }) { region ->
                    val mapReady = mapManager.isMapDownloaded(region.id)
                    val routingReady = mapManager.isRoutingDownloaded(region.id)
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (mapReady && routingReady) Icons.Default.CheckCircle else Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = if (mapReady && routingReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(region.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Carte : ${if (mapReady) "prête" else "absente"} · Routage : ${if (routingReady) "téléchargé" else "absent"}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                IconButton(onClick = { mapManager.deleteRegionFiles(region.id); refreshUI() }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Supprimer les fichiers de cette région")
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    enabled = !mapReady,
                                    onClick = {
                                        val id = mapManager.downloadMap(region)
                                        pendingDownloads[id] = PendingDownload("Carte ${region.name}", region.id, true)
                                    }
                                ) { Text(if (mapReady) "Carte prête" else "Télécharger carte") }
                                OutlinedButton(
                                    enabled = !routingReady,
                                    onClick = {
                                        val id = mapManager.downloadRouting(region)
                                        pendingDownloads[id] = PendingDownload("Routage ${region.name}", region.id, false)
                                    }
                                ) { Text(if (routingReady) "PBF prêt" else "Télécharger PBF") }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("Importer des données", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { mapPicker.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.UploadFile, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Importer .map")
            }
            OutlinedButton(onClick = { routingPicker.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.UploadFile, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Importer PBF")
            }
        }
        if (importStatus.isNotBlank()) {
            Text(importStatus, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Préparer le moteur de navigation", style = MaterialTheme.typography.titleMedium)
                Text(
                    "GraphHopper importe un seul extrait PBF à la fois. Pour couvrir plusieurs régions dans un seul graphe, fournissez un PBF fusionné couvrant toute la zone Belfort–Bâle.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        isCompilingGraph = true
                        graphStatus = "Génération du graphe en cours…"
                        coroutineScope.launch {
                            val files = mapManager.getRoutingFiles()
                            // Priorité au PBF importé (fusionné) ; sinon un PBF régional seulement
                            // s'il est le seul disponible (aucune ambiguïté).
                            val selectedPbf = files.firstOrNull { it.name == "custom.osm.pbf" }
                                ?: files.singleOrNull()
                            graphStatus = when {
                                files.isEmpty() -> "Aucun PBF disponible."
                                selectedPbf == null -> "Plusieurs PBF régionaux sont présents : GraphHopper n'en fusionne pas. Importez un PBF unique couvrant toute la zone Belfort–Bâle, ou supprimez les régions inutiles."
                                else -> routingManager.initOfflineRouting(selectedPbf)
                            }
                            isCompilingGraph = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isCompilingGraph
                ) {
                    Text(if (isCompilingGraph) "Génération…" else "Générer le graphe routier")
                }
                Text(graphStatus, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

/** Copie atomique : un import interrompu ne détruit jamais le fichier déjà utilisable. */
fun copyFileToInternalStorage(context: Context, uri: Uri, destFile: File, expectedExtension: String): Boolean {
    val displayName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
    val actualExtension = displayName?.substringAfterLast('.', "")?.lowercase()
    if (actualExtension != expectedExtension.lowercase()) return false

    val tempFile = File(destFile.parentFile, destFile.name + ".importing")
    val backupFile = File(destFile.parentFile, destFile.name + ".backup")
    return try {
        destFile.parentFile?.mkdirs()
        tempFile.delete()
        val input = context.contentResolver.openInputStream(uri) ?: return false
        input.use { source ->
            FileOutputStream(tempFile).use { output ->
                source.copyTo(output, bufferSize = 128 * 1024)
                output.fd.sync()
            }
        }
        if (!isPlausibleOfflineFile(tempFile, expectedExtension)) {
            tempFile.delete()
            return false
        }

        backupFile.delete()
        val hadOriginal = destFile.exists()
        if (hadOriginal && !destFile.renameTo(backupFile)) {
            tempFile.delete()
            return false
        }
        if (!tempFile.renameTo(destFile)) {
            if (hadOriginal) backupFile.renameTo(destFile)
            tempFile.delete()
            return false
        }
        backupFile.delete()
        true
    } catch (_: Exception) {
        tempFile.delete()
        if (!destFile.exists() && backupFile.exists()) backupFile.renameTo(destFile)
        false
    }
}

private fun isPlausibleOfflineFile(file: File, extension: String): Boolean {
    if (!file.isFile || file.length() < 1024L) return false
    return try {
        file.inputStream().buffered().use { input ->
            if (extension.equals("map", ignoreCase = true)) {
                val expected = "mapsforge binary OSM".toByteArray(Charsets.US_ASCII)
                val actual = ByteArray(expected.size)
                input.read(actual) == actual.size && actual.contentEquals(expected)
            } else {
                // Un fichier OSM-PBF commence par la longueur protobuf du BlobHeader (4 octets BE).
                val headerLength = DataInputStream(input).readInt()
                headerLength in 1..65536 && file.length() >= 4L + headerLength
            }
        }
    } catch (_: Exception) {
        false
    }
}
