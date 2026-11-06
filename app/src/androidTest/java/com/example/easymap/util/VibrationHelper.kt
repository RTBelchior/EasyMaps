package com.example.easymap.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/** Feedback háptico (vibração) para alertas. */
object VibrationHelper {

    @Suppress("DEPRECATION")
    fun vibrate(context: Context, durationMs: Long) {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(durationMs)
        }
    }
}
