package com.showraw.android.ui.presets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.showraw.android.presets.Preset
import com.showraw.android.presets.PresetRepository

/**
 * Tela 1 — grid 2 colunas com 8 presets.
 * Mostra resumo do preset selecionado, estimativa de armazenamento e botão "Gravar".
 */
class PresetsFragment : Fragment() {

    private var selectedPreset: Preset = PresetRepository.all.first()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View? {
        // TODO: inflar layout R.layout.fragment_presets
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // TODO: montar RecyclerView com PresetRepository.all
        // TODO: ao clicar num card, atualizar selectedPreset e painel de resumo
        // TODO: botão "Gravar" → navegar para RecordingFragment passando preset id
        // TODO: exibir estimativa de armazenamento em minutos (não em GB)
    }
}
