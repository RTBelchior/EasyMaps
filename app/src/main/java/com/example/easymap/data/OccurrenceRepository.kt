package com.example.easymap.data

import android.content.Context
import com.example.easymap.model.Category
import com.example.easymap.model.Occurrence
import com.example.easymap.util.ImageUtils
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Ocorrências: guardadas primeiro em SQLite (funciona offline) e depois enviadas para o Firestore,
 * de onde os outros utilizadores as descarregam. A foto segue como miniatura em Base64 dentro do
 * documento (evita o Firebase Storage, que exige plano pago).
 */
class OccurrenceRepository(context: Context) {

    private val appContext = context.applicationContext
    private val database = AppDatabase.get(appContext)

    suspend fun save(occurrence: Occurrence) = withContext(Dispatchers.IO) { database.upsertOccurrence(occurrence) }

    suspend fun getAll(): List<Occurrence> = withContext(Dispatchers.IO) { database.getOccurrences() }

    suspend fun uploadPending(userId: String) {
        val pending = withContext(Dispatchers.IO) { database.getUnsyncedOccurrences(userId) }
        val firestore = FirebaseFirestore.getInstance()
        for (occurrence in pending) {
            val photo = occurrence.photoPath?.let { path ->
                withContext(Dispatchers.IO) { ImageUtils.toBase64Thumbnail(path) }
            }
            val data = hashMapOf<String, Any?>(
                "id" to occurrence.id,
                "userId" to occurrence.userId,
                "category" to occurrence.category.name,
                "description" to occurrence.description,
                "latitude" to occurrence.latitude,
                "longitude" to occurrence.longitude,
                "timestamp" to occurrence.timestamp,
                "photoBase64" to photo
            )
            firestore.collection(COLLECTION).document(occurrence.id).set(data).await()
            withContext(Dispatchers.IO) { database.markOccurrenceSynced(occurrence.id) }
        }
    }

    /** Descarrega as ocorrências de todos os utilizadores e guarda-as na cache local. */
    suspend fun downloadAll() {
        val snapshot = FirebaseFirestore.getInstance().collection(COLLECTION)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(300)
            .get().await()

        withContext(Dispatchers.IO) {
            val local = database.getOccurrences().associateBy { it.id }
            for (document in snapshot.documents) {
                val id = document.getString("id") ?: document.id
                val existing = local[id]
                if (existing != null && !existing.synced) continue // não apagar alterações locais pendentes
                val latitude = document.getDouble("latitude") ?: continue
                val longitude = document.getDouble("longitude") ?: continue

                var photoPath = existing?.photoPath
                if (photoPath == null) {
                    val base64 = document.getString("photoBase64")
                    if (base64 != null) {
                        val file = File(File(appContext.filesDir, "photos"), "$id.jpg")
                        if (ImageUtils.saveFromBase64(base64, file)) photoPath = file.absolutePath
                    }
                }
                database.upsertOccurrence(
                    Occurrence(
                        id = id,
                        userId = document.getString("userId") ?: "",
                        category = Category.fromName(document.getString("category")),
                        description = document.getString("description") ?: "",
                        latitude = latitude,
                        longitude = longitude,
                        timestamp = document.getLong("timestamp") ?: 0L,
                        photoPath = photoPath,
                        synced = true
                    )
                )
            }
        }
    }

    private companion object {
        const val COLLECTION = "occurrences"
    }
}
