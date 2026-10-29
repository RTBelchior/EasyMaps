package com.example.easymap.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * SENSOR 1 – Acelerómetro: deteta travagens/impactos bruscos durante uma viagem.
 * Compara o módulo da aceleração com a gravidade; um desvio grande indica travagem brusca.
 * O sensor só fica registado enquanto é preciso (poupança de bateria).
 */
class HardBrakeDetector(context: Context, private val onHardBrake: () -> Unit) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var active = false
    private var lastTrigger = 0L

    /** Verificação de existência do sensor (nem todos os dispositivos o têm). */
    val isAvailable: Boolean get() = accelerometer != null

    fun start() {
        val sensor = accelerometer ?: return
        if (active) return
        active = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        if (!active) return
        sensorManager.unregisterListener(this)
        active = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val magnitude = sqrt(
            event.values[0] * event.values[0] +
                event.values[1] * event.values[1] +
                event.values[2] * event.values[2]
        )
        val deviation = abs(magnitude - SensorManager.GRAVITY_EARTH)
        val now = SystemClock.elapsedRealtime()
        if (deviation > THRESHOLD && now - lastTrigger > COOLDOWN_MS) {
            lastTrigger = now
            onHardBrake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val THRESHOLD = 6.0f      // m/s² acima/abaixo da gravidade
        const val COOLDOWN_MS = 30_000L // evita alertas repetidos
    }
}

/**
 * SENSOR 2 – Luminosidade: informa o ecrã para trocar o mapa para modo escuro à noite/em túneis.
 */
class AmbientLightMonitor(context: Context, private val onLux: (Float) -> Unit) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val lightSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    private var active = false

    val isAvailable: Boolean get() = lightSensor != null

    fun start() {
        val sensor = lightSensor ?: return
        if (active) return
        active = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        if (!active) return
        sensorManager.unregisterListener(this)
        active = false
    }

    override fun onSensorChanged(event: SensorEvent) = onLux(event.values[0])

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
