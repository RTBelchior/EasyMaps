package com.example.easymap.data

import android.content.Context
import com.example.easymap.model.RouteCodec
import com.example.easymap.model.Trip
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Acesso às viagens: SQLite local (principal) + Firestore (cópia remota). */
class TripRepository(context: Context) {

    private val database = AppDatabase.get(context)

    suspend fun save(trip: Trip): Long = withContext(Dispatchers.IO) { database.insertTrip(trip) }

    suspend fun getAll(userId: String): List<Trip> = withContext(Dispatchers.IO) { database.getTrips(userId) }

    suspend fun get(id: Long): Trip? = withContext(Dispatchers.IO) { database.getTrip(id) }

    /** Envia para o Firestore as viagens ainda não sincronizadas. Lança exceção se falhar (o Worker repete). */
    suspend fun uploadPending(userId: String) {
        val pending = withContext(Dispatchers.IO) { database.getUnsyncedTrips(userId) }
        val firestore = FirebaseFirestore.getInstance()
        for (trip in pending) {
            val data = hashMapOf<String, Any?>(
                "userId" to trip.userId,
                "startTime" to trip.startTime,
                "endTime" to trip.endTime,
                "distanceMeters" to trip.distanceMeters,
                "durationSeconds" to trip.durationSeconds,
                "avgSpeedKmh" to trip.avgSpeedKmh,
                "startName" to trip.startName,
                "endName" to trip.endName,
                "route" to RouteCodec.encode(trip.route)
            )
            firestore.collection("users").document(userId)
                .collection("trips").document(trip.id.toString())
                .set(data).await()
            withContext(Dispatchers.IO) { database.markTripSynced(trip.id) }
        }
    }
}
