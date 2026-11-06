package com.example.easymap.ui.report

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.lifecycleScope
import com.example.easymap.R
import com.example.easymap.data.OccurrenceRepository
import com.example.easymap.data.SyncWorker
import com.example.easymap.databinding.ActivityReportBinding
import com.example.easymap.databinding.ItemCategoryBinding
import com.example.easymap.model.Category
import com.example.easymap.model.Occurrence
import com.example.easymap.service.TripTracker
import com.example.easymap.util.Formatters
import com.example.easymap.util.ImageUtils
import com.example.easymap.util.MapTiles
import com.example.easymap.util.applySystemBarsPadding
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * Registo de uma ocorrência. Coordenadas, data, hora e velocidade são obtidas automaticamente
 * (GPS / toque longo no mapa / relógio). A fotografia usa a câmara do smartphone.
 */
class ReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportBinding
    private var latitude = 0.0
    private var longitude = 0.0
    private val timestamp = System.currentTimeMillis()
    private var photoFile: File? = null
    private var selectedCategory = Category.HAZARD
    private val tiles = mutableMapOf<Category, MaterialCardView>()

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = photoFile
        if (saved && file != null) {
            binding.ivPhoto.setPadding(0, 0, 0, 0)
            binding.ivPhoto.imageTintList = null
            binding.ivPhoto.setImageBitmap(ImageUtils.decodeSampled(file.absolutePath, 400))
            binding.btnPhoto.text = "Tirar outra foto"
        } else {
            file?.delete()
            photoFile = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        latitude = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
        longitude = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)
        selectedCategory = Category.fromName(intent.getStringExtra(EXTRA_CATEGORY))

        binding.tvCoordinates.text = String.format(Locale.US, "%.4f, %.4f", latitude, longitude)
        binding.tvDatetime.text = Formatters.dateTime(timestamp)
        binding.tvSpeed.text = Formatters.speed(TripTracker.snapshot.value.speedKmh)

        binding.btnBack.setOnClickListener { finish() }
        setupMiniMap()
        setupCategories()
        binding.btnPhoto.setOnClickListener { launchCamera() }
        binding.btnSubmit.setOnClickListener { submit() }
    }

    /** Mini-mapa só de leitura, com um ponto azul na posição da ocorrência. */
    private fun setupMiniMap() {
        val point = GeoPoint(latitude, longitude)
        binding.mapView.apply {
            setTileSource(MapTiles.source)
            setMultiTouchControls(false)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(16.0)
            controller.setCenter(point)
            setOnTouchListener { _, _ -> true } // não interfere com o scroll do ecrã
            overlays.add(Marker(this).apply {
                position = point
                icon = ContextCompat.getDrawable(this@ReportActivity, R.drawable.bg_location_dot)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            })
        }
    }

    private fun setupCategories() {
        Category.entries.forEachIndexed { index, category ->
            val row = if (index < 3) binding.rowCategories1 else binding.rowCategories2
            val tile = ItemCategoryBinding.inflate(layoutInflater, row, false)
            val color = Color.parseColor(category.colorHex)

            tile.ivIcon.setImageResource(iconFor(category))
            tile.ivIcon.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            tile.tvLabel.text = category.label
            tile.root.setCardBackgroundColor(ColorUtils.blendARGB(Color.WHITE, color, 0.12f))
            tile.root.strokeColor = color
            tile.root.setOnClickListener { selectCategory(category) }

            tiles[category] = tile.root
            row.addView(tile.root)
        }
        selectCategory(selectedCategory)
    }

    private fun selectCategory(category: Category) {
        selectedCategory = category
        val strokePx = (3 * resources.displayMetrics.density).toInt()
        tiles.forEach { (tileCategory, card) ->
            card.strokeWidth = if (tileCategory == category) strokePx else 0
        }
    }

    private fun iconFor(category: Category): Int = when (category) {
        Category.ACCIDENT -> R.drawable.ic_medical
        Category.ROADWORKS -> R.drawable.ic_build
        Category.TRAFFIC -> R.drawable.ic_car
        Category.HAZARD -> R.drawable.ic_warning
        Category.ENFORCEMENT -> R.drawable.ic_shield
        Category.ROAD_CLOSED -> R.drawable.ic_block
    }

    private fun launchCamera() {
        val directory = File(filesDir, "photos").apply { mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        photoFile = file
        takePicture.launch(uri)
    }

    private fun submit() {
        val occurrence = Occurrence(
            id = UUID.randomUUID().toString(),
            userId = FirebaseAuth.getInstance().currentUser?.uid ?: "local",
            category = selectedCategory,
            description = binding.etDescription.text.toString().trim(),
            latitude = latitude,
            longitude = longitude,
            timestamp = timestamp,
            photoPath = photoFile?.absolutePath,
            synced = false
        )
        binding.btnSubmit.isEnabled = false
        lifecycleScope.launch {
            OccurrenceRepository(applicationContext).save(occurrence) // guarda sempre localmente primeiro
            SyncWorker.enqueue(applicationContext)                    // envia quando houver Internet
            Toast.makeText(this@ReportActivity, "Ocorrência registada.", Toast.LENGTH_SHORT).show()
            finish()
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
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_CATEGORY = "category"
    }
}
