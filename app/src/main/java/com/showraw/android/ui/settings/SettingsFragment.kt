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

    private var threshold        = -6f
    private var attack           = 5f
    private var release          = 80f
    private var hpfFreq          = 120f
    private var hpfRolloff       = 18
    private var noiseGateEnabled = false
    private var noiseGateThr     = -40f
    private var eqLow            = 0f
    private var eqMid            = 0f
    private var eqHigh           = 0f
    private var stabilization    = false
    private var maxDuration      = 0
    private var resolution       = Resolution.R4K_30

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadFromPrefs()
        initLimiterSliders()
        initHpfSection()
        initNoiseGate()
        initEq()
        initCamera()
        initDuration()
        initResolution()

        binding.btnRecordManual.setOnClickListener {
            saveToPrefs()
            (activity as? Navigator)?.startRecording("manual")
        }
    }

    private fun initLimiterSliders() {
        binding.sbThreshold.progress = (threshold + 20).toInt().coerceIn(0, 19)
        updateThresholdLabel()
        binding.sbThreshold.setOnSeekBarChangeListener(listener { threshold = (it - 20).toFloat(); updateThresholdLabel() })

        binding.sbAttack.progress = (attack.toInt() - 1).coerceIn(0, 49)
        updateAttackLabel()
        binding.sbAttack.setOnSeekBarChangeListener(listener { attack = (it + 1).toFloat(); updateAttackLabel() })

        binding.sbRelease.progress = (release.toInt() - 20).coerceIn(0, 480)
        updateReleaseLabel()
        binding.sbRelease.setOnSeekBarChangeListener(listener { release = (it + 20).toFloat(); updateReleaseLabel() })
    }

    private fun initHpfSection() {
        binding.sbHpfFreq.progress = (hpfFreq.toInt() - 80).coerceIn(0, 170)
        updateHpfFreqLabel()
        binding.sbHpfFreq.setOnSeekBarChangeListener(listener { hpfFreq = (it + 80).toFloat(); updateHpfFreqLabel() })

        when (hpfRolloff) {
            12   -> binding.rbRolloff12.isChecked = true
            24   -> binding.rbRolloff24.isChecked = true
            else -> binding.rbRolloff18.isChecked = true
        }
        binding.rgRolloff.setOnCheckedChangeListener { _, id ->
            hpfRolloff = when (id) { binding.rbRolloff12.id -> 12; binding.rbRolloff24.id -> 24; else -> 18 }
        }
    }

    private fun initNoiseGate() {
        binding.swNoiseGate.isChecked = noiseGateEnabled
        binding.sbNgThreshold.progress = (-noiseGateThr.toInt() - 20).coerceIn(0, 39)
        updateNgLabel()
        binding.swNoiseGate.setOnCheckedChangeListener { _, c -> noiseGateEnabled = c }
        binding.sbNgThreshold.setOnSeekBarChangeListener(listener { noiseGateThr = -(it + 20).toFloat(); updateNgLabel() })
    }

    private fun initEq() {
        binding.sbEqLow.progress  = (eqLow  + 12).toInt().coerceIn(0, 24)
        binding.sbEqMid.progress  = (eqMid  + 12).toInt().coerceIn(0, 24)
        binding.sbEqHigh.progress = (eqHigh + 12).toInt().coerceIn(0, 24)
        updateEqLabels()
        binding.sbEqLow.setOnSeekBarChangeListener (listener { eqLow  = (it - 12).toFloat(); updateEqLabels() })
        binding.sbEqMid.setOnSeekBarChangeListener (listener { eqMid  = (it - 12).toFloat(); updateEqLabels() })
        binding.sbEqHigh.setOnSeekBarChangeListener(listener { eqHigh = (it - 12).toFloat(); updateEqLabels() })
    }

    private fun initCamera() {
        binding.swStabilization.isChecked = stabilization
        binding.swStabilization.setOnCheckedChangeListener { _, c -> stabilization = c }
    }

    private fun initDuration() {
        when (maxDuration) {
            30   -> binding.rbDur30.isChecked        = true
            60   -> binding.rbDur60.isChecked        = true
            90   -> binding.rbDur90.isChecked        = true
            else -> binding.rbDurUnlimited.isChecked = true
        }
        binding.rgMaxDuration.setOnCheckedChangeListener { _, id ->
            maxDuration = when (id) {
                binding.rbDur30.id -> 30; binding.rbDur60.id -> 60; binding.rbDur90.id -> 90; else -> 0
            }
        }
    }

    private fun initResolution() {
        when (resolution) {
            Resolution.R4K_60    -> binding.rbRes4k60.isChecked    = true
            Resolution.R1080P_30 -> binding.rbRes1080p30.isChecked = true
            Resolution.R1080P_60 -> binding.rbRes1080p60.isChecked = true
            else                 -> binding.rbRes4k30.isChecked    = true
        }
        binding.rgResolution.setOnCheckedChangeListener { _, id ->
            resolution = when (id) {
                binding.rbRes4k60.id    -> Resolution.R4K_60
                binding.rbRes1080p30.id -> Resolution.R1080P_30
                binding.rbRes1080p60.id -> Resolution.R1080P_60
                else                    -> Resolution.R4K_30
            }
        }
    }

    private fun updateThresholdLabel() { binding.tvThresholdVal.text   = "%+.0f dBFS".format(threshold) }
    private fun updateAttackLabel()    { binding.tvAttackVal.text       = "%.0f ms".format(attack) }
    private fun updateReleaseLabel()   { binding.tvReleaseVal.text      = "%.0f ms".format(release) }
    private fun updateHpfFreqLabel()   { binding.tvHpfFreqVal.text      = "%.0f Hz".format(hpfFreq) }
    private fun updateNgLabel()        { binding.tvNgThresholdVal.text  = "%.0f dBFS".format(noiseGateThr) }
    private fun updateEqLabels() {
        binding.tvEqLowVal.text  = eqLabel(eqLow)
        binding.tvEqMidVal.text  = eqLabel(eqMid)
        binding.tvEqHighVal.text = eqLabel(eqHigh)
    }
    private fun eqLabel(db: Float) = if (db == 0f) "0 dB" else "%+.0f dB".format(db)

    private fun loadFromPrefs() {
        val p = prefs()
        threshold        = p.getFloat  (KEY_THRESHOLD,    -6f)
        attack           = p.getFloat  (KEY_ATTACK,        5f)
        release          = p.getFloat  (KEY_RELEASE,      80f)
        hpfFreq          = p.getFloat  (KEY_HPF_FREQ,    120f)
        hpfRolloff       = p.getInt    (KEY_HPF_ROLLOFF,   18)
        noiseGateEnabled = p.getBoolean(KEY_NG_ENABLED, false)
        noiseGateThr     = p.getFloat  (KEY_NG_THR,      -40f)
        eqLow            = p.getFloat  (KEY_EQ_LOW,        0f)
        eqMid            = p.getFloat  (KEY_EQ_MID,        0f)
        eqHigh           = p.getFloat  (KEY_EQ_HIGH,       0f)
        stabilization    = p.getBoolean(KEY_STAB,       false)
        maxDuration      = p.getInt    (KEY_MAX_DUR,        0)
        resolution       = Resolution.valueOf(p.getString(KEY_RESOLUTION, Resolution.R4K_30.name)!!)
    }

    private fun saveToPrefs() {
        prefs().edit()
            .putFloat  (KEY_THRESHOLD,   threshold)
            .putFloat  (KEY_ATTACK,      attack)
            .putFloat  (KEY_RELEASE,     release)
            .putFloat  (KEY_HPF_FREQ,    hpfFreq)
            .putInt    (KEY_HPF_ROLLOFF, hpfRolloff)
            .putBoolean(KEY_NG_ENABLED,  noiseGateEnabled)
            .putFloat  (KEY_NG_THR,      noiseGateThr)
            .putFloat  (KEY_EQ_LOW,      eqLow)
            .putFloat  (KEY_EQ_MID,      eqMid)
            .putFloat  (KEY_EQ_HIGH,     eqHigh)
            .putBoolean(KEY_STAB,        stabilization)
            .putInt    (KEY_MAX_DUR,     maxDuration)
            .putString (KEY_RESOLUTION,  resolution.name)
            .apply()
    }

    private fun prefs() = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun listener(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) = onChange(p)
        override fun onStartTrackingTouch(sb: SeekBar) = Unit
        override fun onStopTrackingTouch(sb: SeekBar)  = Unit
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    companion object {
        private const val PREFS_NAME      = "manual_preset"
        private const val KEY_THRESHOLD   = "threshold"
        private const val KEY_ATTACK      = "attack"
        private const val KEY_RELEASE     = "release"
        private const val KEY_HPF_FREQ    = "hpf_freq"
        private const val KEY_HPF_ROLLOFF = "hpf_rolloff"
        private const val KEY_NG_ENABLED  = "ng_enabled"
        private const val KEY_NG_THR      = "ng_thr"
        private const val KEY_EQ_LOW      = "eq_low"
        private const val KEY_EQ_MID      = "eq_mid"
        private const val KEY_EQ_HIGH     = "eq_high"
        private const val KEY_STAB        = "stabilization"
        private const val KEY_MAX_DUR     = "max_duration"
        private const val KEY_RESOLUTION  = "resolution"

        private val MB_PER_MIN = mapOf(
            Resolution.R4K_30    to 350,
            Resolution.R4K_60    to 600,
            Resolution.R1080P_30 to 130,
            Resolution.R1080P_60 to 200,
        )

        fun loadManualPreset(context: Context): Preset {
            val p   = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val res = Resolution.valueOf(p.getString(KEY_RESOLUTION, Resolution.R4K_30.name)!!)
            return Preset(
                id                 = "manual",
                name               = "Manual (Pro)",
                emoji              = "🎛️",
                description        = "Configuração personalizada.",
                limiterThreshold   = p.getFloat  (KEY_THRESHOLD,    -6f),
                limiterAttack      = p.getFloat  (KEY_ATTACK,         5f),
                limiterRelease     = p.getFloat  (KEY_RELEASE,       80f),
                hpfFrequency       = p.getFloat  (KEY_HPF_FREQ,     120f),
                hpfRolloff         = p.getInt    (KEY_HPF_ROLLOFF,    18),
                videoResolution    = res,
                estimatedMbPerMin  = MB_PER_MIN[res] ?: 350,
                contextualWarning  = null,
                noiseGateThreshold = if (p.getBoolean(KEY_NG_ENABLED, false)) p.getFloat(KEY_NG_THR, -40f) else -60f,
                eqLowGainDb        = p.getFloat  (KEY_EQ_LOW,         0f),
                eqMidGainDb        = p.getFloat  (KEY_EQ_MID,         0f),
                eqHighGainDb       = p.getFloat  (KEY_EQ_HIGH,        0f),
                stabilization      = p.getBoolean(KEY_STAB,        false),
                maxDurationMinutes = p.getInt    (KEY_MAX_DUR,          0),
            )
        }
    }
}
