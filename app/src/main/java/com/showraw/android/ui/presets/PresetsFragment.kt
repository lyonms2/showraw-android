package com.showraw.android.ui.presets

import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.showraw.android.Navigator
import com.showraw.android.R
import com.showraw.android.databinding.FragmentPresetsBinding
import com.showraw.android.presets.CustomPresetStore
import com.showraw.android.presets.Preset
import com.showraw.android.presets.PresetRepository

class PresetsFragment : Fragment() {

    private var _binding: FragmentPresetsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: PresetAdapter

    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        try {
            val json = requireContext().contentResolver.openInputStream(uri)
                ?.bufferedReader()?.readText() ?: return@registerForActivityResult
            if (CustomPresetStore.importFromJson(requireContext(), json)) {
                refreshPresets()
                Toast.makeText(requireContext(), "Preset importado!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Arquivo inválido ou corrompido.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Erro ao importar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPresetsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = PresetAdapter(
            onSelect    = { preset -> updateSummary(preset) },
            onAdd       = { (requireActivity() as? Navigator)?.showCustomPresetEditor(null) },
            onLongPress = { preset -> showPresetOptions(preset) },
        )
        binding.rvPresets.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvPresets.adapter = adapter

        binding.btnRecord.setOnClickListener {
            val selected = adapter.getSelectedPreset() ?: return@setOnClickListener
            if (availableMinutes(selected) < 5) return@setOnClickListener
            val nav = requireActivity() as? Navigator ?: return@setOnClickListener
            nav.startRecording(selected.id)
        }

        binding.btnLibrary.setOnClickListener {
            (requireActivity() as? Navigator)?.showLibrary()
        }

        binding.btnImport.setOnClickListener {
            importLauncher.launch("*/*")
        }

        binding.btnManual.setOnClickListener {
            (requireActivity() as? Navigator)?.showManual()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPresets()
    }

    private fun refreshPresets() {
        adapter.setData(PresetRepository.all, CustomPresetStore.load(requireContext()))
        val selected = adapter.getSelectedPreset() ?: PresetRepository.all.first()
        updateSummary(selected)
    }

    private fun showPresetOptions(preset: Preset) {
        AlertDialog.Builder(requireContext())
            .setTitle("${preset.emoji} ${preset.name}")
            .setItems(arrayOf("✏  Editar", "📤  Exportar", "🗑  Excluir")) { _, which ->
                when (which) {
                    0 -> (requireActivity() as? Navigator)?.showCustomPresetEditor(preset.id)
                    1 -> exportPreset(preset)
                    2 -> AlertDialog.Builder(requireContext())
                            .setMessage("Excluir \"${preset.name}\"?")
                            .setPositiveButton("Excluir") { _, _ ->
                                CustomPresetStore.delete(requireContext(), preset.id)
                                refreshPresets()
                            }
                            .setNegativeButton("Cancelar", null)
                            .show()
                }
            }
            .show()
    }

    private fun exportPreset(preset: Preset) {
        val json = CustomPresetStore.toExportJson(preset)
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, json)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Preset ShowRaw: ${preset.name}")
        }
        startActivity(android.content.Intent.createChooser(intent, "Exportar preset"))
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

    private fun availableMinutes(preset: Preset): Long = getFreeStorageMb() / preset.estimatedMbPerMin

    private fun getFreeStorageMb(): Long {
        val path = requireContext().getExternalFilesDir(null)?.absolutePath
            ?: Environment.getExternalStorageDirectory().absolutePath
        return StatFs(path).let { it.availableBlocksLong * it.blockSizeLong / (1024L * 1024L) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
