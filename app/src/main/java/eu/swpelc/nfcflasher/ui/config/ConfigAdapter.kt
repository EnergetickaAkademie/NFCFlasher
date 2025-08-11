package eu.swpelc.nfcflasher.ui.config

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import eu.swpelc.nfcflasher.R

class ConfigAdapter(
    private val onItemClick: (BuildingConfigDisplayItem) -> Unit
) : ListAdapter<BuildingConfigDisplayItem, ConfigAdapter.ConfigViewHolder>(ConfigDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConfigViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_config, parent, false)
        return ConfigViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: ConfigViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ConfigViewHolder(
        itemView: View,
        private val onItemClick: (BuildingConfigDisplayItem) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.textViewItemBuildingName)
        private val valueTextView: TextView = itemView.findViewById(R.id.textViewItemBuildingValue)
        private val overriddenIndicator: TextView = itemView.findViewById(R.id.textViewItemOverriddenIndicator)

        fun bind(item: BuildingConfigDisplayItem) {
            nameTextView.text = item.typeName
            valueTextView.text = item.effectiveValue.toString()

            if (item.isOverridden) {
                overriddenIndicator.visibility = View.VISIBLE
                // Optionally, make text bold or change color to indicate override
                valueTextView.setTypeface(null, Typeface.BOLD)
                valueTextView.setTextColor(ContextCompat.getColor(itemView.context, R.color.dark_red_500)) // Using project color
            } else {
                overriddenIndicator.visibility = View.GONE
                valueTextView.setTypeface(null, Typeface.NORMAL)
                valueTextView.setTextColor(ContextCompat.getColor(itemView.context, R.color.black)) // Using project color
            }

            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}

class ConfigDiffCallback : DiffUtil.ItemCallback<BuildingConfigDisplayItem>() {
    override fun areItemsTheSame(oldItem: BuildingConfigDisplayItem, newItem: BuildingConfigDisplayItem): Boolean {
        return oldItem.typeName == newItem.typeName
    }

    override fun areContentsTheSame(oldItem: BuildingConfigDisplayItem, newItem: BuildingConfigDisplayItem): Boolean {
        return oldItem == newItem
    }
}

