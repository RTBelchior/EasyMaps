package com.example.easymap.ui.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.easymap.data.OccurrenceRepository
import com.example.easymap.data.WeatherApi
import com.example.easymap.databinding.FragmentMapBinding
import com.example.easymap.model.Category
import com.example.easymap.model.LatLon
import com.example.easymap.model.Occurrence
import com.example.easymap.sensors.AmbientLightMonitor
import com.example.easymap.sensors.HardBrakeDetector
import com.example.easymap.service.TripService
import com.example.easymap.service.TripTracker
import com.example.easymap.ui.report.ReportActivity
import com.example.easymap.util.Formatters
import com.example.easymap.util.ImageUtils
import com.example.easymap.util.MarkerIcons
import com.example.easymap.util.VibrationHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import org.osmdroid.events.MapEventsReceiver
import com.example.easymap.util.MapTiles
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import kotlin.math.roundToInt

/**
 * Ecrã principal: mapa OpenStreetMap com a posição atual, ocorrências e controlo da viagem.
 * Ecrã tátil: zoom com dois dedos, arrastar e TOQUE LONGO no mapa para reportar nesse ponto.
 */
class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private var locationOverlay: MyLocationNewOverlay? = null
    private var routeLine: Polyline? = null
    private val occurrenceMarkers = mutableListOf<Marker>()
    private var hardBrakeDetector: HardBrakeDetector? = null
    private var lightMonitor: AmbientLightMonitor? = null
    private var pendingAction: (() -> Unit)? = null
    private var previousState = TripTracker.State.IDLE
    private var isDarkMap = false
    private var weatherRequestedForFix = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasLocationPermission()) {
                enableLocation()
                pendingAction?.invoke()
            } else {
                showPermissionDenied()
            }
            pendingAction = null
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMap()
        setupButtons()
        setupSensors()
        observeTrip()
        loadWeather(DEFAULT_LATITUDE, DEFAULT_LONGITUDE)
        if (hasLocationPermission()) enableLocation()
    }

    // ---------- Mapa ----------

    private fun setupMap() {
        binding.mapView.apply {
            setTileSource(MapTiles.source)
            setMultiTouchControls(true) // zoom com dois dedos
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(DEFAULT_LATITUDE, DEFAULT_LONGITUDE))
        }

        val overlay = MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), binding.mapView)
        locationOverlay = overlay
        binding.mapView.overlays.add(overlay)

        val longPress = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
            override fun longPressHelper(p: GeoPoint?): Boolean {
                if (p != null) openReport(p.latitude, p.longitude, null)
                return true
            }
        })
        binding.mapView.overlays.add(0, longPress)
    }

    private fun enableLocation() {
        val overlay = locationOverlay ?: return
        overlay.enableMyLocation()
        overlay.enableFollowLocation()
        if (!weatherRequestedForFix) {
            weatherRequestedForFix = true
            binding.mapView.controller.setZoom(17.0)
            overlay.runOnFirstFix {
                activity?.runOnUiThread {
                    val position = locationOverlay?.myLocation
                    if (position != null && _binding != null) loadWeather(position.latitude, position.longitude)
                }
            }
        }
    }

    private fun currentPoint(): LatLon {
        val position = locationOverlay?.myLocation
        if (position != null) return LatLon(position.latitude, position.longitude)
        val center = binding.mapView.mapCenter
        return LatLon(center.latitude, center.longitude)
    }

    private fun loadWeather(latitude: Double, longitude: Double) {
        viewLifecycleOwner.lifecycleScope.launch {
            val weather = WeatherApi.fetch(latitude, longitude)
            if (_binding == null) return@launch
            binding.tvWeather.text = if (weather == null) "Meteorologia indisponível"
            else "${weather.temperatureC.roundToInt()} °C · ${weather.description}"
        }
    }

    // ---------- Ocorrências ----------

    private fun refreshOccurrences() {
        viewLifecycleOwner.lifecycleScope.launch {
            val repository = OccurrenceRepository(requireContext())
            try {
                repository.downloadAll() // sem Internet falha em silêncio e usamos a cache local
            } catch (e: Exception) {
                // modo offline
            }
            val occurrences = repository.getAll()
            if (_binding != null) showOccurrences(occurrences)
        }
    }

    private fun showOccurrences(occurrences: List<Occurrence>) {
        val map = binding.mapView
        map.overlays.removeAll(occurrenceMarkers)
        occurrenceMarkers.clear()
        for (occurrence in occurrences) {
            val marker = Marker(map).apply {
                position = GeoPoint(occurrence.latitude, occurrence.longitude)
                icon = MarkerIcons.create(requireContext(), occurrence.category)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ ->
                    showOccurrenceDialog(occurrence)
                    true
                }
            }
            occurrenceMarkers.add(marker)
            map.overlays.add(marker)
        }
        map.invalidate()
    }

    private fun showOccurrenceDialog(occurrence: Occurrence) {
        val context = requireContext()
        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(occurrence.category.label)
            .setMessage("${Formatters.dateTime(occurrence.timestamp)}\n${occurrence.description}")
            .setPositiveButton("Fechar", null)
        occurrence.photoPath?.let { path ->
            ImageUtils.decodeSampled(path, 700)?.let { bitmap ->
                builder.setView(ImageView(context).apply {
                    setImageBitmap(bitmap)
                    adjustViewBounds = true
                    setPadding(32, 16, 32, 0)
                })
            }
        }
        builder.show()
    }

    private fun openReport(latitude: Double, longitude: Double, category: Category?) {
        val intent = Intent(requireContext(), ReportActivity::class.java)
            .putExtra(ReportActivity.EXTRA_LATITUDE, latitude)
            .putExtra(ReportActivity.EXTRA_LONGITUDE, longitude)
            .putExtra(ReportActivity.EXTRA_CATEGORY, category?.name)
        startActivity(intent)
    }

    // ---------- Viagem ----------

    private fun setupButtons() {
        binding.btnStart.setOnClickListener { withLocationPermission { startTrip() } }
        binding.btnPause.setOnClickListener {
            val paused = TripTracker.snapshot.value.state == TripTracker.State.PAUSED
            sendServiceAction(if (paused) TripService.ACTION_RESUME else TripService.ACTION_PAUSE)
        }
        binding.btnStop.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Terminar viagem?")
                .setMessage("A viagem será guardada no histórico.")
                .setPositiveButton("Terminar") { _, _ -> sendServiceAction(TripService.ACTION_STOP) }
                .setNegativeButton("Cancelar", null)
                .show()
        }
        binding.fabReport.setOnClickListener {
            val point = currentPoint()
            openReport(point.latitude, point.longitude, null)
        }
        binding.fabLocate.setOnClickListener {
            withLocationPermission {
                enableLocation()
                locationOverlay?.enableFollowLocation()
            }
        }
    }

    private fun startTrip() {
        val context = requireContext()
        val locationManager = context.getSystemService(LocationManager::class.java)
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Snackbar.make(binding.root, "Ativa o GPS para iniciar a viagem.", Snackbar.LENGTH_LONG)
                .setAction("Definições") { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                .setAnchorView(binding.bottomCard)
                .show()
            return
        }
        val intent = Intent(context, TripService::class.java).setAction(TripService.ACTION_START)
        ContextCompat.startForegroundService(context, intent)
    }

    private fun sendServiceAction(action: String) {
        val context = requireContext()
        context.startService(Intent(context, TripService::class.java).setAction(action))
    }

    private fun observeTrip() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                TripTracker.snapshot.collect { render(it) }
            }
        }
    }

    private fun render(snapshot: TripTracker.Snapshot) {
        val active = snapshot.state != TripTracker.State.IDLE
        binding.btnStart.isVisible = !active
        binding.statsRow.isVisible = active
        binding.controlsRow.isVisible = active
        binding.tvStatus.text = when (snapshot.state) {
            TripTracker.State.IDLE -> "Pronto para partir"
            TripTracker.State.RUNNING -> "Viagem em curso"
            TripTracker.State.PAUSED -> "Viagem em pausa"
        }
        binding.btnPause.text = if (snapshot.state == TripTracker.State.PAUSED) "Retomar" else "Pausar"
        binding.tvSpeed.text = Formatters.speed(snapshot.speedKmh)
        binding.tvDistance.text = Formatters.distance(snapshot.distanceMeters)
        binding.tvDuration.text = Formatters.duration(snapshot.elapsedSeconds)
        updateRoute(snapshot.route)

        // O acelerómetro só trabalha durante a viagem (poupança de bateria)
        if (snapshot.state == TripTracker.State.RUNNING) hardBrakeDetector?.start() else hardBrakeDetector?.stop()

        if (previousState != TripTracker.State.IDLE && snapshot.state == TripTracker.State.IDLE) {
            Toast.makeText(requireContext(), "Viagem guardada no histórico.", Toast.LENGTH_SHORT).show()
        }
        previousState = snapshot.state
    }

    private fun updateRoute(route: List<LatLon>) {
        val map = binding.mapView
        if (route.isEmpty()) {
            routeLine?.let { map.overlays.remove(it) }
            routeLine = null
            map.invalidate()
            return
        }
        val line = routeLine ?: Polyline(map).also {
            it.outlinePaint.color = Color.parseColor("#1D4ED8")
            it.outlinePaint.strokeWidth = 12f
            map.overlays.add(it)
            routeLine = it
        }
        line.setPoints(route.map { GeoPoint(it.latitude, it.longitude) })
        map.invalidate()
    }

    // ---------- Sensores ----------

    private fun setupSensors() {
        hardBrakeDetector = HardBrakeDetector(requireContext()) { onHardBrake() }
        lightMonitor = AmbientLightMonitor(requireContext()) { lux -> onLightChanged(lux) }
    }

    private fun onHardBrake() {
        if (_binding == null) return
        VibrationHelper.vibrate(requireContext(), 300)
        val point = TripTracker.snapshot.value.lastLocation ?: currentPoint()
        Snackbar.make(binding.root, "Travagem brusca detetada.", Snackbar.LENGTH_LONG)
            .setAction("Reportar perigo") { openReport(point.latitude, point.longitude, Category.HAZARD) }
            .setAnchorView(binding.bottomCard)
            .show()
    }

    /** Sensor de luz: mapa escuro com pouca luz (histerese para não ficar a piscar). */
    private fun onLightChanged(lux: Float) {
        if (_binding == null) return
        val dark = if (isDarkMap) lux < 60f else lux < 15f
        if (dark == isDarkMap) return
        isDarkMap = dark
        binding.mapView.overlayManager.tilesOverlay.setColorFilter(if (dark) TilesOverlay.INVERT_COLORS else null)
        binding.mapView.invalidate()
    }

    // ---------- Permissões ----------

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun withLocationPermission(action: () -> Unit) {
        if (hasLocationPermission()) {
            action()
            return
        }
        pendingAction = action
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun showPermissionDenied() {
        if (_binding == null) return
        Snackbar.make(binding.root, "Sem permissão de localização não é possível usar o GPS.", Snackbar.LENGTH_LONG)
            .setAction("Definições") {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", requireContext().packageName, null))
                )
            }
            .setAnchorView(binding.bottomCard)
            .show()
    }

    // ---------- Ciclo de vida ----------

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        lightMonitor?.start()
        refreshOccurrences()
    }

    override fun onPause() {
        binding.mapView.onPause()
        lightMonitor?.stop()
        hardBrakeDetector?.stop()
        super.onPause()
    }

    override fun onDestroyView() {
        binding.mapView.onDetach()
        _binding = null
        locationOverlay = null
        routeLine = null
        occurrenceMarkers.clear()
        super.onDestroyView()
    }

    private companion object {
        const val DEFAULT_LATITUDE = 38.5244   // Setúbal
        const val DEFAULT_LONGITUDE = -8.8882
    }
}
