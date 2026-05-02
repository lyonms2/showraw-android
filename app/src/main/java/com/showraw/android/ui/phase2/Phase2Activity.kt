package com.showraw.android.ui.phase2

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.showraw.android.R
import com.showraw.android.audio.AudioEngine
import com.showraw.android.audio.AudioStats
import com.showraw.android.audio.WavWriter
import com.showraw.android.databinding.ActivityPhase2Binding
import com.showraw.android.presets.PresetRepository
import com.showraw.android.video.VideoCaptureManager
import com.showraw.android.video.VideoExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Phase2Activity : AppCompatActivity() {

    private lateinit var binding: ActivityPhase2Binding
    private val scope        = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val audioEngine  = AudioEngine()
    private val videoManager = VideoCaptureManager(this) // lazy init após context
    private val preset       = PresetRepository.findById("show")!!

    private var wavWriter:   WavWriter? = null
    private var tempVideo:   File?      = null
    private var isRecording  = false
    private var recordStart  = 0L

    private val timerHandler  = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            val secs = (System.currentTimeMillis() - recordStart) / 1000
            binding.tvTimer.text = "%02d:%02d".format(secs / 60, secs % 60)
            timerHandler.postDelayed(this, 1_000)
        }
    }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.all { it }) startCamera()
        else Toast.makeText(this, "Câmera e microfone são necessários.", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhase2Binding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvPresetName.text = "${preset.emoji} ${preset.name}"

        audioEngine.onStats      = { stats -> runOnUiThread { updateStats(stats) } }
        audioEngine.onSplWarning = { runOnUiThread { binding.tvSplWarning.visibility = View.VISIBLE } }

        binding.btnRecord.setOnClickListener      { toggleRecording() }
        binding.btnNovaGravacao.setOnClickListener { resetToIdle() }

        checkAndRequestPermissions()
    }

    // ── Câmera ─────────────────────────────────────────────────────

    private fun startCamera() {
        videoManager.bindToLifecycle(this, binding.previewView, preset) {
            binding.btnRecord.isEnabled = true
        }
    }

    // ── Gravação ───────────────────────────────────────────────────

    private fun toggleRecording() {
        if (isRecording) stopRecording() else startRecording()
    }

    private fun startRecording() {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        tempVideo = File(getExternalFilesDir(null), "tmp_video_$ts.mp4")
        val wavFile = File(getExternalFilesDir(null), "tmp_audio_$ts.wav")

        // Câmera inicia primeiro; AudioEngine arranca no callback onStarted
        // para garantir sincronização máxima entre os dois fluxos.
        videoManager.startRecording(
            outputFile = tempVideo!!,
            onStarted  = {
                wavWriter = WavWriter(wavFile)
                audioEngine.configure(preset)
                audioEngine.onBufferReady = { buf, size -> wavWriter?.write(buf, size) }
                audioEngine.start()

                isRecording = true
                recordStart = System.currentTimeMillis()
                timerHandler.post(timerRunnable)

                runOnUiThread {
                    binding.btnRecord.text = "■ Parar Gravação"
                    binding.tvRecDot.visibility = View.VISIBLE
                    binding.tvSplWarning.visibility = View.GONE
                }
            },
            onFinalized = { success ->
                if (!success) {
                    runOnUiThread { Toast.makeText(this, "Erro na câmera.", Toast.LENGTH_SHORT).show() }
                }
            }
        )
    }

    private fun stopRecording() {
        timerHandler.removeCallbacks(timerRunnable)
        videoManager.stopRecording()
        audioEngine.stop()

        val wavFile = wavWriter?.file
        wavWriter?.finish()
        wavWriter = null
        isRecording = false

        binding.tvRecDot.visibility = View.GONE
        binding.btnRecord.isEnabled = false
        showExportOverlay(tempVideo, wavFile)
    }

    // ── Exportação ─────────────────────────────────────────────────

    private fun showExportOverlay(videoFile: File?, wavFile: File?) {
        binding.exportOverlay.visibility = View.VISIBLE
        binding.exportProgress.progress  = 0
        binding.tvExportStatus.text      = "Codificando AAC…"
        binding.tvExportResult.visibility    = View.GONE
        binding.btnNovaGravacao.visibility   = View.GONE

        if (videoFile == null || wavFile == null) {
            binding.tvExportStatus.text = "Erro: arquivo temporário não encontrado."
            binding.btnNovaGravacao.visibility = View.VISIBLE
            return
        }

        val ts     = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outFile = File(getExternalFilesDir(null), "showraw_$ts.mp4")

        scope.launch {
            try {
                VideoExporter.mux(videoFile, wavFile, outFile) { progress ->
                    runOnUiThread {
                        binding.exportProgress.progress = (progress * 100).toInt()
                        binding.tvExportStatus.text = when {
                            progress < 0.70f -> "Codificando AAC… (${"%.0f".format(progress * 100)}%)"
                            progress < 0.95f -> "Combinando vídeo + áudio…"
                            else             -> "Finalizando…"
                        }
                    }
                }

                val mb = "%.1f".format(outFile.length() / 1_048_576f)
                runOnUiThread {
                    binding.tvExportStatus.text = "✅ MP4 exportado com sucesso!"
                    binding.exportProgress.progress = 100
                    binding.tvExportResult.text =
                        "📁 ${mb} MB\n${outFile.absolutePath}"
                    binding.tvExportResult.visibility  = View.VISIBLE
                    binding.btnNovaGravacao.visibility = View.VISIBLE
                }

            } catch (e: Exception) {
                runOnUiThread {
                    binding.tvExportStatus.text = "❌ Erro: ${e.message}"
                    binding.btnNovaGravacao.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun resetToIdle() {
        binding.exportOverlay.visibility = View.GONE
        binding.btnRecord.isEnabled = true
        binding.btnRecord.text = "● Iniciar Gravação"
        binding.tvTimer.text = "00:00"
        binding.tvPeak.text = "— dBFS"
        binding.tvGr.text   = "0.0 dB"
        binding.vuBar.progress = 0
    }

    // ── Stats ──────────────────────────────────────────────────────

    private fun updateStats(stats: AudioStats) {
        val peak = stats.peakDbFs.coerceIn(-96f, 0f)
        binding.tvPeak.text = "${"%.1f".format(peak)} dBFS"
        binding.tvGr.text   = "${"%.1f".format(stats.gainReductionDb)} dB"

        val progress = (96 + peak).toInt().coerceIn(0, 96)
        binding.vuBar.progress = progress
        binding.vuBar.progressTintList = ColorStateList.valueOf(
            when {
                peak > -6f  -> getColor(R.color.vu_red)
                peak > -18f -> getColor(R.color.vu_yellow)
                else        -> getColor(R.color.vu_green)
            }
        )
    }

    // ── Permissões ─────────────────────────────────────────────────

    private fun checkAndRequestPermissions() {
        val needed = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
            .toTypedArray()

        if (needed.isEmpty()) startCamera()
        else permLauncher.launch(needed)
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacks(timerRunnable)
        audioEngine.stop()
        videoManager.unbind()
        scope.cancel()
    }
}
