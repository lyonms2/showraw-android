package com.showraw.android.ui.recording

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.showraw.android.audio.AudioEngine
import com.showraw.android.audio.AudioStats
import com.showraw.android.presets.PresetRepository
import com.showraw.android.video.VideoCaptureManager

/**
 * Tela 2 — viewfinder da câmera + VU meter + stats em tempo real.
 * Exibe: Limiter GR, Mic blend, Pico, Release, badge RAW, tempo gravado,
 * tempo restante de armazenamento. Botões: Pausar / Parar.
 */
class RecordingFragment : Fragment() {

    private val audioEngine = AudioEngine()
    private lateinit var videoManager: VideoCaptureManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val presetId = arguments?.getString(ARG_PRESET_ID) ?: "show"
        val preset   = PresetRepository.findById(presetId) ?: PresetRepository.all.first()
        audioEngine.configure(preset)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View? {
        // TODO: inflar R.layout.fragment_recording com PreviewView + VU meter custom view
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        audioEngine.onStats    = ::updateStats
        audioEngine.onSplWarning = ::showSplWarning
        // TODO: iniciar câmera e áudio juntos
        // TODO: VU meter: verde ≤ -18 dBFS | amarelo -18 a -6 | vermelho > -6
    }

    private fun updateStats(stats: AudioStats) {
        // TODO: atualizar UI com stats.rms, peakDbFs, gainReductionDb
    }

    private fun showSplWarning() {
        // TODO: exibir overlay vermelho com opções (continuar / mic externo / parar)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        audioEngine.stop()
    }

    companion object {
        const val ARG_PRESET_ID = "preset_id"
        fun newInstance(presetId: String) = RecordingFragment().apply {
            arguments = Bundle().apply { putString(ARG_PRESET_ID, presetId) }
        }
    }
}
