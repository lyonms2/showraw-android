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
• Presets Personalizados — crie seus próprios perfis de DSP (veja Modo Pro).

O nome e o emoji do preset ativo aparecem no canto inferior direito da tela de gravação.""")

        addSection(root, "🎙 Microfone Externo",
            """O ShowRaw captura áudio em modo UNPROCESSED — sem processamento automático do Android — para máxima qualidade. Isso também significa que o Controle de Ganho Automático (AGC) do sistema está desativado.

LIMITAÇÕES POR TIPO DE DISPOSITIVO
──────────────────────────────────
▸ Celular sem microfone externo
  O microfone embutido está posicionado para capturar voz próxima, não fontes distantes. Em shows e ambientes com volume alto o sinal pode saturar ou soar com artefatos mesmo com o melhor preset.

▸ Microfone de lapela (P2 / TRRS)
  Melhora muito a captação de voz em entrevistas e vlogs. Resultado depende da qualidade do microfone e do conector TRRS do aparelho.

▸ Microfone externo via adaptador USB-C
  Solução recomendada para gravações profissionais. Permite posicionar o microfone próximo à fonte sonora, eliminando boa parte dos problemas de nível e ruído de fundo.

▸ Interface de áudio USB-C
  Nível profissional. Permite ligar qualquer microfone XLR com phantom power e controle de ganho analógico preciso.

ATENÇÃO: Nenhum processamento de software substitui um bom posicionamento de microfone. O app faz o melhor possível com o hardware disponível, mas o resultado final é limitado pelo microfone utilizado.""")

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

Rolloff 12 dB/oitava é suave e musical; 24 dB/oitava é mais cirúrgico.

──────────────────────────────────────────
RUÍDO DE MANEJO (chiado da mão no celular)
──────────────────────────────────────────
Segurar o celular transmite vibração diretamente ao microfone. O resultado são dois tipos de ruído:

▸ Rumble de baixa frequência (abaixo de 80 Hz)
  Causado pela pressão dos dedos e microvibrações do aparelho.
  O HPF a 80 Hz elimina praticamente todo esse componente sem afetar a gravação — o bumbo e o contrabaixo ficam intactos.

▸ Chiado de alta frequência (1–8 kHz)
  Causado pelo atrito dos dedos deslizando na superfície do aparelho.
  Não existe filtro eficaz para esse componente — ele ocorre na mesma faixa dos instrumentos e da voz.

Como melhorar:
  • Segure firme e evite reposicionar a mão durante a gravação
  • Use um tripé ou suporte — elimina 100% do ruído de manejo
  • Use uma gaiola/rig de celular — isola o aparelho da mão
  • Use microfone externo — sai completamente da equação""")


        addSection(root, "🎛 EQ — Equalizador",
            """O EQ de 3 bandas permite ajustar o equilíbrio tonal do áudio capturado.

Bandas:
  Graves (Low) — frequências abaixo de ~250 Hz. Aumente para mais warmth, reduza para limpar.
  Médios (Mid) — voz e presença (~1-4 kHz). Aumente para clareza, reduza para menos nasalidade.
  Agudos (High) — brilho e ar (~8 kHz+). Aumente para mais definição, reduza para menos sibilância.

Dica: comece com todos em 0 dB e ajuste aos poucos. Cortar é geralmente mais eficaz que aumentar.""")

        addSection(root, "🎚 Compressor",
            """O Compressor reduz dinamicamente o volume dos sons mais altos, nivelando a gravação.

Como funciona:
  Threshold: nível a partir do qual o compressor começa a agir (ex.: −18 dBFS).
  Ratio: quanto o sinal é comprimido. Ex.: 3:1 = cada 3 dB acima do threshold vira 1 dB.
  Makeup Gain: compensação de nível aplicada após a compressão para restaurar o volume.
  Attack: tempo para o compressor reagir a um pico (mais curto = mais agressivo).
  Release: tempo para soltar após o pico cair.

O compressor é aplicado depois do EQ e antes do Limiter na cadeia de DSP:
  Ganho → HPF → EQ → Compressor → Limiter

Por que isso importa:
  Em shows com bateria e palco alto, o compressor controla os picos intensos e sobe o volume médio. O Limiter é o último recurso contra clipping — idealmente deve acionar pouco.

Dica: se o indicador GR (Gain Reduction) do Limiter estiver sempre ativo, diminua o Makeup Gain do compressor.""")

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

Foco — Toque em qualquer ponto da tela para focar naquele ponto.

🎧 Monitoramento de áudio — Aparece durante a gravação. Reproduz o áudio capturado pelo fone de ouvido em tempo real.
  Atenção: existe uma latência de 50–200 ms dependendo do dispositivo. Use apenas para verificar nível, não para performance ao vivo.""")

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
            """O Modo Pro oferece duas formas de controle avançado:

── AJUSTES PRO (preset Manual) ────────────────────
Acesso: selecione o preset "Manual" e toque em Gravar. Você será redirecionado para Ajustes Avançados onde configura sessão a sessão:
• Frequência e rolloff do HPF
• Threshold, attack e release do Limiter
• Ganhos de graves, médios e agudos do EQ
• Threshold do Noise Gate
• Resolução de vídeo e estabilização

── PRESETS PERSONALIZADOS ──────────────────────────
Para usuários que usam sempre as mesmas configurações, crie presets reutilizáveis com nome e emoji próprios.

Como criar: na tela de seleção de presets, role até o final e toque no cartão "+ Novo Preset". Configure todos os parâmetros de DSP e câmera e toque em "Salvar Preset".

O preset aparece na grade ao lado dos presets padrão. Para editar ou excluir, pressione e segure o cartão.

Exemplo de uso: um músico de estúdio pode criar "🎸 Ensaio" com compressão moderada e noise gate desativado, e "🎤 Palco" com ganho máximo e HPF agressivo.""")

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
