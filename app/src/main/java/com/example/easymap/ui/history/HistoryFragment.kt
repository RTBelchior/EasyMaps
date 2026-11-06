package com.example.easymap.ui.history

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.easymap.R
import com.example.easymap.data.OccurrenceRepository
import com.example.easymap.data.TripRepository
import com.example.easymap.databinding.FragmentHistoryBinding
import com.example.easymap.model.Occurrence
import com.example.easymap.model.Trip
import com.example.easymap.util.Formatters
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Histórico de deslocamentos com filtro (Hoje, Semana, Mês), gráfico de barras dinâmico,
 * distância total e lista detalhada de viagens.
 */
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private var allTrips: List<Trip> = emptyList()
    private var allOccurrences: List<Occurrence> = emptyList()
    private var selectedTabPosition = 0 // 0: Hoje, 1: Semana, 2: Mês

    private val adapter = TripAdapter { trip ->
        startActivity(
            Intent(requireContext(), TripDetailActivity::class.java)
                .putExtra(TripDetailActivity.EXTRA_TRIP_ID, trip.id)
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvTrips.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTrips.adapter = adapter
        binding.rvTrips.isNestedScrollingEnabled = false

        setupTabs()
    }

    private fun setupTabs() {
        val tabs = listOf(binding.tabToday, binding.tabWeek, binding.tabMonth)
        tabs.forEachIndexed { index, textView ->
            textView.setOnClickListener {
                if (selectedTabPosition != index) {
                    selectedTabPosition = index
                    updateTabUi(tabs, index)
                    applyFilter()
                }
            }
        }
    }

    private fun updateTabUi(tabs: List<TextView>, selectedIndex: Int) {
        tabs.forEachIndexed { index, textView ->
            if (index == selectedIndex) {
                textView.setBackgroundResource(R.drawable.bg_tab_selected)
                textView.setTextColor(Color.parseColor("#1D4ED8"))
            } else {
                textView.setBackgroundResource(android.R.color.transparent)
                textView.setTextColor(Color.parseColor("#64748B"))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "local"
        viewLifecycleOwner.lifecycleScope.launch {
            val context = context ?: return@launch
            allTrips = TripRepository(context).getAll(userId)
            try {
                allOccurrences = OccurrenceRepository(context).getAll()
            } catch (e: Exception) {
                allOccurrences = emptyList()
            }
            if (_binding != null) applyFilter()
        }
    }

    private fun applyFilter() {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val startTime = when (selectedTabPosition) {
            0 -> calendar.timeInMillis
            1 -> {
                calendar.add(Calendar.DAY_OF_YEAR, -6)
                calendar.timeInMillis
            }
            else -> {
                calendar.add(Calendar.DAY_OF_YEAR, -29)
                calendar.timeInMillis
            }
        }

        val filtered = allTrips.filter { it.startTime >= startTime }.sortedByDescending { it.startTime }

        val occurrenceCounts = mutableMapOf<Long, Int>()
        for (trip in filtered) {
            val count = allOccurrences.count { occ ->
                occ.timestamp in (trip.startTime - 300_000)..(trip.endTime + 300_000)
            }
            occurrenceCounts[trip.id] = count
        }

        adapter.submit(filtered, occurrenceCounts)
        binding.tvEmpty.isVisible = filtered.isEmpty()

        val totalMeters = filtered.sumOf { it.distanceMeters }
        binding.tvTotalDistance.text = Formatters.distance(totalMeters)

        updateChart(filtered, startTime, now)
    }

    private fun updateChart(trips: List<Trip>, startTime: Long, endTime: Long) {
        val bars = listOf(
            binding.bar0, binding.bar1, binding.bar2,
            binding.bar3, binding.bar4, binding.bar5, binding.bar6
        )

        val interval = (endTime - startTime).coerceAtLeast(1) / 7.0
        val barDistances = DoubleArray(7)

        for (trip in trips) {
            val offset = trip.startTime - startTime
            val barIndex = ((offset / interval).toInt()).coerceIn(0, 6)
            barDistances[barIndex] += trip.distanceMeters
        }

        val maxDistance = barDistances.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val peakIndex = barDistances.indices.maxByOrNull { barDistances[it] } ?: 2

        val density = resources.displayMetrics.density
        val maxBarHeightPx = (64 * density).toInt()
        val minBarHeightPx = (16 * density).toInt()

        for (i in 0..6) {
            val bar = bars[i]
            val ratio = barDistances[i] / maxDistance
            val heightPx = if (barDistances[i] > 0) {
                (ratio * maxBarHeightPx).toInt().coerceAtLeast(minBarHeightPx)
            } else {
                minBarHeightPx
            }

            val param = bar.layoutParams
            param.height = heightPx
            bar.layoutParams = param

            if (i == peakIndex && barDistances[i] > 0) {
                bar.setBackgroundResource(R.drawable.bg_chart_bar_active)
            } else {
                bar.setBackgroundResource(R.drawable.bg_chart_bar)
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
