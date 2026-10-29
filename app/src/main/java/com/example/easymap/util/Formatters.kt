package com.example.easymap.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Funções de formatação de valores para mostrar ao utilizador. */
object Formatters {

    fun distance(meters: Double): String =
        if (meters < 1000) "${meters.roundToInt()} m"
        else String.format(Locale.getDefault(), "%.1f km", meters / 1000)

    fun duration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, secs)
        else String.format(Locale.getDefault(), "%02d:%02d", minutes, secs)
    }

    fun speed(kmh: Double): String = String.format(Locale.getDefault(), "%.0f km/h", kmh)

    fun dateTime(millis: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(millis))

    fun time(millis: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
}
