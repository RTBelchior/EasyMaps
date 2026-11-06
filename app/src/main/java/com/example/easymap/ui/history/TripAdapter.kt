package com.example.easymap.ui.history

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.easymap.databinding.ItemTripBinding
import com.example.easymap.model.Trip
import com.example.easymap.util.Formatters

/** Lista de viagens do histórico. */
class TripAdapter(private val onClick: (Trip) -> Unit) : RecyclerView.Adapter<TripAdapter.Holder>() {

    private var items: List<Trip> = emptyList()
    private var occurrenceCounts: Map<Long, Int> = emptyMap()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(trips: List<Trip>, occurrences: Map<Long, Int> = emptyMap()) {
        items = trips
        occurrenceCounts = occurrences
        notifyDataSetChanged()
    }

    class Holder(val binding: ItemTripBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemTripBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val trip = items[position]
        val count = occurrenceCounts[trip.id] ?: 0
        val occurrenceText = when (count) {
            1 -> "1 ocorrência"
            else -> "$count ocorrências"
        }

        with(holder.binding) {
            tvTime.text = "${Formatters.time(trip.startTime)} – ${Formatters.time(trip.endTime)}"
            tvDistance.text = Formatters.distance(trip.distanceMeters)
            tvRoute.text = "${trip.startName} → ${trip.endName}"
            tvMeta.text = "${Formatters.durationShort(trip.durationSeconds)}   ${Formatters.speed(trip.avgSpeedKmh)}   $occurrenceText"
            root.setOnClickListener { onClick(trip) }
        }
    }

    override fun getItemCount() = items.size
}
