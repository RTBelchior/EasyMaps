package com.example.easymap.service

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.easymap.data.OccurrenceRepository
import com.example.easymap.data.SyncWorker
import com.example.easymap.data.TripRepository
import com.example.easymap.model.LatLon
import com.example.easymap.model.Trip
import com.example.easymap.util.GeocodingHelper
import com.example.easymap.util.NotificationHelper
import com.example.easymap.util.VibrationHelper
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Serviço em primeiro plano que acompanha a deslocação por GPS (continua com o ecrã desligado).
 * Tarefas concorrentes: callbacks do GPS, cronómetro (coroutine), verificação de ocorrências
 * próximas (coroutine em IO) e gravação final na base de dados.
 */
class TripService : Service(), LocationListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var locationManager: LocationManager
    private var timerJob: Job? = null
    private var lastLocation: Location? = null
    private var startTime = 0L
    private var lastAlertCheck = 0L
    private val alertedIds = mutableSetOf<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LocationManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTrip()
            ACTION_PAUSE -> pauseTrip()
            ACTION_RESUME -> resumeTrip()
            ACTION_STOP -> stopTrip()
        }
        return START_NOT_STICKY
    }

    private fun startTrip() {
        val notification = NotificationHelper.buildTripNotification(this, "A gravar a tua viagem")
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NotificationHelper.TRIP_NOTIFICATION_ID, notification, type)

        startTime = System.currentTimeMillis()
        lastLocation = null
        alertedIds.clear()
        TripTracker.reset()
        TripTracker.update { it.copy(state = TripTracker.State.RUNNING) }
        if (!requestLocationUpdates()) {
            stopSelfSafely()
            return
        }
        startTimer()
    }

    private fun pauseTrip() {
        TripTracker.update { it.copy(state = TripTracker.State.PAUSED, speedKmh = 0.0) }
        locationManager.removeUpdates(this) // poupa bateria enquanto está em pausa
        lastLocation = null
    }

    private fun resumeTrip() {
        TripTracker.update { it.copy(state = TripTracker.State.RUNNING) }
        requestLocationUpdates()
    }

    private fun stopTrip() {
        locationManager.removeUpdates(this)
        timerJob?.cancel()
        val snapshot = TripTracker.snapshot.value
        val endTime = System.currentTimeMillis()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "local"

        scope.launch(Dispatchers.IO) {
            if (snapshot.elapsedSeconds > 0 && snapshot.route.isNotEmpty()) {
                val trip = Trip(
                    userId = userId,
                    startTime = startTime,
                    endTime = endTime,
                    distanceMeters = snapshot.distanceMeters,
                    durationSeconds = snapshot.elapsedSeconds,
                    avgSpeedKmh = snapshot.averageSpeedKmh,
                    startName = GeocodingHelper.placeName(applicationContext, snapshot.route.firstOrNull()),
                    endName = GeocodingHelper.placeName(applicationContext, snapshot.route.lastOrNull()),
                    route = snapshot.route
                )
                TripRepository(applicationContext).save(trip)
                SyncWorker.enqueue(applicationContext)
            }
            TripTracker.reset()
            stopSelfSafely()
        }
    }

    private fun stopSelfSafely() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun requestLocationUpdates(): Boolean {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return false
        return try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, UPDATE_INTERVAL_MS, MIN_DISTANCE_M, this, Looper.getMainLooper()
            )
            true
        } catch (e: SecurityException) {
            false
        } catch (e: IllegalArgumentException) {
            false // fornecedor GPS inexistente
        }
    }

    /** Cronómetro: incrementa a duração a cada segundo enquanto a viagem estiver ativa. */
    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                delay(1000)
                TripTracker.update {
                    if (it.state == TripTracker.State.RUNNING) it.copy(elapsedSeconds = it.elapsedSeconds + 1) else it
                }
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        if (TripTracker.snapshot.value.state != TripTracker.State.RUNNING) return
        if (location.hasAccuracy() && location.accuracy > MAX_ACCURACY_M) return // ignora leituras imprecisas

        val previous = lastLocation
        val moved = if (previous != null) previous.distanceTo(location).toDouble() else 0.0
        val isNewPoint = previous == null || moved >= MIN_MOVE_M
        if (isNewPoint) lastLocation = location

        val point = LatLon(location.latitude, location.longitude)
        val speed = if (location.hasSpeed()) location.speed * 3.6 else 0.0
        TripTracker.update {
            it.copy(
                distanceMeters = it.distanceMeters + if (isNewPoint && previous != null) moved else 0.0,
                speedKmh = speed,
                route = if (isNewPoint) it.route + point else it.route,
                lastLocation = point
            )
        }
        checkNearbyOccurrences(location)
    }

    /** Alerta (notificação + vibração) quando há uma ocorrência a menos de 300 m. */
    private fun checkNearbyOccurrences(location: Location) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAlertCheck < ALERT_CHECK_INTERVAL_MS) return
        lastAlertCheck = now

        scope.launch(Dispatchers.IO) {
            val occurrences = OccurrenceRepository(applicationContext).getAll()
            for (occurrence in occurrences) {
                if (occurrence.id in alertedIds) continue
                val result = FloatArray(1)
                Location.distanceBetween(
                    location.latitude, location.longitude, occurrence.latitude, occurrence.longitude, result
                )
                if (result[0] < ALERT_RADIUS_M) {
                    alertedIds.add(occurrence.id)
                    NotificationHelper.notifyAlert(
                        applicationContext,
                        occurrence.id.hashCode(),
                        "${occurrence.category.label} à frente",
                        "A cerca de ${result[0].toInt()} m. Conduz com atenção."
                    )
                    VibrationHelper.vibrate(applicationContext, 500)
                }
            }
        }
    }

    // Estes métodos são abstratos em Android < 11, por isso têm de existir.
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onDestroy() {
        locationManager.removeUpdates(this)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.easymap.action.START"
        const val ACTION_PAUSE = "com.example.easymap.action.PAUSE"
        const val ACTION_RESUME = "com.example.easymap.action.RESUME"
        const val ACTION_STOP = "com.example.easymap.action.STOP"

        private const val UPDATE_INTERVAL_MS = 2000L
        private const val MIN_DISTANCE_M = 3f
        private const val MIN_MOVE_M = 3.0
        private const val MAX_ACCURACY_M = 40f
        private const val ALERT_RADIUS_M = 300f
        private const val ALERT_CHECK_INTERVAL_MS = 15_000L
    }
}
