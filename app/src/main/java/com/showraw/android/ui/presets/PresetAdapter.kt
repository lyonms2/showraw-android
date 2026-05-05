package com.showraw.android.ui.presets

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.showraw.android.R
import com.showraw.android.databinding.ItemAddPresetBinding
import com.showraw.android.databinding.ItemPresetCardBinding
import com.showraw.android.presets.Preset

class PresetAdapter(
    private val onSelect: (Preset) -> Unit,
    private val onAdd: () -> Unit = {},
    private val onLongPress: (Preset) -> Unit = {},
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class Item {
        data class Card(val preset: Preset, val isCustom: Boolean) : Item()
        object AddNew : Item()
    }

    private val items = mutableListOf<Item>()
    private var selectedId = ""

    fun setData(builtIn: List<Preset>, custom: List<Preset>) {
        items.clear()
        builtIn.forEach { items.add(Item.Card(it, false)) }
        custom.forEach  { items.add(Item.Card(it, true)) }
        items.add(Item.AddNew)
        if (selectedId.isEmpty() || items.none { it is Item.Card && it.preset.id == selectedId }) {
            selectedId = builtIn.firstOrNull()?.id ?: ""
        }
        notifyDataSetChanged()
    }

    fun getSelectedPreset(): Preset? =
        items.filterIsInstance<Item.Card>().firstOrNull { it.preset.id == selectedId }?.preset

    inner class CardVH(val binding: ItemPresetCardBinding) : RecyclerView.ViewHolder(binding.root)
    inner class AddVH(val binding: ItemAddPresetBinding)   : RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int) = when (items[position]) {
        is Item.Card -> 0
        Item.AddNew  -> 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == 0) {
            CardVH(ItemPresetCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        } else {
            AddVH(ItemAddPresetBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is Item.Card -> {
                val vh     = holder as CardVH
                val preset = item.preset
                vh.binding.tvEmoji.text = preset.emoji
                vh.binding.tvName.text  = preset.name
                vh.binding.root.setBackgroundResource(
                    if (preset.id == selectedId) R.drawable.bg_preset_card_selected
                    else R.drawable.bg_preset_card_normal
                )
                vh.binding.root.setOnClickListener {
                    val prev = selectedId
                    selectedId = preset.id
                    val prevPos = items.indexOfFirst { it is Item.Card && it.preset.id == prev }
                    if (prevPos >= 0) notifyItemChanged(prevPos)
                    notifyItemChanged(position)
                    onSelect(preset)
                }
                if (item.isCustom) {
                    vh.binding.root.setOnLongClickListener { onLongPress(preset); true }
                } else {
                    vh.binding.root.setOnLongClickListener(null)
                }
            }
            Item.AddNew -> (holder as AddVH).binding.root.setOnClickListener { onAdd() }
        }
    }

    override fun getItemCount() = items.size
}
