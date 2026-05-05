package com.showraw.android.ui.presets

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.showraw.android.databinding.FragmentCustomPresetEditorBinding
import com.showraw.android.presets.CustomPresetStore
import com.showraw.android.presets.EqBand
import com.showraw.android.presets.EqFilterType
import com.showraw.android.presets.Preset
import com.showraw.android.presets.Resolution
import java.util.UUID
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

class CustomPresetEditorFragment : Fragment() {

    private var _binding: FragmentCustomPresetEditorBinding? = null
    private val binding get() = _binding!!

    private var editingPreset: Preset? = null
    private val compRatios = listOf(1.5f, 2f, 2.5f, 3f, 4f, 6f, 8f, 10f)

    private val LOW_FREQ_MIN  =   40f; private val LOW_FREQ_MAX  =   800f
    private val MID_FREQ_MIN  =  200f; private val MID_FREQ_MAX  = 8_000f
    private val MID2_FREQ_MIN =  200f; private val MID2_FREQ_MAX = 8_000f
    private val HIGH_FREQ_MIN = 1_000f; private val HIGH_FREQ_MAX = 20_000f

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
            refreshCurve()
        }

        setupSeekBars()
        binding.btnSavePreset.setOnClickListener { savePreset() }
        binding.btnExportPreset.setOnClickListener { exportPreset() }
    }

    // ── Populate from existing preset ───────────────────────────────

    private fun populate(p: Preset) {
        binding.etPresetEmoji.setText(p.emoji)
        binding.etPresetName.setText(p.name)

        binding.sbGain.progress          = p.inputGainDb.toInt().coerceIn(0, 30)
        binding.sbThreshold.progress     = (-p.limiterThreshold - 1).toInt().coerceIn(0, 19)
        binding.sbAttack.progress        = (p.limiterAttack - 1).toInt().coerceIn(0, 49)
        binding.sbRelease.progress       = (p.limiterRelease - 20).toInt().coerceIn(0, 480)
        binding.sbCompThreshold.progress = (-p.compressorThreshold - 6).toInt().coerceIn(0, 34)
        val ratioIdx = compRatios.indexOfFirst { it == p.compressorRatio }.takeIf { it >= 0 } ?: 3
        binding.sbCompRatio.progress     = ratioIdx
        binding.sbCompMakeup.progress    = p.compressorMakeupDb.toInt().coerceIn(0, 18)
        binding.sbCompAttack.progress    = (p.compressorAttack - 1).toInt().coerceIn(0, 199)
        binding.sbCompRelease.progress   = (p.compressorRelease - 50).toInt().coerceIn(0, 450)
        binding.sbHpfFreq.progress       = (p.hpfFrequency - 80).toInt().coerceIn(0, 170)
        when (p.hpfRolloff) {
            12   -> binding.rgRolloff.check(binding.rbRolloff12.id)
            24   -> binding.rgRolloff.check(binding.rbRolloff24.id)
            else -> binding.rgRolloff.check(binding.rbRolloff18.id)
        }

        val low   = p.eqBands.firstOrNull { it.type == EqFilterType.LOW_SHELF }
        val peaks = p.eqBands.filter { it.type == EqFilterType.PEAKING }
        val mid   = peaks.getOrNull(0)
        val mid2  = peaks.getOrNull(1)
        val high  = p.eqBands.firstOrNull { it.type == EqFilterType.HIGH_SHELF }
        low?.let {
            binding.sbEqLowFreq.progress  = freqToProgress(it.freq, LOW_FREQ_MIN, LOW_FREQ_MAX)
            binding.sbEqLowGain.progress  = (it.gainDb + 12).toInt().coerceIn(0, 24)
        }
        mid?.let {
            binding.sbEqMidFreq.progress  = freqToProgress(it.freq, MID_FREQ_MIN, MID_FREQ_MAX)
            binding.sbEqMidGain.progress  = (it.gainDb + 12).toInt().coerceIn(0, 24)
            binding.sbEqMidQ.progress     = ((it.q - 0.3f) / 0.1f).roundToInt().coerceIn(0, 27)
        }
        mid2?.let {
            binding.sbEqMid2Freq.progress = freqToProgress(it.freq, MID2_FREQ_MIN, MID2_FREQ_MAX)
            binding.sbEqMid2Gain.progress = (it.gainDb + 12).toInt().coerceIn(0, 24)
            binding.sbEqMid2Q.progress    = ((it.q - 0.3f) / 0.1f).roundToInt().coerceIn(0, 27)
        }
        high?.let {
            binding.sbEqHighFreq.progress = freqToProgress(it.freq, HIGH_FREQ_MIN, HIGH_FREQ_MAX)
            binding.sbEqHighGain.progress = (it.gainDb + 12).toInt().coerceIn(0, 24)
        }

        val gateEnabled = p.noiseGateThreshold > -59f
        binding.swNoiseGate.isChecked     = gateEnabled
        binding.sbNgThreshold.progress    = (-p.noiseGateThreshold - 21).toInt().coerceIn(0, 39)
        binding.swCompEnabled.isChecked   = p.compressorEnabled
        binding.swStabilization.isChecked = p.stabilization
        when (p.videoResolution) {
            Resolution.R4K_30    -> binding.rgResolution.check(binding.rbRes4k30.id)
            Resolution.R4K_60    -> binding.rgResolution.check(binding.rbRes4k60.id)
            Resolution.R1080P_30 -> binding.rgResolution.check(binding.rbRes1080p30.id)
            Resolution.R1080P_60 -> binding.rgResolution.check(binding.rbRes1080p60.id)
        }
        when (p.targetBitrateKbps) {
            16_000 -> binding.rgBitrate.check(binding.rbBitrate16m.id)
            30_000 -> binding.rgBitrate.check(binding.rbBitrate30m.id)
            50_000 -> binding.rgBitrate.check(binding.rbBitrate50m.id)
            else   -> binding.rgBitrate.check(binding.rbBitrateAuto.id)
        }
        updateAllLabels()
        refreshCurve()
    }

    // ── SeekBar listeners ────────────────────────────────────────────

    private fun setupSeekBars() {
        fun onChange(bar: SeekBar, update: (Int) -> Unit) {
            bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) { update(p); if (fromUser) refreshCurve() }
                override fun onStartTrackingTouch(sb: SeekBar) = Unit
                override fun onStopTrackingTouch(sb: SeekBar) = Unit
            })
        }
        onChange(binding.sbGain)          { v -> binding.tvGainVal.text          = if (v == 0) "0 dB" else "+$v dB" }
        onChange(binding.sbThreshold)     { v -> binding.tvThresholdVal.text     = "${-(v + 1)} dBFS" }
        onChange(binding.sbAttack)        { v -> binding.tvAttackVal.text        = "${v + 1} ms" }
        onChange(binding.sbRelease)       { v -> binding.tvReleaseVal.text       = "${v + 20} ms" }
        onChange(binding.sbCompThreshold) { v -> binding.tvCompThresholdVal.text = "${-(v + 6)} dBFS" }
        onChange(binding.sbCompRatio)     { v -> binding.tvCompRatioVal.text     = "${compRatios[v]}:1" }
        onChange(binding.sbCompMakeup)    { v -> binding.tvCompMakeupVal.text    = if (v == 0) "0 dB" else "+$v dB" }
        onChange(binding.sbCompAttack)    { v -> binding.tvCompAttackVal.text    = "${v + 1} ms" }
        onChange(binding.sbCompRelease)   { v -> binding.tvCompReleaseVal.text   = "${v + 50} ms" }
        onChange(binding.sbHpfFreq)       { v -> binding.tvHpfFreqVal.text       = "${v + 80} Hz" }
        onChange(binding.sbNgThreshold)   { v -> binding.tvNgThresholdVal.text   = "${-(v + 21)} dBFS" }

        onChange(binding.sbEqLowFreq)   { v -> binding.tvEqLowFreq.text   = fmtFreq(progressToFreq(v, LOW_FREQ_MIN,  LOW_FREQ_MAX)) }
        onChange(binding.sbEqLowGain)   { v -> binding.tvEqLowGain.text   = fmtDb(v - 12) }
        onChange(binding.sbEqMidFreq)   { v -> binding.tvEqMidFreq.text   = fmtFreq(progressToFreq(v, MID_FREQ_MIN,  MID_FREQ_MAX)) }
        onChange(binding.sbEqMidGain)   { v -> binding.tvEqMidGain.text   = fmtDb(v - 12) }
        onChange(binding.sbEqMidQ)      { v -> binding.tvEqMidQ.text      = "%.1f".format(0.3f + v * 0.1f) }
        onChange(binding.sbEqMid2Freq)  { v -> binding.tvEqMid2Freq.text  = fmtFreq(progressToFreq(v, MID2_FREQ_MIN, MID2_FREQ_MAX)) }
        onChange(binding.sbEqMid2Gain)  { v -> binding.tvEqMid2Gain.text  = fmtDb(v - 12) }
        onChange(binding.sbEqMid2Q)     { v -> binding.tvEqMid2Q.text     = "%.1f".format(0.3f + v * 0.1f) }
        onChange(binding.sbEqHighFreq)  { v -> binding.tvEqHighFreq.text  = fmtFreq(progressToFreq(v, HIGH_FREQ_MIN, HIGH_FREQ_MAX)) }
        onChange(binding.sbEqHighGain)  { v -> binding.tvEqHighGain.text  = fmtDb(v - 12) }
    }

    private fun updateAllLabels() {
        val g = binding.sbGain.progress
        binding.tvGainVal.text          = if (g == 0) "0 dB" else "+$g dB"
        binding.tvThresholdVal.text     = "${-(binding.sbThreshold.progress + 1)} dBFS"
        binding.tvAttackVal.text        = "${binding.sbAttack.progress + 1} ms"
        binding.tvReleaseVal.text       = "${binding.sbRelease.progress + 20} ms"
        binding.tvCompThresholdVal.text = "${-(binding.sbCompThreshold.progress + 6)} dBFS"
        binding.tvCompRatioVal.text     = "${compRatios[binding.sbCompRatio.progress]}:1"
        val mk = binding.sbCompMakeup.progress
        binding.tvCompMakeupVal.text    = if (mk == 0) "0 dB" else "+$mk dB"
        binding.tvCompAttackVal.text    = "${binding.sbCompAttack.progress + 1} ms"
        binding.tvCompReleaseVal.text   = "${binding.sbCompRelease.progress + 50} ms"
        binding.tvHpfFreqVal.text       = "${binding.sbHpfFreq.progress + 80} Hz"
        binding.tvNgThresholdVal.text   = "${-(binding.sbNgThreshold.progress + 21)} dBFS"

        binding.tvEqLowFreq.text   = fmtFreq(progressToFreq(binding.sbEqLowFreq.progress,  LOW_FREQ_MIN,  LOW_FREQ_MAX))
        binding.tvEqLowGain.text   = fmtDb(binding.sbEqLowGain.progress  - 12)
        binding.tvEqMidFreq.text   = fmtFreq(progressToFreq(binding.sbEqMidFreq.progress,  MID_FREQ_MIN,  MID_FREQ_MAX))
        binding.tvEqMidGain.text   = fmtDb(binding.sbEqMidGain.progress  - 12)
        binding.tvEqMidQ.text      = "%.1f".format(0.3f + binding.sbEqMidQ.progress * 0.1f)
        binding.tvEqMid2Freq.text  = fmtFreq(progressToFreq(binding.sbEqMid2Freq.progress, MID2_FREQ_MIN, MID2_FREQ_MAX))
        binding.tvEqMid2Gain.text  = fmtDb(binding.sbEqMid2Gain.progress - 12)
        binding.tvEqMid2Q.text     = "%.1f".format(0.3f + binding.sbEqMid2Q.progress * 0.1f)
        binding.tvEqHighFreq.text  = fmtFreq(progressToFreq(binding.sbEqHighFreq.progress, HIGH_FREQ_MIN, HIGH_FREQ_MAX))
        binding.tvEqHighGain.text  = fmtDb(binding.sbEqHighGain.progress - 12)
    }

    private fun refreshCurve() {
        binding.eqCurveView.setBands(buildEqBands())
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun progressToFreq(p: Int, min: Float, max: Float) =
        min * (max / min).pow(p / 100f)

    private fun freqToProgress(freq: Float, min: Float, max: Float) =
        (log2(freq / min) / log2(max / min) * 100).roundToInt().coerceIn(0, 100)

    private fun fmtFreq(f: Float) =
        if (f >= 1000f) "${"%.1f".format(f / 1000f)} kHz" else "${f.roundToInt()} Hz"

    private fun fmtDb(v: Int) = if (v >= 0) "+$v dB" else "$v dB"

    private fun buildEqBands() = listOf(
        EqBand(EqFilterType.LOW_SHELF,  progressToFreq(binding.sbEqLowFreq.progress,  LOW_FREQ_MIN,  LOW_FREQ_MAX),  (binding.sbEqLowGain.progress  - 12).toFloat(), 0.707f),
        EqBand(EqFilterType.PEAKING,    progressToFreq(binding.sbEqMidFreq.progress,  MID_FREQ_MIN,  MID_FREQ_MAX),  (binding.sbEqMidGain.progress  - 12).toFloat(), 0.3f + binding.sbEqMidQ.progress * 0.1f),
        EqBand(EqFilterType.PEAKING,    progressToFreq(binding.sbEqMid2Freq.progress, MID2_FREQ_MIN, MID2_FREQ_MAX), (binding.sbEqMid2Gain.progress - 12).toFloat(), 0.3f + binding.sbEqMid2Q.progress * 0.1f),
        EqBand(EqFilterType.HIGH_SHELF, progressToFreq(binding.sbEqHighFreq.progress, HIGH_FREQ_MIN, HIGH_FREQ_MAX), (binding.sbEqHighGain.progress - 12).toFloat(), 0.707f),
    )

    // ── Save ─────────────────────────────────────────────────────────

    private fun savePreset() {
        val name = binding.etPresetName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "Digite um nome para o preset.", Toast.LENGTH_SHORT).show()
            return
        }
        CustomPresetStore.save(requireContext(), buildPreset(name))
        Toast.makeText(requireContext(), "Preset \"$name\" salvo!", Toast.LENGTH_SHORT).show()
        @Suppress("DEPRECATION")
        requireActivity().onBackPressed()
    }

    // ── Export ───────────────────────────────────────────────────────

    private fun exportPreset() {
        val name = binding.etPresetName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "Digite um nome antes de exportar.", Toast.LENGTH_SHORT).show()
            return
        }
        val json = CustomPresetStore.toExportJson(buildPreset(name))
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, json)
            putExtra(Intent.EXTRA_SUBJECT, "Preset ShowRaw: $name")
        }
        startActivity(Intent.createChooser(intent, "Exportar preset"))
    }

    private fun buildPreset(name: String): Preset {
        val emoji      = binding.etPresetEmoji.text.toString().trim().ifEmpty { "🎵" }
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

        return Preset(
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
            eqBands             = buildEqBands(),
            stabilization       = binding.swStabilization.isChecked,
            inputGainDb         = binding.sbGain.progress.toFloat(),
            compressorThreshold = -(binding.sbCompThreshold.progress + 6).toFloat(),
            compressorRatio     = compRatios[binding.sbCompRatio.progress],
            compressorAttack    = (binding.sbCompAttack.progress + 1).toFloat(),
            compressorRelease   = (binding.sbCompRelease.progress + 50).toFloat(),
            compressorMakeupDb  = binding.sbCompMakeup.progress.toFloat(),
            compressorEnabled   = binding.swCompEnabled.isChecked,
            maxDurationMinutes  = 0,
            targetBitrateKbps   = when (binding.rgBitrate.checkedRadioButtonId) {
                binding.rbBitrate16m.id -> 16_000
                binding.rbBitrate30m.id -> 30_000
                binding.rbBitrate50m.id -> 50_000
                else                    -> 0
            },
        )
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
