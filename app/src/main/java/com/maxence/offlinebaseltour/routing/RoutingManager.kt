package com.maxence.offlinebaseltour.routing

import android.content.Context
import com.graphhopper.GraphHopper
import com.graphhopper.config.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.mapsforge.core.model.LatLong
import java.io.File
import java.util.Locale

class RoutingManager(context: Context) {
    val routingDir = File(context.getExternalFilesDir(null), "routing")
    private val graphCacheDir = File(routingDir, "graph-cache")
    private val graphMetadataFile = File(graphCacheDir, "basel-graph-source.txt")

    @Volatile private var hopper: GraphHopper? = null
    @Volatile var isReady = false
        private set
    private val graphMutex = Mutex()

    init {
        routingDir.mkdirs()
        // Ne pas charger le graphe sur le thread principal : il peut être volumineux.
    }

    private fun loadHopper(pbfFile: File?) {
        graphCacheDir.parentFile?.mkdirs()
        val instance = GraphHopper().apply {
            if (pbfFile != null) osmFile = pbfFile.absolutePath
            graphHopperLocation = graphCacheDir.absolutePath
            profiles = listOf(
                Profile("car").setVehicle("car").setWeighting("fastest"),
                Profile("foot").setVehicle("foot").setWeighting("shortest")
            )
//            chEnabled = false
//            lmEnabled = false
        }
        instance.importOrLoad()
        if (pbfFile != null) {
            graphMetadataFile.writeText(
                "source=${pbfFile.name}\nbytes=${pbfFile.length()}\ncreated=${System.currentTimeMillis()}\n"
            )
        }
        hopper = instance
        isReady = true
    }

    suspend fun loadExistingGraph(): Boolean = withContext(Dispatchers.IO) {
        graphMutex.withLock {
            if (isReady && hopper != null) return@withLock true
            if (!graphCacheDir.exists() || graphCacheDir.list()?.isNotEmpty() != true) return@withLock false
            // Invalide les anciens graphes sans preuve de leur source/couverture.
            if (!graphMetadataFile.isFile || graphMetadataFile.length() == 0L) {
                graphCacheDir.deleteRecursively()
                return@withLock false
            }
            return@withLock try {
                loadHopper(null)
                true
            } catch (e: Exception) {
                hopper = null
                isReady = false
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun initOfflineRouting(pbfFile: File): String = withContext(Dispatchers.IO) {
        graphMutex.withLock {
            isReady = false
            try { hopper?.close() } catch (_: Exception) {}
            hopper = null
            if (!pbfFile.exists() || pbfFile.length() <= 1024L) {
                return@withLock "Erreur : fichier PBF absent ou trop petit."
            }
            try {
                if (graphCacheDir.exists() && !graphCacheDir.deleteRecursively()) {
                    return@withLock "Impossible de supprimer l'ancien graphe."
                }
                loadHopper(pbfFile)
                "Graphe routier généré avec succès."
            } catch (e: OutOfMemoryError) {
                hopper = null
                isReady = false
                graphCacheDir.deleteRecursively()
                "Erreur mémoire : PBF trop volumineux pour ce téléphone."
            } catch (e: Exception) {
                hopper = null
                isReady = false
                graphCacheDir.deleteRecursively()
                e.printStackTrace()
                "Erreur technique : ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    suspend fun calculateRoute(
        start: LatLong,
        end: LatLong,
        mode: String = "car"
    ): RouteResult? {
        if (mode != "car" && mode != "foot") return null
        if (!loadExistingGraph()) return null

        return withContext(Dispatchers.Default) {
            try {
                val engine = hopper ?: return@withContext null
                val request = com.graphhopper.GHRequest(
                    start.latitude, start.longitude, end.latitude, end.longitude
                ).setProfile(mode).setLocale(Locale.FRENCH)

                val response = engine.route(request)
                if (response.hasErrors()) return@withContext null

                val path = response.best
                val routePoints = path.points.map { point -> LatLong(point.lat, point.lon) }
                val instructionList = path.instructions
                val instructions = instructionList.mapIndexed { index, instruction ->
                    val street = instruction.name?.takeIf { it.isNotBlank() }
                    val instructionPoints = instruction.points.map { point -> LatLong(point.lat, point.lon) }
                    // GraphHopper ne duplique pas le dernier point d'une instruction :
                    // il appartient au début de l'instruction suivante.
                    val nextStart = instructionList.getOrNull(index + 1)?.points?.let { points ->
                        if (points.size() > 0) LatLong(points.getLat(0), points.getLon(0)) else null
                    }
                    val completePoints = if (nextStart != null && instructionPoints.lastOrNull() != nextStart) {
                        instructionPoints + nextStart
                    } else instructionPoints
                    NavigationInstruction(
                        text = instructionText(instruction.sign, street),
                        distanceMetres = instruction.distance,
                        timeMillis = instruction.time,
                        path = completePoints
                    )
                }
                RouteResult(routePoints, path.distance, path.time, instructions)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}

data class NavigationInstruction(
    val text: String,
    val distanceMetres: Double,
    val timeMillis: Long,
    val path: List<LatLong>
)

private fun instructionText(sign: Int, street: String?): String {
    val road = street?.let { " sur $it" }.orEmpty()
    return when (sign) {
        -3 -> "Tournez fortement à gauche$road"
        -2 -> "Tournez à gauche$road"
        -1 -> "Prenez légèrement à gauche$road"
        0 -> if (street.isNullOrBlank()) "Continuez tout droit" else "Continuez sur $street"
        1 -> "Prenez légèrement à droite$road"
        2 -> "Tournez à droite$road"
        3 -> "Tournez fortement à droite$road"
        4 -> "Vous êtes arrivé à destination"
        5 -> "Passez le point de passage"
        6 -> "Prenez le rond-point$road"
        -6 -> "Sortez du rond-point$road"
        -7 -> "Restez à gauche$road"
        7 -> "Restez à droite$road"
        -8 -> "Faites demi-tour à gauche$road"
        8 -> "Faites demi-tour à droite$road"
        -98 -> "Faites demi-tour"
        -99 -> street?.let { "Continuez sur $it" } ?: "Continuez"
        else -> street?.let { "Continuez sur $it" } ?: "Continuez"
    }
}

data class RouteResult(
    val path: List<LatLong>,
    val distanceMetres: Double,
    val timeMillis: Long,
    val instructions: List<NavigationInstruction>
)
