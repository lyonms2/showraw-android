package com.showraw.android.ui.manual

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

/**
 * Tela 5 — Manual do usuário com seções expansíveis (accordion).
 * Seções: Presets · Modo RAW · Limiter · HPF · Mic Blending · Mic Externo · Exportação.
 */
class ManualFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View? {
        // TODO: inflar R.layout.fragment_manual com ExpandableListView ou RecyclerView
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // TODO: popular com conteúdo educativo sobre cada feature de áudio
    }
}
