package com.example.easymap.model

data class LatLon(val latitude: Double, val longitude: Double)

/** Deslocação guardada no histórico. */
data class Trip(
    val id: Long = 0,
    val userId: String,
    val startTime: Long,
    val endTime: Long,
    val distanceMeters: Double,
    val durationSeconds: Long,
    val avgSpeedKmh: Double,
    val startName: String,
    val endName: String,
    val route: List<LatLon>,
    val synced: Boolean = false
)

/** Converte o percurso para texto ("lat,lon;lat,lon") para guardar numa única coluna. */
object RouteCodec {
    fun encode(points: List<LatLon>): String =
        points.joinToString(";") { "${it.latitude},${it.longitude}" }

    fun decode(text: String): List<LatLon> {
        if (text.isBlank()) return emptyList()
        return text.split(";").mapNotNull { part ->
            val values = part.split(",")
            val latitude = values.getOrNull(0)?.toDoubleOrNull()
            val longitude = values.getOrNull(1)?.toDoubleOrNull()
            if (latitude != null && longitude != null) LatLon(latitude, longitude) else null
        }
    }
}
