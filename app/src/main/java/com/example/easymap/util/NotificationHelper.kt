package com.example.easymap.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.easymap.MainActivity
import com.example.easymap.R

/** Criação de canais e notificações (viagem em curso e alertas de ocorrências próximas). */
object NotificationHelper {

    private const val CHANNEL_TRIP = "trip_channel"
    private const val CHANNEL_ALERTS = "alerts_channel"
    const val TRIP_NOTIFICATION_ID = 1

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TRIP, "Viagem em curso", NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Alertas de ocorrências", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
    )

    fun buildTripNotification(context: Context, text: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_TRIP)
            .setSmallIcon(R.drawable.ic_map)
            .setContentTitle("EasyMaps")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openAppIntent(context))
            .build()

    @SuppressLint("MissingPermission")
    fun notifyAlert(context: Context, id: Int, title: String, text: String) {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        if (needsPermission &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_map)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
