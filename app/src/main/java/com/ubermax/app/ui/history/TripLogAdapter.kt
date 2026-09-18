package com.ubermax.app.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ubermax.app.R
import com.ubermax.app.data.db.entity.TripLogEntity
import com.ubermax.app.databinding.ItemTripLogBinding
import com.ubermax.app.domain.model.Action
import com.ubermax.app.domain.model.HudReasonFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lista del historial de ofertas. Muestra decisión, destino abreviado, hora y
 * métricas económicas de cada registro.
 */
class TripLogAdapter : ListAdapter<TripLogEntity, TripLogAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTripLogBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemTripLogBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(trip: TripLogEntity) {
            val context = binding.root.context
            val accepted = trip.decision == Action.ACCEPT.name

            val decisionLabel = when (trip.decision) {
                Action.ACCEPT.name -> context.getString(R.string.hud_accepted)
                Action.CANCEL.name -> context.getString(R.string.hud_rejected)
                Action.WARN.name -> context.getString(R.string.hud_rejected)
                else -> trip.decision
            }
            binding.tvItemDecision.text = (if (accepted) "✅ " else "❌ ") + decisionLabel
            binding.tvItemDecision.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (accepted) R.color.profit_positive else R.color.profit_negative
                )
            )

            val destination = trip.destination.ifBlank { context.getString(R.string.hud_no_destination) }
            binding.tvItemDestination.text =
                "📍 " + HudReasonFormatter.abbreviateDestination(destination)

            val time = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(trip.timestamp))
            binding.tvItemTime.text = context.getString(
                R.string.history_date_format,
                trip.dayOfWeek,
                time
            )

            binding.tvItemNet.text = context.getString(R.string.history_price_format, trip.netProfit)
            binding.tvItemNet.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (trip.netProfit >= 0) R.color.profit_positive else R.color.profit_negative
                )
            )
            binding.tvItemPerKm.text = "$%.2f/km".format(trip.profitPerKm)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TripLogEntity>() {
            override fun areItemsTheSame(oldItem: TripLogEntity, newItem: TripLogEntity) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: TripLogEntity, newItem: TripLogEntity) =
                oldItem == newItem
        }
    }
}
