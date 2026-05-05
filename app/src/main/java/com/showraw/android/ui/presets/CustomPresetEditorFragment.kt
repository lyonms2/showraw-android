package com.showraw.android.ui.presets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.showraw.android.databinding.FragmentCustomPresetEditorBinding
import com.showraw.android.presets.CustomPresetStore
import com.showraw.android.presets.Preset
import com.showraw.android.presets.Resolution
import java.util.UUID

class CustomPresetEditorFragment : Fragment() {

    private var _binding: FragmentCustomPresetEditorBinding? = null
    private val binding get() = _binding!!

    private var editingPreset: Preset? = null
    private val ratios = listOf(1.5f, 2f, 2.5f, 3f, 4f, 6f, 8f, 10f)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCustomPresetEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val presetId = arguments?.getString(ARG_PRESET_ID)
        editingPreset = presetId?.let { id ->
            CustomPresetStore.load(requireContext()).find { it.id == id }
        }

        if (editingPreset != null) {
            binding.tvEditorTitle.text = "Editar Preset"
            populate(editingPreset!!)
        } else {
            updateAllLabels()
        }

        setupSeekBars()
        binding.btnSavePreset.setOnClickListener { savePreset() }
    }

    private fun populate(p: Preset) {
        binding.etPresetEmoji.setText(p.emoji)
        binding.etPresetName.setText(p.name)
        binding.sbGain.progress          = p.inputGainDb.toInt().coerceIn(0, 30)
        binding.sbThreshold.progress     = (-p.limiterThreshold - 1).toInt().coerceIn(0, 19)
        binding.sbAttack.progress        = (p.limiterAttack - 1).toInt().coerceIn(0, 49)
        binding.sbRelease.progress       = (p.limiterRelease - 20).toInt().coerceIn(0, 480)
        binding.sbCompThreshold.progress = (-p.compressorThreshold - 6).toInt().coerceIn(0, 34)
        val ratioIdx = ratios.indexOfFirst { it == p.compressorRatio }.takeIf { it >= 0 } ?: 3
        binding.sbCompRatio.progress     = ratioIdx
        binding.sbCompMakeup.progress    = p.compressorMakeupDb.toInt().coerceIn(0, 18)
        binding.sbHpfFreq.progress       = (p.hpfFrequency - 80).toInt().coerceIn(0, 170)
        when (p.hpfRolloff) {
            12   -> binding.rgRolloff.check(binding.rbRolloff12.id)
            24   -> binding.rgRolloff.check(binding.rbRolloff24.id)
            else -> binding.rgRolloff.check(binding.rbRolloff18.id)
        }
        binding.sbEqLow.progress  = (p.eqLowGainDb  + 12).toInt().coerceIn(0, 24)
        binding.sbEqMid.progress  = (p.eqMidGainDb  + 12).toInt().coerceIn(0, 24)
        binding.sbEqHigh.progress = (p.eqHighGainDb + 12).toInt().coerceIn(0, 24)
        val gateEnabled = p.noiseGateThreshold > -59f
        binding.swNoiseGate.isChecked = gateEnabled
        binding.sbNgThreshold.progress = (-p.noiseGateThreshold - 21).toInt().coerceIn(0, 39)
        binding.swStabilization.isChecked = p.stabilization
        when (p.videoResolution) {
            Resolution.R4K_30    -> binding.rgResolution.check(binding.rbRes4k30.id)
            Resolution.R4K_60    -> binding.rgResolution.check(binding.rbRes4k60.id)
            Resolution.R1080P_30 -> binding.rgResolution.check(binding.rbRes1080p30.id)
            Resolution.R1080P_60 -> binding.rgResolution.check(binding.rbRes1080p60.id)
        }
        updateAllLabels()
    }

    private fun setupSeekBars() {
        fun onChange(bar: SeekBar, update: (Int) -> Unit) {
            bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) = update(p)
                override fun onStartTrackingTouch(sb: SeekBar) = Unit
                override fun onStopTrackingTouch(sb: SeekBar) = Unit
            })
        }
        onChange(binding.sbGain)          { v -> binding.tvGainVal.text         = if (v == 0) "0 dB" else "+$v dB" }
        onChange(binding.sbThreshold)     { v -> binding.tvThresholdVal.text    = "${-(v + 1)} dBFS" }
        onChange(binding.sbAttack)        { v -> binding.tvAttackVal.text       = "${v + 1} ms" }
        onChange(binding.sbRelease)       { v -> binding.tvReleaseVal.text      = "${v + 20} ms" }
        onChange(binding.sbCompThreshold) { v -> binding.tvCompThresholdVal.text = "${-(v + 6)} dBFS" }
        onChange(binding.sbCompRatio)     { v -> binding.tvCompRatioVal.text    = "${ratios[v]}:1" }
        onChange(binding.sbCompMakeup)    { v -> binding.tvCompMakeupVal.text   = if (v == 0) "0 dB" else "+$v dB" }
        onChange(binding.sbHpfFreq)       { v -> binding.tvHpfFreqVal.text      = "${v + 80} Hz" }
        onChange(binding.sbEqLow)         { v -> binding.tvEqLowVal.text        = fmtDb(v - 12) }
        onChange(binding.sbEqMid)         { v -> binding.tvEqMidVal.text        = fmtDb(v - 12) }
        onChange(binding.sbEqHigh)        { v -> binding.tvEqHighVal.text       = fmtDb(v - 12) }
        onChange(binding.sbNgThreshold)   { v -> binding.tvNgThresholdVal.text  = "${-(v + 21)} dBFS" }
    }

    private fun updateAllLabels() {
        val g = binding.sbGain.progress
        binding.tvGainVal.text          = if (g == 0) "0 dB" else "+$g dB"
        binding.tvThresholdVal.text     = "${-(binding.sbThreshold.progress + 1)} dBFS"
        binding.tvAttackVal.text        = "${binding.sbAttack.progress + 1} ms"
        binding.tvReleaseVal.text       = "${binding.sbRelease.progress + 20} ms"
        binding.tvCompThresholdVal.text = "${-(binding.sbCompThreshold.progress + 6)} dBFS"
        binding.tvCompRatioVal.text     = "${ratios[binding.sbCompRatio.progress]}:1"
        val mk = binding.sbCompMakeup.progress
        binding.tvCompMakeupVal.text    = if (mk == 0) "0 dB" else "+$mk dB"
        binding.tvHpfFreqVal.text       = "${binding.sbHpfFreq.progress + 80} Hz"
        binding.tvEqLowVal.text         = fmtDb(binding.sbEqLow.progress  - 12)
        binding.tvEqMidVal.text         = fmtDb(binding.sbEqMid.progress  - 12)
        binding.tvEqHighVal.text        = fmtDb(binding.sbEqHigh.progress - 12)
        binding.tvNgThresholdVal.text   = "${-(binding.sbNgThreshold.progress + 21)} dBFS"
    }

    private fun fmtDb(v: Int) = if (v >= 0) "+$v dB" else "$v dB"

    private fun savePreset() {
        val name = binding.etPresetName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "Digite um nome para o preset.", Toast.LENGTH_SHORT).show()
            return
        }
        val emoji = binding.etPresetEmoji.text.toString().trim().ifEmpty { "🎵" }
        val resolution = when (binding.rgResolution.checkedRadioButtonId) {
            binding.rbRes4k60.id    -> Resolution.R4K_60
            binding.rbRes1080p30.id -> Resolution.R1080P_30
            binding.rbRes1080p60.id -> Resolution.R1080P_60
            else                    -> Resolution.R4K_30
        }
        val rolloff = when (binding.rgRolloff.checkedRadioButtonId) {
            binding.rbRolloff12.id -> 12
            binding.rbRolloff24.id -> 24
            else                   -> 18
        }
        val ngThreshold = if (binding.swNoiseGate.isChecked)
            -(binding.sbNgThreshold.progress + 21).toFloat() else -60f

        val preset = Preset(
            id                  = editingPreset?.id ?: UUID.randomUUID().toString(),
            name                = name,
            emoji               = emoji,
            description         = editingPreset?.description ?: "",
            limiterThreshold    = -(binding.sbThreshold.progress + 1).toFloat(),
            limiterAttack       = (binding.sbAttack.progress + 1).toFloat(),
            limiterRelease      = (binding.sbRelease.progress + 20).toFloat(),
            hpfFrequency        = (binding.sbHpfFreq.progress + 80).toFloat(),
            hpfRolloff          = rolloff,
            videoResolution     = resolution,
            estimatedMbPerMin   = resolution.estimatedMb(),
            contextualWarning   = null,
            noiseGateThreshold  = ngThreshold,
            eqLowGainDb         = (binding.sbEqLow.progress  - 12).toFloat(),
            eqMidGainDb         = (binding.sbEqMid.progress  - 12).toFloat(),
            eqHighGainDb        = (binding.sbEqHigh.progress - 12).toFloat(),
            stabilization       = binding.swStabilization.isChecked,
            inputGainDb         = binding.sbGain.progress.toFloat(),
            compressorThreshold = -(binding.sbCompThreshold.progress + 6).toFloat(),
            compressorRatio     = ratios[binding.sbCompRatio.progress],
            compressorAttack    = 20f,
            compressorRelease   = 150f,
            compressorMakeupDb  = binding.sbCompMakeup.progress.toFloat(),
            maxDurationMinutes  = 0,
        )
        CustomPresetStore.save(requireContext(), preset)
        Toast.makeText(requireContext(), "Preset \"$name\" salvo!", Toast.LENGTH_SHORT).show()
        @Suppress("DEPRECATION")
        requireActivity().onBackPressed()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_PRESET_ID = "preset_id"

        fun newInstance(presetId: String? = null) = CustomPresetEditorFragment().apply {
            if (presetId != null) arguments = Bundle().apply { putString(ARG_PRESET_ID, presetId) }
        }
    }
}

private fun Resolution.estimatedMb() = when (this) {
    Resolution.R4K_30    -> 350
    Resolution.R4K_60    -> 600
    Resolution.R1080P_30 -> 130
    Resolution.R1080P_60 -> 200
}
