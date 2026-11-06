package com.example.easymap.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.easymap.model.Category
import com.example.easymap.model.Occurrence
import com.example.easymap.model.RouteCodec
import com.example.easymap.model.Trip

/**
 * Base de dados LOCAL (SQLite). Guarda viagens e ocorrências para funcionarem sem Internet.
 * A coluna "synced" indica se o registo já foi enviado para a base de dados remota (Firestore).
 * Todas as funções fazem I/O: chamar sempre fora da thread principal (ver os repositórios).
 */
class AppDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE trips (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                userId TEXT NOT NULL,
                startTime INTEGER NOT NULL,
                endTime INTEGER NOT NULL,
                distanceMeters REAL NOT NULL,
                durationSeconds INTEGER NOT NULL,
                avgSpeedKmh REAL NOT NULL,
                startName TEXT,
                endName TEXT,
                route TEXT,
                synced INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL(
            """CREATE TABLE occurrences (
                id TEXT PRIMARY KEY,
                userId TEXT NOT NULL,
                category TEXT NOT NULL,
                description TEXT,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                timestamp INTEGER NOT NULL,
                photoPath TEXT,
                synced INTEGER NOT NULL DEFAULT 0)"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS trips")
        db.execSQL("DROP TABLE IF EXISTS occurrences")
        onCreate(db)
    }

    // ---------- Viagens ----------

    fun insertTrip(trip: Trip): Long {
        val values = ContentValues().apply {
            put("userId", trip.userId)
            put("startTime", trip.startTime)
            put("endTime", trip.endTime)
            put("distanceMeters", trip.distanceMeters)
            put("durationSeconds", trip.durationSeconds)
            put("avgSpeedKmh", trip.avgSpeedKmh)
            put("startName", trip.startName)
            put("endName", trip.endName)
            put("route", RouteCodec.encode(trip.route))
            put("synced", if (trip.synced) 1 else 0)
        }
        return writableDatabase.insert("trips", null, values)
    }

    fun getTrips(userId: String): List<Trip> =
        readableDatabase.query("trips", null, "userId = ?", arrayOf(userId), null, null, "startTime DESC")
            .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toTrip()) } }

    fun getTrip(id: Long): Trip? =
        readableDatabase.query("trips", null, "id = ?", arrayOf(id.toString()), null, null, null)
            .use { cursor -> if (cursor.moveToFirst()) cursor.toTrip() else null }

    fun getUnsyncedTrips(userId: String): List<Trip> =
        readableDatabase.query("trips", null, "userId = ? AND synced = 0", arrayOf(userId), null, null, null)
            .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toTrip()) } }

    fun markTripSynced(id: Long) {
        val values = ContentValues().apply { put("synced", 1) }
        writableDatabase.update("trips", values, "id = ?", arrayOf(id.toString()))
    }

    // ---------- Ocorrências ----------

    fun upsertOccurrence(occurrence: Occurrence) {
        val values = ContentValues().apply {
            put("id", occurrence.id)
            put("userId", occurrence.userId)
            put("category", occurrence.category.name)
            put("description", occurrence.description)
            put("latitude", occurrence.latitude)
            put("longitude", occurrence.longitude)
            put("timestamp", occurrence.timestamp)
            put("photoPath", occurrence.photoPath)
            put("synced", if (occurrence.synced) 1 else 0)
        }
        writableDatabase.insertWithOnConflict("occurrences", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getOccurrences(): List<Occurrence> =
        readableDatabase.query("occurrences", null, null, null, null, null, "timestamp DESC")
            .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toOccurrence()) } }

    fun getUnsyncedOccurrences(userId: String): List<Occurrence> =
        readableDatabase.query("occurrences", null, "userId = ? AND synced = 0", arrayOf(userId), null, null, null)
            .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toOccurrence()) } }

    fun markOccurrenceSynced(id: String) {
        val values = ContentValues().apply { put("synced", 1) }
        writableDatabase.update("occurrences", values, "id = ?", arrayOf(id))
    }

    /** Número de registos (viagens + ocorrências) ainda por sincronizar. */
    fun countUnsynced(userId: String): Int = getUnsyncedTrips(userId).size + getUnsyncedOccurrences(userId).size

    // ---------- Conversão de cursores ----------

    private fun Cursor.toTrip() = Trip(
        id = getLong(getColumnIndexOrThrow("id")),
        userId = getString(getColumnIndexOrThrow("userId")),
        startTime = getLong(getColumnIndexOrThrow("startTime")),
        endTime = getLong(getColumnIndexOrThrow("endTime")),
        distanceMeters = getDouble(getColumnIndexOrThrow("distanceMeters")),
        durationSeconds = getLong(getColumnIndexOrThrow("durationSeconds")),
        avgSpeedKmh = getDouble(getColumnIndexOrThrow("avgSpeedKmh")),
        startName = getString(getColumnIndexOrThrow("startName")) ?: "",
        endName = getString(getColumnIndexOrThrow("endName")) ?: "",
        route = RouteCodec.decode(getString(getColumnIndexOrThrow("route")) ?: ""),
        synced = getInt(getColumnIndexOrThrow("synced")) == 1
    )

    private fun Cursor.toOccurrence() = Occurrence(
        id = getString(getColumnIndexOrThrow("id")),
        userId = getString(getColumnIndexOrThrow("userId")),
        category = Category.fromName(getString(getColumnIndexOrThrow("category"))),
        description = getString(getColumnIndexOrThrow("description")) ?: "",
        latitude = getDouble(getColumnIndexOrThrow("latitude")),
        longitude = getDouble(getColumnIndexOrThrow("longitude")),
        timestamp = getLong(getColumnIndexOrThrow("timestamp")),
        photoPath = getString(getColumnIndexOrThrow("photoPath")),
        synced = getInt(getColumnIndexOrThrow("synced")) == 1
    )

    companion object {
        private const val DB_NAME = "easymaps.db"
        private const val DB_VERSION = 1

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: AppDatabase(context.applicationContext).also { instance = it }
            }
    }
}
