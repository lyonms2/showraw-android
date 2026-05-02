package com.showraw.android.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

/**
 * Tela 4 — Configurações Pro (feature gate em fase de monetização).
 * Sliders: threshold / attack / release / HPF freq.
 * Seletor rolloff: 12 / 18 / 24 dB/oct.
 * Toggle stems WAV. Seletor codec H.264 / H.265.
 */
class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View? {
        // TODO: inflar R.layout.fragment_settings
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // TODO: sliders Threshold (-20 a 0 dBFS), Attack (1-50ms), Release (20-500ms)
        // TODO: slider HPF (80-250Hz) com chips de rolloff (12/18/24)
        // TODO: persistir preset "manual" em SharedPreferences
    }
}
