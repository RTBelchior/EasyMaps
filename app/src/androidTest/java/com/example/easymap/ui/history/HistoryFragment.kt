package com.example.easymap.ui.history

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.easymap.data.TripRepository
import com.example.easymap.databinding.FragmentHistoryBinding
import com.example.easymap.model.Trip
import com.example.easymap.util.Formatters
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.Calendar

/** Histórico de deslocações com filtro por Hoje / Semana / Mês. */
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private var allTrips: List<Trip> = emptyList()
    private val adapter = TripAdapter { trip ->
        startActivity(
            Intent(requireContext(), TripDetailActivity::class.java).putExtra(TripDetailActivity.EXTRA_TRIP_ID, trip.id)
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
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) = applyFilter()
            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
        })
    }

    override fun onResume() {
        super.onResume()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "local"
        viewLifecycleOwner.lifecycleScope.launch {
            allTrips = TripRepository(requireContext()).getAll(userId)
            if (_binding != null) applyFilter()
        }
    }

    private fun applyFilter() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            when (binding.tabs.selectedTabPosition) {
                1 -> add(Calendar.DAY_OF_YEAR, -6)
                2 -> add(Calendar.DAY_OF_YEAR, -29)
            }
        }
        val filtered = allTrips.filter { it.startTime >= calendar.timeInMillis }
        adapter.submit(filtered)
        binding.tvEmpty.isVisible = filtered.isEmpty()
        binding.tvSummary.text =
            "${Formatters.distance(filtered.sumOf { it.distanceMeters })} · ${filtered.size} viagens"
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
