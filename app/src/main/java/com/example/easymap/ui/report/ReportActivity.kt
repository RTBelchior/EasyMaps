package com.example.easymap.ui.report

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.easymap.R
import com.example.easymap.data.OccurrenceRepository
import com.example.easymap.data.SyncWorker
import com.example.easymap.databinding.ActivityReportBinding
import com.example.easymap.model.Category
import com.example.easymap.model.Occurrence
import com.example.easymap.util.Formatters
import com.example.easymap.util.ImageUtils
import com.example.easymap.util.MapTiles
import com.example.easymap.util.applySystemBarsPadding
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * Ecrã de reporte de ocorrências com pré-visualização de mapa, dados de localização/velocidade,
 * grelha de categorias personalizadas, fotografia e descrição.
 */
class ReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportBinding
    private var latitude = 0.0
    private var longitude = 0.0
    private var speedKmh = 0.0
    private val timestamp = System.currentTimeMillis()
    private var selectedCategory: Category = Category.HAZARD
    private var isDarkMap = false

    private var photoFile: File? = null
    private var photoUri: Uri? = null
    private val categoryCardMap = mutableMapOf<Category, MaterialCardView>()

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = photoFile
        if (saved && file != null) {
            ImageUtils.decodeSampled(file.absolutePath, 800)?.let { bitmap ->
                binding.ivPhoto.setImageBitmap(bitmap)
                binding.ivPhoto.isVisible = true
                binding.ivPhotoPlaceholder.isVisible = false
                binding.btnPhoto.text = "Tirar outra foto"
            }
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
        speedKmh = intent.getDoubleExtra(EXTRA_SPEED, 0.0)
        selectedCategory = Category.fromName(intent.getStringExtra(EXTRA_CATEGORY))

        setupHeader()
        setupMap()
        setupInfoCard()
        setupCategoryGrid()
        setupPhotoAndSubmit()
    }

    private fun setupHeader() {
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun setupMap() {
        binding.mapView.apply {
            setTileSource(MapTiles.source)
            setMultiTouchControls(true)
            setBuiltInZoomControls(false)
            controller.setZoom(16.5)
            val point = GeoPoint(latitude, longitude)
            controller.setCenter(point)

            val marker = Marker(this).apply {
                position = point
                icon = ContextCompat.getDrawable(this@ReportActivity, R.drawable.bg_location_dot)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
            overlays.add(marker)
        }

        binding.btnMapRecenter.setOnClickListener {
            binding.mapView.controller.animateTo(GeoPoint(latitude, longitude), 16.5, 500L)
        }

        binding.btnMapLayers.setOnClickListener {
            isDarkMap = !isDarkMap
            binding.mapView.overlayManager.tilesOverlay.setColorFilter(
                if (isDarkMap) TilesOverlay.INVERT_COLORS else null
            )
            binding.mapView.invalidate()
        }
    }

    private fun setupInfoCard() {
        binding.tvCoordinates.text = String.format(Locale.US, "%.4f, %.4f", latitude, longitude)
        binding.tvDatetime.text = Formatters.dateTime(timestamp)
        binding.tvSpeed.text = Formatters.speed(speedKmh)
    }

    private fun setupCategoryGrid() {
        val grid = binding.gridCategories
        grid.removeAllViews()
        categoryCardMap.clear()

        for ((index, category) in Category.entries.withIndex()) {
            val card = createCategoryCard(category)
            categoryCardMap[category] = card

            val param = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(index % 3, 1f)
                rowSpec = GridLayout.spec(index / 3, 1f)
                setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            }
            grid.addView(card, param)
        }

        updateCategorySelection(selectedCategory)
    }

    private fun createCategoryCard(category: Category): MaterialCardView {
        val card = MaterialCardView(this).apply {
            radius = dpToPx(16).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.parseColor(category.bgHex))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                selectedCategory = category
                updateCategorySelection(category)
            }
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(8), dpToPx(14), dpToPx(8), dpToPx(14))
        }

        // Circular Background Container for Category Icon
        val iconContainer = FrameLayout(this).apply {
            val size = dpToPx(48)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(category.iconBgHex))
            }
        }

        val iconView = ImageView(this).apply {
            val iconSize = dpToPx(24)
            layoutParams = FrameLayout.LayoutParams(iconSize, iconSize).apply {
                gravity = Gravity.CENTER
            }
            setImageResource(category.iconResId)
            setColorFilter(Color.WHITE)
        }
        iconContainer.addView(iconView)
        contentLayout.addView(iconContainer)

        // Label TextView
        val labelView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(8)
            }
            text = category.label
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0F172A"))
            gravity = Gravity.CENTER
        }
        contentLayout.addView(labelView)

        card.addView(contentLayout)
        return card
    }

    private fun updateCategorySelection(selected: Category) {
        for ((cat, card) in categoryCardMap) {
            val isSelected = cat == selected
            if (isSelected) {
                card.strokeColor = Color.parseColor("#2563EB")
                card.strokeWidth = dpToPx(3)
                card.cardElevation = dpToPx(3).toFloat()
            } else {
                card.strokeColor = Color.TRANSPARENT
                card.strokeWidth = 0
                card.cardElevation = 0f
            }
        }
    }

    private fun setupPhotoAndSubmit() {
        binding.btnPhoto.setOnClickListener { launchCamera() }
        binding.btnSubmit.setOnClickListener { submit() }
        binding.etDescription.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.root.postDelayed({
                    binding.root.fullScroll(View.FOCUS_DOWN)
                }, 200)
            }
        }
    }

    private fun launchCamera() {
        val directory = File(filesDir, "photos").apply { mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        photoFile = file
        photoUri = uri
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
            OccurrenceRepository(applicationContext).save(occurrence)
            SyncWorker.enqueue(applicationContext)
            Toast.makeText(this@ReportActivity, "Ocorrência registada.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun Context.dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        binding.mapView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        binding.mapView.onDetach()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_CATEGORY = "category"
        const val EXTRA_SPEED = "speed"
    }
}
