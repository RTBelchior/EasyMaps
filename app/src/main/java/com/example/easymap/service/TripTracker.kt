package com.example.easymap.service

import com.example.easymap.model.LatLon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Estado partilhado da viagem em curso. O [TripService] escreve; os ecrãs apenas observam.
 * Assim a interface não fica acoplada ao serviço.
 */
object TripTracker {

    enum class State { IDLE, RUNNING, PAUSED }

    data class Snapshot(
        val state: State = State.IDLE,
        val distanceMeters: Double = 0.0,
        val elapsedSeconds: Long = 0L,
        val speedKmh: Double = 0.0,
        val route: List<LatLon> = emptyList(),
        val lastLocation: LatLon? = null
    ) {
        val averageSpeedKmh: Double
            get() = if (elapsedSeconds > 0) distanceMeters / elapsedSeconds * 3.6 else 0.0
    }

    private val mutableSnapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = mutableSnapshot.asStateFlow()

    fun update(transform: (Snapshot) -> Snapshot) = mutableSnapshot.update(transform)

    fun reset() {
        mutableSnapshot.value = Snapshot()
    }
}
