package com.showraw.android.ui.phase1

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.showraw.android.audio.AudioEngine
import com.showraw.android.audio.AudioStats
import com.showraw.android.audio.HardwareValidationResult
import com.showraw.android.audio.HardwareValidator
import com.showraw.android.audio.WavWriter
import com.showraw.android.databinding.ActivityPhase1Binding
import com.showraw.android.presets.PresetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Phase1Activity : AppCompatActivity() {

    private lateinit var binding: ActivityPhase1Binding
    private val scope       = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val audioEngine = AudioEngine()
    private var wavWriter: WavWriter? = null
    private var isRecording = false

    private val preset = PresetRepository.findById("show")!!

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "Permissão de microfone necessária.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhase1Binding.inflate(layoutInflater)
        setContentView(binding.root)

        requestMicPermission()

        binding.btnValidate.setOnClickListener { runValidation() }
        binding.btnRecord.setOnClickListener   { toggleRecording() }

        audioEngine.onStats      = { stats -> runOnUiThread { updateStats(stats) } }
        audioEngine.onSplWarning = { runOnUiThread { binding.tvSplWarning.visibility = View.VISIBLE } }
    }

    // ── Validação ──────────────────────────────────────────────────

    private fun runValidation() {
        binding.btnValidate.isEnabled = false
        binding.tvHwResult.text = "Validando… (1-2 segundos)"

        scope.launch {
            val result = withContext(Dispatchers.IO) { HardwareValidator.validate() }
            binding.tvHwResult.text = when (result) {
                HardwareValidationResult.RAW_CONFIRMED     -> "✅ Modo RAW confirmado neste aparelho"
                HardwareValidationResult.RAW_UNCONFIRMED   -> "⚠️ Modo RAW não confirmado. O áudio pode conter processamento nativo. Recomendamos microfone externo."
                HardwareValidationResult.AMBIENT_TOO_QUIET -> "🔇 Ambiente silencioso demais. Reproduza música ou fale perto do microfone e tente novamente."
                HardwareValidationResult.PERMISSION_DENIED -> "❌ Permissão de microfone negada."
                HardwareValidationResult.ERROR             -> "❌ Erro na validação de hardware."
            }
            binding.btnValidate.isEnabled = true
        }
    }

    // ── Gravação ───────────────────────────────────────────────────

    private fun toggleRecording() {
        if (!hasMicPermission()) {
            requestMicPermission()
            return
        }
        if (isRecording) stopRecording() else startRecording()
    }

    private fun startRecording() {
        val ts   = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(getExternalFilesDir(null), "showraw_$ts.wav")
        wavWriter = WavWriter(file)

        audioEngine.configure(preset)
        audioEngine.onBufferReady = { buf, size -> wavWriter?.write(buf, size) }
        audioEngine.start()

        isRecording = true
        binding.btnRecord.text = "■ Parar e Exportar WAV"
        binding.tvFile.visibility     = View.GONE
        binding.tvSplWarning.visibility = View.GONE
    }

    private fun stopRecording() {
        audioEngine.stop()
        val file = wavWriter?.finish()
        wavWriter = null
        isRecording = false

        binding.btnRecord.text = "● Iniciar Gravação"
        binding.tvPeak.text  = "— dBFS"
        binding.tvGr.text    = "0.0 dB"
        binding.vuBar.progress = 0

        if (file != null) {
            val mb = "%.1f".format(file.length() / 1_048_576f)
            binding.tvFile.text      = "✅ WAV exportado (${mb} MB)\n${file.absolutePath}"
            binding.tvFile.visibility = View.VISIBLE
        }
    }

    // ── Stats em tempo real ────────────────────────────────────────

    private fun updateStats(stats: AudioStats) {
        val peakDb = stats.peakDbFs.coerceIn(-96f, 0f)

        binding.tvPeak.text = "${"%.1f".format(peakDb)} dBFS"
        binding.tvGr.text   = "${"%.1f".format(stats.gainReductionDb)} dB"

        // ProgressBar: 0 = silêncio total, 96 = 0 dBFS
        val progress = (96 + peakDb).toInt().coerceIn(0, 96)
        binding.vuBar.progress = progress
        binding.vuBar.progressTintList = android.content.res.ColorStateList.valueOf(
            when {
                peakDb > -6f  -> getColor(com.showraw.android.R.color.vu_red)
                peakDb > -18f -> getColor(com.showraw.android.R.color.vu_yellow)
                else          -> getColor(com.showraw.android.R.color.vu_green)
            }
        )
    }

    // ── Permissão ──────────────────────────────────────────────────

    private fun hasMicPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestMicPermission() {
        if (!hasMicPermission()) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    override fun onDestroy() {
        super.onDestroy()
        audioEngine.stop()
        scope.cancel()
    }
}
