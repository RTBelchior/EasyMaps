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

    @SuppressLint("NotifyDataSetChanged")
    fun submit(trips: List<Trip>) {
        items = trips
        notifyDataSetChanged()
    }

    class Holder(val binding: ItemTripBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemTripBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val trip = items[position]
        with(holder.binding) {
            tvTime.text = "${Formatters.dateTime(trip.startTime)} – ${Formatters.time(trip.endTime)}"
            tvDistance.text = Formatters.distance(trip.distanceMeters)
            tvRoute.text = "${trip.startName} → ${trip.endName}"
            tvMeta.text = "${Formatters.duration(trip.durationSeconds)} · média ${Formatters.speed(trip.avgSpeedKmh)}"
            root.setOnClickListener { onClick(trip) }
        }
    }

    override fun getItemCount() = items.size
}
