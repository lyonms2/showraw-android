package com.showraw.android.ui.recording

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.showraw.android.Navigator
import com.showraw.android.R
import com.showraw.android.audio.AudioEngine
import com.showraw.android.audio.AudioStats
import com.showraw.android.audio.WavWriter
import com.showraw.android.databinding.FragmentRecordingBinding
import com.showraw.android.presets.Preset
import com.showraw.android.presets.PresetRepository
import com.showraw.android.video.VideoCaptureManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingFragment : Fragment() {

    private var _binding: FragmentRecordingBinding? = null
    private val binding get() = _binding!!

    private lateinit var preset: Preset
    private lateinit var videoManager: VideoCaptureManager
    private lateinit var permLauncher: ActivityResultLauncher<Array<String>>

    private val audioEngine = AudioEngine()

    private var wavWriter: WavWriter? = null
    private var tempVideo: File? = null
    private var pendingWavFile: File? = null
    private var isRecording = false
    private var recordStart = 0L
    private var freeMbAtStart = 0L

    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            val secs = (System.currentTimeMillis() - recordStart) / 1000
            _binding?.tvTimer?.text = "%02d:%02d".format(secs / 60, secs % 60)

            // Tempo restante = capacidade total estimada - tempo já gravado
            val totalSecs = freeMbAtStart * 60L / preset.estimatedMbPerMin
            val remainSecs = (totalSecs - secs).coerceAtLeast(0L)
            _binding?.tvTimeRemaining?.text = "−%02d:%02d".format(remainSecs / 60, remainSecs % 60)

            timerHandler.postDelayed(this, 1_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val presetId = arguments?.getString(ARG_PRESET_ID) ?: "show"
        preset = PresetRepository.findById(presetId) ?: PresetRepository.all.first()
        audioEngine.configure(preset)

        permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            if (perms.values.all { it }) {
                bindCamera()
            } else {
                Toast.makeText(requireContext(), "Câmera e microfone são necessários.", Toast.LENGTH_LONG).show()
                @Suppress("DEPRECATION")
                requireActivity().onBackPressed()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentRecordingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        videoManager = VideoCaptureManager(requireContext())
        binding.tvPresetName.text = "${preset.emoji} ${preset.name}"

        audioEngine.onStats      = { stats -> activity?.runOnUiThread { updateStats(stats) } }
        audioEngine.onSplWarning = { activity?.runOnUiThread { _binding?.overlaySpl?.visibility = View.VISIBLE } }

        binding.btnStop.setOnClickListener { stopRecording() }
        binding.btnSplContinue.setOnClickListener { binding.overlaySpl.visibility = View.GONE }
        binding.btnSplMic.setOnClickListener {
            binding.overlaySpl.visibility = View.GONE
            Toast.makeText(requireContext(), "Conecte o microfone externo e reinicie a gravação.", Toast.LENGTH_LONG).show()
        }
        binding.btnSplStop.setOnClickListener {
            binding.overlaySpl.visibility = View.GONE
            stopRecording()
        }

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val needed = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            .filter { ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED }
            .toTypedArray()
        if (needed.isEmpty()) bindCamera() else permLauncher.launch(needed)
    }

    private fun bindCamera() {
        videoManager.bindToLifecycle(viewLifecycleOwner, binding.previewView, preset) {
            startRecording()
        }
    }

    private fun startRecording() {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        tempVideo = File(requireContext().getExternalFilesDir(null), "tmp_video_$ts.mp4")
        val wavFile = File(requireContext().getExternalFilesDir(null), "tmp_audio_$ts.wav")

        videoManager.startRecording(
            outputFile = tempVideo!!,
            onStarted = {
                wavWriter = WavWriter(wavFile)
                audioEngine.onBufferReady = { buf, size -> wavWriter?.write(buf, size) }
                audioEngine.start()

                isRecording = true
                recordStart = System.currentTimeMillis()
                freeMbAtStart = getFreeStorageMb()
                timerHandler.post(timerRunnable)

                _binding?.tvRecDot?.visibility = View.VISIBLE
                _binding?.tvTimeRemaining?.visibility = View.VISIBLE
                _binding?.overlaySpl?.visibility = View.GONE
                _binding?.btnStop?.isEnabled = true
            },
            onFinalized = { success ->
                _binding?.overlayFinalizing?.visibility = View.GONE
                val vFile = tempVideo
                val wFile = pendingWavFile
                if (success && vFile != null && wFile != null) {
                    (activity as? Navigator)?.showExport(vFile.absolutePath, wFile.absolutePath)
                } else if (isAdded) {
                    Toast.makeText(requireContext(), "Erro ao finalizar gravação.", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    private fun stopRecording() {
        if (!isRecording) return
        timerHandler.removeCallbacks(timerRunnable)
        audioEngine.stop()
        pendingWavFile = wavWriter?.file
        wavWriter?.finish()
        wavWriter = null
        isRecording = false

        binding.tvRecDot.visibility = View.GONE
        binding.tvTimeRemaining.visibility = View.GONE
        binding.btnStop.isEnabled = false
        binding.overlayFinalizing.visibility = View.VISIBLE

        videoManager.stopRecording()
    }

    private fun getFreeStorageMb(): Long {
        val path = requireContext().getExternalFilesDir(null)?.absolutePath
            ?: Environment.getExternalStorageDirectory().absolutePath
        val stat = StatFs(path)
        return stat.availableBlocksLong * stat.blockSizeLong / (1024L * 1024L)
    }

    private fun updateStats(stats: AudioStats) {
        val b = _binding ?: return
        val peak = stats.peakDbFs.coerceIn(-96f, 0f)
        b.tvPeak.text = "${"%.1f".format(peak)} dBFS"
        b.tvGr.text   = "${"%.1f".format(stats.gainReductionDb)} dB"

        val progress = (96 + peak).toInt().coerceIn(0, 96)
        b.vuBar.progress = progress
        b.vuBar.progressTintList = ColorStateList.valueOf(
            when {
                peak > -6f  -> ContextCompat.getColor(requireContext(), R.color.vu_red)
                peak > -18f -> ContextCompat.getColor(requireContext(), R.color.vu_yellow)
                else        -> ContextCompat.getColor(requireContext(), R.color.vu_green)
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timerHandler.removeCallbacks(timerRunnable)
        audioEngine.stop()
        wavWriter?.finish()
        wavWriter = null
        videoManager.unbind()
        _binding = null
    }

    companion object {
        const val ARG_PRESET_ID = "preset_id"
        fun newInstance(presetId: String) = RecordingFragment().apply {
            arguments = Bundle().apply { putString(ARG_PRESET_ID, presetId) }
        }
    }
}
