package com.showraw.android.ui.presets

import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.showraw.android.Navigator
import com.showraw.android.R
import com.showraw.android.databinding.FragmentPresetsBinding
import com.showraw.android.presets.Preset
import com.showraw.android.presets.PresetRepository

class PresetsFragment : Fragment() {

    private var _binding: FragmentPresetsBinding? = null
    private val binding get() = _binding!!

    private var selectedPreset: Preset = PresetRepository.all.first()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPresetsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = PresetAdapter { preset ->
            selectedPreset = preset
            updateSummary(preset)
        }
        binding.rvPresets.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvPresets.adapter = adapter

        updateSummary(selectedPreset)

        binding.btnRecord.setOnClickListener {
            if (availableMinutes(selectedPreset) < 5) return@setOnClickListener
            val nav = requireActivity() as? Navigator ?: return@setOnClickListener
            if (selectedPreset.id == "manual") {
                nav.showProSettings()
            } else {
                nav.startRecording(selectedPreset.id)
            }
        }

        binding.btnLibrary.setOnClickListener {
            (requireActivity() as? Navigator)?.showLibrary()
        }
    }

    private fun updateSummary(preset: Preset) {
        binding.tvSummaryEmoji.text = preset.emoji
        binding.tvSummaryName.text  = preset.name
        binding.tvSummaryDesc.text  = preset.description

        if (preset.contextualWarning != null) {
            binding.tvSummaryWarning.visibility = View.VISIBLE
            binding.tvSummaryWarning.text = "⚠ ${preset.contextualWarning}"
        } else {
            binding.tvSummaryWarning.visibility = View.GONE
        }

        val minutes = availableMinutes(preset)
        when {
            minutes < 5  -> {
                binding.tvStorage.text = "⚠ Espaço insuficiente (~$minutes min disponíveis)"
                binding.tvStorage.setTextColor(ContextCompat.getColor(requireContext(), R.color.alert_red))
                binding.btnRecord.isEnabled = false
                binding.btnRecord.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#555555"))
            }
            minutes < 10 -> {
                binding.tvStorage.text = "⚠ Pouco espaço (~$minutes min) · ${preset.videoResolution.label}"
                binding.tvStorage.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
                binding.btnRecord.isEnabled = true
                binding.btnRecord.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#EF9F27"))
            }
            else         -> {
                binding.tvStorage.text = "~ $minutes min disponíveis · ${preset.videoResolution.label}"
                binding.tvStorage.setTextColor(Color.parseColor("#999999"))
                binding.btnRecord.isEnabled = true
                binding.btnRecord.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#EF9F27"))
            }
        }
    }

    private fun availableMinutes(preset: Preset): Long {
        return getFreeStorageMb() / preset.estimatedMbPerMin
    }

    private fun getFreeStorageMb(): Long {
        val path = requireContext().getExternalFilesDir(null)?.absolutePath
            ?: Environment.getExternalStorageDirectory().absolutePath
        val stat = StatFs(path)
        return stat.availableBlocksLong * stat.blockSizeLong / (1024L * 1024L)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
