package com.example.easymap.util

import android.content.Context
import android.location.Geocoder
import com.example.easymap.model.LatLon
import java.util.Locale

/** Serviço de geocodificação do Android: converte coordenadas no nome da rua. Chamar fora da thread principal. */
object GeocodingHelper {

    fun placeName(context: Context, point: LatLon?): String {
        if (point == null) return "Desconhecido"
        val fallback = String.format(Locale.US, "%.4f, %.4f", point.latitude, point.longitude)
        if (!Geocoder.isPresent()) return fallback
        return try {
            @Suppress("DEPRECATION")
            val addresses = Geocoder(context, Locale.getDefault()).getFromLocation(point.latitude, point.longitude, 1)
            val address = addresses?.firstOrNull() ?: return fallback
            address.thoroughfare ?: address.locality ?: fallback
        } catch (e: Exception) {
            fallback
        }
    }
}
