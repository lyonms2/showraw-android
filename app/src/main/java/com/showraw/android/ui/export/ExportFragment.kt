package com.showraw.android.ui.export

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.showraw.android.video.ExportMode

/**
 * Tela 3 — preview do vídeo gravado.
 * Opções: Vídeo final (MP4) / Stems WAV / Ambos.
 * Formulário YouTube: título auto (data + GPS), privacidade, descrição.
 */
class ExportFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View? {
        // TODO: inflar R.layout.fragment_export
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // TODO: preview com ExoPlayer ou MediaPlayer
        // TODO: seletor de ExportMode (radio buttons)
        // TODO: painel YouTube expandível com toggle de privacidade
        // TODO: botão "Exportar" → chamar VideoExporter.export()
        // TODO: botão "Publicar no YouTube" → chamar YouTubeUploader.upload()
    }
}
