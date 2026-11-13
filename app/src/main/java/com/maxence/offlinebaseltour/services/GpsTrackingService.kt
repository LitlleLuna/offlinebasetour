package com.maxence.offlinebaseltour.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.maxence.offlinebaseltour.BaselTourApp
import com.maxence.offlinebaseltour.R
import com.maxence.offlinebaseltour.database.Track
import com.maxence.offlinebaseltour.database.TrackPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Enregistre la position GNSS native. Aucune connexion de données n'est requise.
 * Room reçoit chaque point accepté afin de limiter les pertes en cas d'arrêt du processus.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GpsTrackingService : Service() {
    private lateinit var locationManager: LocationManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val channelId = "OfflineBaselTourTracking"

    @Volatile private var currentTrackId: Long = -1L
    @Volatile private var currentDistance = 0f
    @Volatile private var lastAcceptedLocation: Location? = null

    companion object {
        const val ACTION_START = "com.maxence.offlinebaseltour.action.START"
        const val ACTION_PAUSE = "com.maxence.offlinebaseltour.action.PAUSE"
        const val ACTION_RESUME = "com.maxence.offlinebaseltour.action.RESUME"
        const val ACTION_STOP = "com.maxence.offlinebaseltour.action.STOP"
        private const val NOTIFICATION_ID = 410
        private const val MAX_ACCURACY_METRES = 35f
        private const val MAX_SPEED_METRES_PER_SECOND = 70f
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (TrackingManager.trackingState.value != TrackingState.RECORDING) return
            if (!location.latitude.isFinite() || !location.longitude.isFinite()) return
            if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) return
            if (!location.hasAccuracy() || !location.accuracy.isFinite() ||
                location.accuracy <= 0f || location.accuracy > MAX_ACCURACY_METRES) return

            val previous = lastAcceptedLocation
            if (previous != null) {
                val deltaSeconds = (location.elapsedRealtimeNanos - previous.elapsedRealtimeNanos) / 1_000_000_000.0
                if (deltaSeconds <= 0.0) return
                val segment = previous.distanceTo(location)
                if (segment < 1.5f) return
                if (segment / deltaSeconds > MAX_SPEED_METRES_PER_SECOND) return
                currentDistance += segment
            }

            lastAcceptedLocation = Location(location)
            TrackingManager.updateDistance(currentDistance)
            updateNotification()

            val trackId = currentTrackId
            if (trackId > 0L) {
                val point = TrackPoint(
                    trackId = trackId,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    time = location.time,
                    accuracy = location.accuracy
                )
                val distanceSnapshot = currentDistance
                serviceScope.launch {
                    BaselTourApp.database.trackDao().savePointAndDistance(point, distanceSnapshot)
                }
            }
        }

        @Deprecated("Deprecated by Android; required for older API compatibility")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_PAUSE -> pauseTracking()
            ACTION_RESUME -> resumeTracking()
            ACTION_STOP -> stopTracking()
            null -> restoreInterruptedTrack()
        }
        return START_STICKY
    }

    private fun promoteToForeground() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startTracking() {
        // startForegroundService() doit être suivi rapidement de startForeground().
        promoteToForeground()
        TrackingManager.updateState(TrackingState.RECORDING)
        serviceScope.launch {
            try {
                val dao = BaselTourApp.database.trackDao()
                val active = dao.getActiveTrack()
                val track = active ?: Track(
                    name = "Trajet du " + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date()),
                    startTime = System.currentTimeMillis()
                ).let { dao.insertTrack(it); dao.getActiveTrack() }

                if (track == null) error("La base n'a pas retourné le trajet créé.")
                currentTrackId = track.id
                currentDistance = track.distanceMetres
                lastAcceptedLocation = null
                TrackingManager.updateDistance(currentDistance)

                when (TrackingManager.trackingState.value) {
                    TrackingState.RECORDING -> requestLocationUpdates()
                    TrackingState.PAUSED -> Unit
                    TrackingState.STOPPED -> {
                        track.endTime = System.currentTimeMillis()
                        dao.updateTrack(track)
                        currentTrackId = -1L
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        return@launch
                    }
                }
                updateNotification()
            } catch (e: Exception) {
                TrackingManager.updateState(TrackingState.STOPPED)
                updateNotification("Impossible de démarrer le suivi : ${e.message ?: "erreur de stockage"}")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun restoreInterruptedTrack() {
        // START_STICKY peut redémarrer le service avec un Intent nul : promotion immédiate.
        promoteToForeground()
        serviceScope.launch {
            val active = BaselTourApp.database.trackDao().getActiveTrack()
            if (active == null) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }
            currentTrackId = active.id
            currentDistance = active.distanceMetres
            lastAcceptedLocation = null
            TrackingManager.updateDistance(currentDistance)
            TrackingManager.updateState(TrackingState.RECORDING)
            requestLocationUpdates()
        }
    }

    private fun pauseTracking() {
        if (TrackingManager.trackingState.value != TrackingState.RECORDING) return
        removeLocationUpdates()
        lastAcceptedLocation = null
        TrackingManager.updateState(TrackingState.PAUSED)
        updateNotification()
    }

    private fun resumeTracking() {
        if (TrackingManager.trackingState.value != TrackingState.PAUSED) return
        lastAcceptedLocation = null
        TrackingManager.updateState(TrackingState.RECORDING)
        requestLocationUpdates()
        updateNotification()
    }

    private fun stopTracking() {
        removeLocationUpdates()
        TrackingManager.updateState(TrackingState.STOPPED)
        val id = currentTrackId
        val distance = currentDistance
        serviceScope.launch {
            if (id > 0L) {
                val dao = BaselTourApp.database.trackDao()
                dao.getTrackById(id)?.let { track ->
                    track.endTime = System.currentTimeMillis()
                    track.distanceMetres = distance
                    dao.updateTrack(track)
                }
            }
            currentTrackId = -1L
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun requestLocationUpdates() {
        try {
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                TrackingManager.updateState(TrackingState.PAUSED)
                updateNotification("GPS désactivé — activez la localisation puis reprenez")
                return
            }
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                3000L,
                2f,
                locationListener,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            updateNotification("Autorisation GPS manquante")
            TrackingManager.updateState(TrackingState.PAUSED)
        } catch (e: IllegalArgumentException) {
            updateNotification("Récepteur GPS indisponible")
            TrackingManager.updateState(TrackingState.PAUSED)
        }
    }

    private fun removeLocationUpdates() {
        try {
            locationManager.removeUpdates(locationListener)
        } catch (_: SecurityException) {
        }
    }

    private fun createNotification(message: String? = null): Notification {
        val distanceKm = String.format(Locale.getDefault(), "%.2f", currentDistance / 1000f)
        val stateText = when (TrackingManager.trackingState.value) {
            TrackingState.PAUSED -> "En pause"
            TrackingState.RECORDING -> "Enregistrement"
            TrackingState.STOPPED -> "Suivi GPS"
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Basel Tour Offline — $stateText")
            .setContentText(message ?: "Distance parcourue : $distanceKm km")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(TrackingManager.trackingState.value != TrackingState.STOPPED)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(message: String? = null) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification(message))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Suivi GPS hors ligne", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        removeLocationUpdates()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
