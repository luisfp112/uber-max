package com.ubermax.app.ui.history

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.ubermax.app.R
import com.ubermax.app.databinding.ActivityHistoryBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Historial completo de ofertas evaluadas, con filtro temporal y gráfico de
 * ganancia promedio por hora.
 */
@AndroidEntryPoint
class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private val viewModel: HistoryViewModel by viewModels()
    private val adapter = TripLogAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter

        binding.toggleFilter.check(R.id.btn_filter_today)
        binding.toggleFilter.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            viewModel.setFilter(
                when (checkedId) {
                    R.id.btn_filter_7d -> HistoryFilter.LAST_7D
                    R.id.btn_filter_30d -> HistoryFilter.LAST_30D
                    R.id.btn_filter_all -> HistoryFilter.ALL
                    else -> HistoryFilter.TODAY
                }
            )
        }

        observe()
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    adapter.submitList(state.trips)
                    binding.tvHistoryEmpty.visibility =
                        if (state.trips.isEmpty()) View.VISIBLE else View.GONE

                    binding.chartHours.setHours(state.hours)
                    val hasHours = state.hours.isNotEmpty()
                    binding.chartHours.visibility = if (hasHours) View.VISIBLE else View.GONE
                    binding.tvChartEmpty.visibility = if (hasHours) View.GONE else View.VISIBLE
                }
            }
        }
    }
}
