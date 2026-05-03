package com.showraw.android.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import com.showraw.android.Navigator
import com.showraw.android.databinding.FragmentSettingsBinding
import com.showraw.android.presets.Preset
import com.showraw.android.presets.Resolution

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    // Valores em memória — atualizados pelos sliders em tempo real
    private var threshold = -6f    // dBFS  (-20 a 0)
    private var attack    = 5f     // ms    (1-50)
    private var release   = 80f    // ms    (20-500)
    private var hpfFreq   = 120f   // Hz    (80-250)
    private var hpfRolloff = 18    // 12, 18 ou 24 dB/oct
    private var resolution = Resolution.R4K_30

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadFromPrefs()
        initSliders()
        initRolloffGroup()
        initResolutionGroup()

        binding.btnRecordManual.setOnClickListener {
            saveToPrefs()
            (activity as? Navigator)?.startRecording("manual")
        }
    }

    private fun initSliders() {
        // Threshold: progress 0-19 → valor = -(20 - progress) = progress - 20
        binding.sbThreshold.progress = (threshold + 20).toInt().coerceIn(0, 19)
        updateThresholdLabel()
        binding.sbThreshold.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                threshold = (p - 20).toFloat()
                updateThresholdLabel()
            }
            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar) = Unit
        })

        // Attack: progress 0-49 → valor = progress + 1 ms
        binding.sbAttack.progress = (attack.toInt() - 1).coerceIn(0, 49)
        updateAttackLabel()
        binding.sbAttack.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                attack = (p + 1).toFloat()
                updateAttackLabel()
            }
            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar) = Unit
        })

        // Release: progress 0-480 → valor = progress + 20 ms
        binding.sbRelease.progress = (release.toInt() - 20).coerceIn(0, 480)
        updateReleaseLabel()
        binding.sbRelease.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                release = (p + 20).toFloat()
                updateReleaseLabel()
            }
            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar) = Unit
        })

        // HPF Freq: progress 0-170 → valor = progress + 80 Hz
        binding.sbHpfFreq.progress = (hpfFreq.toInt() - 80).coerceIn(0, 170)
        updateHpfFreqLabel()
        binding.sbHpfFreq.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                hpfFreq = (p + 80).toFloat()
                updateHpfFreqLabel()
            }
            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar) = Unit
        })
    }

    private fun initRolloffGroup() {
        when (hpfRolloff) {
            12   -> binding.rbRolloff12.isChecked = true
            24   -> binding.rbRolloff24.isChecked = true
            else -> binding.rbRolloff18.isChecked = true
        }
        binding.rgRolloff.setOnCheckedChangeListener { _, checkedId ->
            hpfRolloff = when (checkedId) {
                binding.rbRolloff12.id -> 12
                binding.rbRolloff24.id -> 24
                else                   -> 18
            }
        }
    }

    private fun initResolutionGroup() {
        when (resolution) {
            Resolution.R4K_60    -> binding.rbRes4k60.isChecked    = true
            Resolution.R1080P_30 -> binding.rbRes1080p30.isChecked = true
            Resolution.R1080P_60 -> binding.rbRes1080p60.isChecked = true
            else                 -> binding.rbRes4k30.isChecked    = true
        }
        binding.rgResolution.setOnCheckedChangeListener { _, checkedId ->
            resolution = when (checkedId) {
                binding.rbRes4k60.id    -> Resolution.R4K_60
                binding.rbRes1080p30.id -> Resolution.R1080P_30
                binding.rbRes1080p60.id -> Resolution.R1080P_60
                else                    -> Resolution.R4K_30
            }
        }
    }

    // ── Labels ──────────────────────────────────────────────────────

    private fun updateThresholdLabel() {
        binding.tvThresholdVal.text = "%+.0f dBFS".format(threshold)
    }

    private fun updateAttackLabel() {
        binding.tvAttackVal.text = "%.0f ms".format(attack)
    }

    private fun updateReleaseLabel() {
        binding.tvReleaseVal.text = "%.0f ms".format(release)
    }

    private fun updateHpfFreqLabel() {
        binding.tvHpfFreqVal.text = "%.0f Hz".format(hpfFreq)
    }

    // ── SharedPreferences ───────────────────────────────────────────

    private fun loadFromPrefs() {
        val p = prefs()
        threshold  = p.getFloat(KEY_THRESHOLD, -6f)
        attack     = p.getFloat(KEY_ATTACK,     5f)
        release    = p.getFloat(KEY_RELEASE,   80f)
        hpfFreq    = p.getFloat(KEY_HPF_FREQ, 120f)
        hpfRolloff = p.getInt(KEY_HPF_ROLLOFF,  18)
        resolution = Resolution.valueOf(p.getString(KEY_RESOLUTION, Resolution.R4K_30.name)!!)
    }

    private fun saveToPrefs() {
        prefs().edit()
            .putFloat(KEY_THRESHOLD,  threshold)
            .putFloat(KEY_ATTACK,     attack)
            .putFloat(KEY_RELEASE,    release)
            .putFloat(KEY_HPF_FREQ,   hpfFreq)
            .putInt(KEY_HPF_ROLLOFF,  hpfRolloff)
            .putString(KEY_RESOLUTION, resolution.name)
            .apply()
    }

    private fun prefs() =
        requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val PREFS_NAME    = "manual_preset"
        private const val KEY_THRESHOLD  = "threshold"
        private const val KEY_ATTACK     = "attack"
        private const val KEY_RELEASE    = "release"
        private const val KEY_HPF_FREQ   = "hpf_freq"
        private const val KEY_HPF_ROLLOFF = "hpf_rolloff"
        private const val KEY_RESOLUTION  = "resolution"

        private val MB_PER_MIN = mapOf(
            Resolution.R4K_30    to 350,
            Resolution.R4K_60    to 600,
            Resolution.R1080P_30 to 130,
            Resolution.R1080P_60 to 200,
        )

        /** Lido pelo RecordingFragment antes de iniciar gravação manual. */
        fun loadManualPreset(context: Context): Preset {
            val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val res = Resolution.valueOf(p.getString(KEY_RESOLUTION, Resolution.R4K_30.name)!!)
            return Preset(
                id                = "manual",
                name              = "Manual (Pro)",
                emoji             = "🎛️",
                description       = "Configuração personalizada.",
                limiterThreshold  = p.getFloat(KEY_THRESHOLD,  -6f),
                limiterAttack     = p.getFloat(KEY_ATTACK,       5f),
                limiterRelease    = p.getFloat(KEY_RELEASE,     80f),
                hpfFrequency      = p.getFloat(KEY_HPF_FREQ,  120f),
                hpfRolloff        = p.getInt(KEY_HPF_ROLLOFF,   18),
                videoResolution   = res,
                estimatedMbPerMin = MB_PER_MIN[res] ?: 350,
                contextualWarning = null,
            )
        }
    }
}
