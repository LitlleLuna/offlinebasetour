package com.maxence.offlinebaseltour.managers

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import java.io.File
import java.io.DataInputStream

data class MapRegion(
    val id: String,
    val name: String,
    val mapUrl: String,
    val pbfUrl: String,
    val estimatedSizeMb: Int
)

class MapManager(private val context: Context) {
    val mapsDir = File(context.getExternalFilesDir(null), "maps")
    val routingDir = File(context.getExternalFilesDir(null), "routing")

    init {
        mapsDir.mkdirs()
        routingDir.mkdirs()
    }

    // Ces deux régions fournissent les cartes Mapsforge et les extraits PBF Geofabrik.
    // Attention : GraphHopper ne fusionne pas automatiquement plusieurs PBF.
    val availableRegions = listOf(
        MapRegion(
            "franche-comte",
            "Franche-Comté (Belfort)",
            "https://download.mapsforge.org/maps/v5/europe/france/franche-comte.map",
            "https://download.geofabrik.de/europe/france/franche-comte-latest.osm.pbf",
            90
        ),
        MapRegion(
            "alsace",
            "Alsace (France)",
            "https://download.mapsforge.org/maps/v5/europe/france/alsace.map",
            "https://download.geofabrik.de/europe/france/alsace-latest.osm.pbf",
            50
        ),
        MapRegion(
            "switzerland",
            "Suisse",
            "https://download.mapsforge.org/maps/v5/europe/switzerland.map",
            "https://download.geofabrik.de/europe/switzerland-latest.osm.pbf",
            520
        )
    )

    fun isMapDownloaded(regionId: String): Boolean {
        val file = File(mapsDir, "$regionId.map")
        return isPlausibleMap(file)
    }

    fun isRoutingDownloaded(regionId: String): Boolean {
        val file = File(routingDir, "$regionId.osm.pbf")
        return isPlausiblePbf(file)
    }

    fun downloadMap(region: MapRegion): Long =
        enqueueDownload(region.mapUrl, mapsDir, "${region.id}.map", "Carte ${region.name}")

    fun downloadRouting(region: MapRegion): Long =
        enqueueDownload(region.pbfUrl, routingDir, "${region.id}.osm.pbf", "Routage ${region.name}")

    /** Publie le fichier seulement après confirmation de fin par DownloadManager. */
    fun finalizeDownload(regionId: String, isMap: Boolean, successful: Boolean): Boolean {
        val directory = if (isMap) mapsDir else routingDir
        val finalName = if (isMap) "$regionId.map" else "$regionId.osm.pbf"
        val temporary = File(directory, "$finalName.download")
        val destination = File(directory, finalName)
        val valid = if (isMap) isPlausibleMap(temporary) else isPlausiblePbf(temporary)
        if (!successful || !valid) {
            temporary.delete()
            return false
        }
        val backup = File(directory, "$finalName.backup")
        backup.delete()
        val hadDestination = destination.exists()
        if (hadDestination && !destination.renameTo(backup)) return false
        if (!temporary.renameTo(destination)) {
            if (hadDestination) backup.renameTo(destination)
            return false
        }
        backup.delete()
        return true
    }

    private fun enqueueDownload(url: String, destinationDir: File, fileName: String, title: String): Long {
        destinationDir.mkdirs()
        val temporaryName = "$fileName.download"
        File(destinationDir, temporaryName).delete()
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(title)
            .setDescription("Téléchargement pour utilisation hors ligne")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(false)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(context, null, "${destinationDir.name}/$temporaryName")
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return dm.enqueue(request)
    }

    fun getAllDownloadedMapFiles(): List<File> =
        mapsDir.listFiles { file -> file.extension.equals("map", ignoreCase = true) && isPlausibleMap(file) }
            ?.sortedBy { it.name }
            ?: emptyList()

    fun getRoutingFiles(): List<File> =
        routingDir.listFiles { file -> file.extension.equals("pbf", ignoreCase = true) && isPlausiblePbf(file) }
            ?.sortedBy { it.name }
            ?: emptyList()

    private fun isPlausibleMap(file: File): Boolean {
        if (!file.isFile || file.length() <= 1024L) return false
        return try {
            DataInputStream(file.inputStream().buffered()).use { input ->
                val expected = "mapsforge binary OSM".toByteArray(Charsets.US_ASCII)
                val actual = ByteArray(expected.size)
                input.readFully(actual)
                actual.contentEquals(expected)
            }
        } catch (_: Exception) { false }
    }

    private fun isPlausiblePbf(file: File): Boolean {
        if (!file.isFile || file.length() <= 1024L) return false
        return try {
            DataInputStream(file.inputStream().buffered()).use { input ->
                val size = input.readInt()
                size in 1..65536 && file.length() >= 4L + size
            }
        } catch (_: Exception) { false }
    }

    fun deleteRegionFiles(regionId: String) {
        File(mapsDir, "$regionId.map").delete()
        File(mapsDir, "$regionId.map.download").delete()
        File(mapsDir, "$regionId.map.backup").delete()
        File(routingDir, "$regionId.osm.pbf").delete()
        File(routingDir, "$regionId.osm.pbf.download").delete()
        File(routingDir, "$regionId.osm.pbf.backup").delete()
    }

    fun deleteCustomRoutingFile() {
        File(routingDir, "custom.osm.pbf").delete()
        File(routingDir, "custom.osm.pbf.importing").delete()
        File(routingDir, "custom.osm.pbf.backup").delete()
    }
}
