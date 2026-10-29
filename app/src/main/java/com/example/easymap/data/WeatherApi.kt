package com.example.easymap.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class Weather(val temperatureC: Double, val description: String)

/** API externa: Open-Meteo (meteorologia, gratuita e sem chave). Devolve null se falhar. */
object WeatherApi {

    suspend fun fetch(latitude: Double, longitude: Double): Weather? = withContext(Dispatchers.IO) {
        try {
            val url = URL(
                String.format(
                    Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code",
                    latitude, longitude
                )
            )
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            try {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val current = JSONObject(body).getJSONObject("current")
                Weather(current.getDouble("temperature_2m"), describe(current.getInt("weather_code")))
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun describe(code: Int): String = when (code) {
        0 -> "Céu limpo"
        1, 2, 3 -> "Parcialmente nublado"
        45, 48 -> "Nevoeiro"
        in 51..57 -> "Chuvisco"
        in 61..67 -> "Chuva"
        in 71..77 -> "Neve"
        in 80..82 -> "Aguaceiros"
        in 95..99 -> "Trovoada"
        else -> "Tempo variável"
    }
}
