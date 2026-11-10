package com.example.easymap.ui.history

import android.graphics.Color
import android.location.Location
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.easymap.data.OccurrenceRepository
import com.example.easymap.data.TripRepository
import com.example.easymap.databinding.ActivityTripDetailBinding
import com.example.easymap.model.Occurrence
import com.example.easymap.model.Trip
import com.example.easymap.util.Formatters
import com.example.easymap.util.MapTiles
import com.example.easymap.util.MarkerIcons
import com.example.easymap.util.applySystemBarsPadding
import kotlinx.coroutines.launch
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/** Detalhe de uma viagem: percurso no mapa, estatísticas e ocorrências encontradas perto do trajeto. */
class TripDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTripDetailBinding
    private var tripPoints: List<GeoPoint> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityTripDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        binding.btnBack.setOnClickListener { finish() }
        binding.mapView.setTileSource(MapTiles.source)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.setBuiltInZoomControls(false)

        val tripId = intent.getLongExtra(EXTRA_TRIP_ID, -1L)
        lifecycleScope.launch {
            val trip = TripRepository(this@TripDetailActivity).get(tripId)
            if (trip == null) {
                finish()
                return@launch
            }
            val occurrences = OccurrenceRepository(this@TripDetailActivity).getAll()
            show(trip, occurrences)
        }
    }

    private fun show(trip: Trip, occurrences: List<Occurrence>) {
        binding.tvRoute.text = "${trip.startName} → ${trip.endName}"
        binding.tvDate.text = "${Formatters.dateTime(trip.startTime)} · ${Formatters.time(trip.endTime)}"
        binding.tvDistance.text = Formatters.distance(trip.distanceMeters)
        binding.tvDuration.text = Formatters.durationShort(trip.durationSeconds)
        binding.tvAvgSpeed.text = Formatters.speed(trip.avgSpeedKmh)

        val nearby = occurrences.filter { occurrence -> isNearRoute(trip, occurrence) }
        binding.tvNearby.text = if (nearby.isEmpty()) {
            "Sem ocorrências perto do percurso."
        } else {
            val summary = nearby.groupingBy { it.category.label }.eachCount()
                .entries.joinToString(", ") { "${it.value} ${it.key.lowercase()}" }
            "${nearby.size} ocorrência(s): $summary"
        }

        tripPoints = trip.route.map { GeoPoint(it.latitude, it.longitude) }
        if (tripPoints.isEmpty()) return

        val line = Polyline(binding.mapView).apply {
            outlinePaint.color = Color.parseColor("#2563EB")
            outlinePaint.strokeWidth = 12f
            setPoints(tripPoints)
        }
        binding.mapView.overlays.add(line)

        for (occurrence in nearby) {
            binding.mapView.overlays.add(Marker(binding.mapView).apply {
                position = GeoPoint(occurrence.latitude, occurrence.longitude)
                icon = MarkerIcons.create(this@TripDetailActivity, occurrence.category)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            })
        }

        binding.btnMapRecenter.setOnClickListener { recenterMap() }
        recenterMap()
    }

    private fun recenterMap() {
        if (tripPoints.isEmpty()) return
        binding.mapView.post {
            if (tripPoints.size > 1) {
                try {
                    binding.mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(tripPoints), true, 120)
                } catch (e: Exception) {
                    binding.mapView.controller.setZoom(16.0)
                    binding.mapView.controller.setCenter(tripPoints.first())
                }
            } else {
                binding.mapView.controller.setZoom(16.0)
                binding.mapView.controller.setCenter(tripPoints.first())
            }
        }
    }

    /** Uma ocorrência está "perto" se estiver a menos de 300 m de algum ponto do percurso. */
    private fun isNearRoute(trip: Trip, occurrence: Occurrence): Boolean {
        val result = FloatArray(1)
        return trip.route.any { point ->
            Location.distanceBetween(point.latitude, point.longitude, occurrence.latitude, occurrence.longitude, result)
            result[0] < 300f
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        binding.mapView.onPause()
        super.onPause()
    }

    companion object {
        const val EXTRA_TRIP_ID = "trip_id"
    }
}
