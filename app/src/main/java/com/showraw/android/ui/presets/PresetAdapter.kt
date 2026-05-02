package com.showraw.android.ui.presets

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.showraw.android.R
import com.showraw.android.databinding.ItemPresetCardBinding
import com.showraw.android.presets.Preset
import com.showraw.android.presets.PresetRepository

class PresetAdapter(
    private val onSelect: (Preset) -> Unit,
) : RecyclerView.Adapter<PresetAdapter.VH>() {

    private val items = PresetRepository.all
    private var selectedId = items.first().id

    inner class VH(val binding: ItemPresetCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemPresetCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val preset = items[position]
        holder.binding.tvEmoji.text = preset.emoji
        holder.binding.tvName.text  = preset.name
        holder.binding.root.setBackgroundResource(
            if (preset.id == selectedId) R.drawable.bg_preset_card_selected
            else                         R.drawable.bg_preset_card_normal
        )
        holder.binding.root.setOnClickListener {
            val prevIdx = items.indexOfFirst { it.id == selectedId }
            selectedId = preset.id
            if (prevIdx >= 0) notifyItemChanged(prevIdx)
            notifyItemChanged(position)
            onSelect(preset)
        }
    }

    override fun getItemCount() = items.size
}
