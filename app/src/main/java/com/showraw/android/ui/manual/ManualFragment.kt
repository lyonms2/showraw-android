package com.showraw.android.ui.manual

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment

class ManualFragment : Fragment() {

    private val primary = Color.parseColor("#EF9F27")
    private val bgMain  = Color.parseColor("#0D0D0D")
    private val bgCard  = Color.parseColor("#1A1A1A")
    private val bgBody  = Color.parseColor("#111111")
    private val textSec = Color.parseColor("#999999")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        val scroll = NestedScrollView(requireContext()).apply {
            setBackgroundColor(bgMain)
        }

        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(40))
        }
        scroll.addView(root)

        root.addView(TextView(requireContext()).apply {
            text = "Manual"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(primary)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        })

        root.addView(TextView(requireContext()).apply {
            text = "Toque em um tópico para expandir"
            textSize = 12f
            setTextColor(textSec)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(4); bottomMargin = dp(8) }
        })

        addSection(root, "🎚 Presets",
            """Cada preset é um perfil de gravação otimizado para um cenário específico.

• Show / Palco — limiter agressivo, HPF alto para cortar ruído de palco.
• Entrevista — equilíbrio de voz, noise gate suave, HPF em 80 Hz.
• Outdoor — HPF alto para vento, limiter rápido.
• Vlog — som natural com correção suave.
• Reunião — noise gate forte, ganho conservador.
• Modo Pro — você configura cada parâmetro manualmente em Ajustes Avançados.

O nome e o emoji do preset ativo aparecem no canto inferior direito da tela de gravação.""")

        addSection(root, "🔒 Limiter",
            """O Limiter é uma proteção automática que impede o clipping (distorção por saturação).

Como funciona:
  Threshold: nível máximo permitido (ex.: −1 dBFS). Qualquer sinal acima é reduzido.
  Attack: velocidade com que o limiter entra em ação (valores baixos = reação mais rápida).
  Release: velocidade com que o limiter libera o ganho após o pico cair.

O indicador LIMITER GR na tela mostra em tempo real quantos dB de ganho estão sendo reduzidos.
Se GR estiver sempre acima de 6 dB, o volume da fonte está muito alto — afaste o microfone ou reduza o volume da fonte.""")

        addSection(root, "〰 HPF — Filtro Passa-Alta",
            """O HPF (High Pass Filter) corta frequências abaixo de um limiar, eliminando:
• Ruído de manejo (dedos no microfone)
• Ronco de ar condicionado e ventiladores
• Vento em gravações externas

Frequência de corte típica:
  80 Hz — gravações musicais (preserva baixo e bumbo)
  120 Hz — voz falada (entrevistas, vlogs)
  180 Hz — ambientes ruidosos / vento moderado
  250 Hz — vento forte (corte mais agressivo)

Rolloff 12 dB/oitava é suave e musical; 24 dB/oitava é mais cirúrgico.""")

        addSection(root, "🎛 EQ — Equalizador",
            """O EQ de 3 bandas permite ajustar o equilíbrio tonal do áudio capturado.

Bandas:
  Graves (Low) — frequências abaixo de ~250 Hz. Aumente para mais warmth, reduza para limpar.
  Médios (Mid) — voz e presença (~1-4 kHz). Aumente para clareza, reduza para menos nasalidade.
  Agudos (High) — brilho e ar (~8 kHz+). Aumente para mais definição, reduza para menos sibilância.

Dica: comece com todos em 0 dB e ajuste aos poucos. Cortar é geralmente mais eficaz que aumentar.""")

        addSection(root, "🚪 Noise Gate",
            """O Noise Gate silencia automaticamente o sinal quando ele cai abaixo de um limiar.

Uso ideal:
  Em ambientes com ruído de fundo constante (ar condicionado, ventilador, rua).
  Para separar falas em entrevistas.

Threshold: quanto mais alto, mais agressivo — sons fracos são cortados.
Cuidado: valores muito altos cortam o início de palavras (fenômeno chamado "clipping de gate").""")

        addSection(root, "📷 Câmera",
            """Controles de câmera disponíveis na tela de gravação:

EIS (Estabilização) — Ativa estabilização eletrônica. Recomendado para gravações em movimento.
  Nota: não pode ser alterado durante a gravação.

⇄ Trocar câmera — Alterna entre câmera traseira e frontal.
  Nota: não pode ser alterado durante a gravação.

Zoom — Pinça dois dedos na pré-visualização para ampliar. O nível aparece no topo.

Exposição — Arraste o controle deslizante para ajustar o brilho da imagem em tempo real.

Foco — Toque em qualquer ponto da tela para focar naquele ponto.""")

        addSection(root, "📤 Exportação",
            """Após parar a gravação, o app processa e combina vídeo + áudio:

1. O áudio bruto (WAV) é codificado em AAC 256 kbps.
2. O AAC é combinado com o vídeo num único MP4 final.
3. Um stem de áudio separado (.m4a) é salvo ao lado do vídeo.

Compartilhamento:
  ↑ Vídeo MP4 — compartilha o arquivo final com qualquer app.
  ↑ Áudio M4A — compartilha apenas o stem de áudio (para DAWs).
  ▶ YouTube — abre direto no app do YouTube para upload.

Localização GPS é incorporada nos metadados do MP4 se a permissão for concedida.""")

        addSection(root, "⚙ Modo Pro",
            """O Modo Pro dá controle total sobre todos os parâmetros de DSP.

Acesso: na tela inicial, selecione o preset "Pro" e toque em Gravar — você será redirecionado para Ajustes Avançados onde pode configurar:

• Frequência e rolloff do HPF
• Threshold, attack e release do Limiter
• Ganhos de graves, médios e agudos do EQ
• Threshold do Noise Gate
• Resolução de vídeo e estabilização

As configurações Pro são salvas automaticamente e aplicadas na próxima gravação.""")

        return scroll
    }

    private fun addSection(parent: LinearLayout, title: String, content: String) {
        val header = TextView(requireContext()).apply {
            text = "▶  $title"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setBackgroundColor(bgCard)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) }
        }

        val body = TextView(requireContext()).apply {
            text = content
            textSize = 13f
            setTextColor(Color.parseColor("#CCCCCC"))
            setBackgroundColor(bgBody)
            setPadding(dp(14), dp(10), dp(14), dp(14))
            setLineSpacing(0f, 1.4f)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

        header.setOnClickListener {
            val expanding = body.visibility != View.VISIBLE
            body.visibility = if (expanding) View.VISIBLE else View.GONE
            header.text = "${if (expanding) "▼" else "▶"}  $title"
        }

        parent.addView(header)
        parent.addView(body)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
