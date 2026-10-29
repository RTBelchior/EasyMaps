package com.example.easymap.ui.report

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.easymap.data.OccurrenceRepository
import com.example.easymap.data.SyncWorker
import com.example.easymap.databinding.ActivityReportBinding
import com.example.easymap.model.Category
import com.example.easymap.model.Occurrence
import com.example.easymap.util.Formatters
import com.example.easymap.util.ImageUtils
import com.example.easymap.util.applySystemBarsPadding
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * Registo de uma ocorrência. Coordenadas, data e hora são obtidas automaticamente
 * (vêm do GPS/toque longo no mapa e do relógio). A fotografia usa a câmara do smartphone.
 */
class ReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportBinding
    private var latitude = 0.0
    private var longitude = 0.0
    private val timestamp = System.currentTimeMillis()
    private var photoFile: File? = null
    private var photoUri: Uri? = null

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = photoFile
        if (saved && file != null) {
            binding.ivPhoto.setImageBitmap(ImageUtils.decodeSampled(file.absolutePath, 800))
            binding.ivPhoto.visibility = View.VISIBLE
            binding.btnPhoto.text = "Tirar outra fotografia"
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
        binding.tvCoordinates.text = String.format(Locale.US, "Coordenadas: %.5f, %.5f", latitude, longitude)
        binding.tvDatetime.text = "Data e hora: ${Formatters.dateTime(timestamp)}"

        setupCategories(Category.fromName(intent.getStringExtra(EXTRA_CATEGORY)))
        binding.btnPhoto.setOnClickListener { launchCamera() }
        binding.btnSubmit.setOnClickListener { submit() }
    }

    private fun setupCategories(selected: Category) {
        for (category in Category.entries) {
            val chip = Chip(this).apply {
                id = View.generateViewId()
                text = category.label
                isCheckable = true
                tag = category
                isChecked = category == selected
            }
            binding.chipGroup.addView(chip)
        }
    }

    private fun selectedCategory(): Category {
        val chip = binding.chipGroup.findViewById<Chip>(binding.chipGroup.checkedChipId)
        return (chip?.tag as? Category) ?: Category.HAZARD
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
            category = selectedCategory(),
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

    companion object {
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_CATEGORY = "category"
    }
}
