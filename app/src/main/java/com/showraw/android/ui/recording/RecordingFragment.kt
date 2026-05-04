package com.showraw.android.ui.recording

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.FocusMeteringAction
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.showraw.android.Navigator
import com.showraw.android.R
import com.showraw.android.RecordingService
import com.showraw.android.audio.AudioEngine
import com.showraw.android.audio.AudioStats
import com.showraw.android.audio.WavWriter
import com.showraw.android.databinding.FragmentRecordingBinding
import com.showraw.android.presets.Preset
import com.showraw.android.presets.PresetRepository
import com.showraw.android.ui.settings.SettingsFragment
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
    private var sessionName = ""
    private var stabilizationEnabled = false
    private var isRecording = false
    private var currentUiRotation = 0f
    private var isPaused    = false
    private var recordStart = 0L
    private var recordedMs  = 0L
    private var freeMbAtStart = 0L

    private val orientationListener by lazy {
        object : OrientationEventListener(requireContext()) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return

                // Metadado de rotação do vídeo (convenção CameraX)
                val surfaceRotation = when {
                    orientation <= 45 || orientation > 315 -> Surface.ROTATION_0
                    orientation in 46..134                 -> Surface.ROTATION_270
                    orientation in 135..224                -> Surface.ROTATION_180
                    else                                   -> Surface.ROTATION_90
                }
                videoManager.updateVideoRotation(surfaceRotation)

                // Ângulo de rotação dos ícones da UI (oposto à rotação física para compensar)
                val uiDegrees = when {
                    orientation <= 45 || orientation > 315 -> 0f
                    orientation in 46..134                 -> -90f
                    orientation in 135..224                -> 180f
                    else                                   -> 90f
                }
                if (uiDegrees != currentUiRotation) {
                    currentUiRotation = uiDegrees
                    rotateUiElements(uiDegrees)
                }
            }
        }
    }

    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            val elapsed  = recordedMs + (System.currentTimeMillis() - recordStart)
            val secs     = elapsed / 1000
            _binding?.tvTimer?.text = "%02d:%02d".format(secs / 60, secs % 60)

            val totalSecs  = freeMbAtStart * 60L / preset.estimatedMbPerMin
            val remainSecs = (totalSecs - secs).coerceAtLeast(0L)
            _binding?.tvTimeRemaining?.text = "−%02d:%02d".format(remainSecs / 60, remainSecs % 60)

            // Auto-split: parar quando atingir duração máxima
            val maxMs = preset.maxDurationMinutes * 60_000L
            if (maxMs > 0 && elapsed >= maxMs) {
                stopRecording()
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Duração máxima atingida — gravação finalizada.", Toast.LENGTH_SHORT).show()
                }
                return
            }

            // Atualizar notificação a cada segundo
            context?.startService(Intent(context, RecordingService::class.java).also {
                it.action = RecordingService.ACTION_UPDATE
                it.putExtra(RecordingService.EXTRA_ELAPSED, "%02d:%02d".format(secs / 60, secs % 60))
                it.putExtra(RecordingService.EXTRA_PAUSED, false)
            })

            timerHandler.postDelayed(this, 1_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val presetId = arguments?.getString(ARG_PRESET_ID) ?: "show"
        preset = if (presetId == "manual") {
            SettingsFragment.loadManualPreset(requireContext())
        } else {
            PresetRepository.findById(presetId) ?: PresetRepository.all.first()
        }
        stabilizationEnabled = preset.stabilization
        audioEngine.configure(preset)

        permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            if (perms.values.all { it }) bindCamera()
            else {
                Toast.makeText(requireContext(), "Câmera e microfone são necessários.", Toast.LENGTH_LONG).show()
                @Suppress("DEPRECATION")
                requireActivity().onBackPressed()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRecordingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        videoManager = VideoCaptureManager(requireContext())
        binding.tvPresetName.text = "${preset.emoji} ${preset.name}"

        audioEngine.onStats = { stats ->
            activity?.runOnUiThread {
                updateStats(stats)
                _binding?.waveformView?.push(stats.rms)
            }
        }
        audioEngine.onSplWarning = { activity?.runOnUiThread { _binding?.overlaySpl?.visibility = View.VISIBLE } }

        binding.btnStop.setOnClickListener {
            if (!isRecording) startRecording() else stopRecording()
        }
        binding.btnPause.setOnClickListener { togglePause() }
        binding.btnFlipCamera.setOnClickListener { flipCamera() }
        binding.btnStabilization.setOnClickListener { toggleStabilization() }

        binding.btnSplContinue.setOnClickListener { binding.overlaySpl.visibility = View.GONE }
        binding.btnSplMic.setOnClickListener {
            binding.overlaySpl.visibility = View.GONE
            Toast.makeText(requireContext(), "Conecte o microfone externo e reinicie a gravação.", Toast.LENGTH_LONG).show()
        }
        binding.btnSplStop.setOnClickListener {
            binding.overlaySpl.visibility = View.GONE
            stopRecording()
        }

        setupGestures()
        setupExposureSlider()
        checkAndRequestPermissions()
    }

    private fun setupGestures() {
        val scaleDetector = ScaleGestureDetector(requireContext(),
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val control = videoManager.getCameraControl() ?: return false
                    val state   = videoManager.getCameraInfo()?.zoomState?.value ?: return false
                    val newRatio = (state.zoomRatio * detector.scaleFactor)
                        .coerceIn(state.minZoomRatio, state.maxZoomRatio)
                    control.setZoomRatio(newRatio)
                    _binding?.tvZoomLevel?.let { it.text = "%.1f×".format(newRatio); it.visibility = View.VISIBLE }
                    return true
                }
            })

        val tapDetector = GestureDetector(requireContext(),
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    val control = videoManager.getCameraControl() ?: return false
                    val point   = binding.previewView.meteringPointFactory.createPoint(e.x, e.y)
                    control.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                    return true
                }
            })

        binding.previewView.setOnTouchListener { v, event ->
            scaleDetector.onTouchEvent(event)
            tapDetector.onTouchEvent(event)
            v.performClick()
            true
        }
    }

    private fun setupExposureSlider() {
        binding.sbExposure.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                val control = videoManager.getCameraControl() ?: return
                val info    = videoManager.getCameraInfo()    ?: return
                val range   = info.exposureState.exposureCompensationRange
                val index   = (range.lower + (range.upper - range.lower) * p / 100)
                control.setExposureCompensationIndex(index)
                _binding?.tvExposureVal?.text = if (index >= 0) "+$index" else "$index"
            }
            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar)  = Unit
        })
    }

    private fun checkAndRequestPermissions() {
        val needed = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            .filter { ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED }
            .toTypedArray()
        if (needed.isEmpty()) bindCamera() else permLauncher.launch(needed)
    }

    private fun bindCamera() {
        videoManager.bindToLifecycle(viewLifecycleOwner, binding.previewView, preset) {
            activity?.runOnUiThread { enterPreviewState() }
        }
    }

    private fun flipCamera() {
        binding.btnFlipCamera.isEnabled = false
        videoManager.flipCamera(viewLifecycleOwner, binding.previewView, preset) {
            activity?.runOnUiThread { enterPreviewState() }
        }
    }

    private fun enterPreviewState() {
        binding.btnFlipCamera.isEnabled    = true
        binding.btnStabilization.isEnabled = true
        binding.btnStop.isEnabled = true
        binding.btnStop.text = "● Gravar"
        binding.btnPause.isEnabled = false
        binding.btnPause.text = "⏸ Pausar"
        binding.etSessionName.visibility = View.VISIBLE
        binding.exposureRow.visibility   = View.GONE
        binding.waveformView.visibility  = View.GONE
        binding.tvZoomLevel.visibility   = View.GONE
        updateStabilizationButton()
    }

    private fun startRecording() {
        // Fechar teclado e capturar nome da sessão
        sessionName = binding.etSessionName.text.toString().trim()
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSessionName.windowToken, 0)

        val ts     = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val prefix = if (sessionName.isNotEmpty()) sanitize(sessionName) else ts
        tempVideo  = File(requireContext().getExternalFilesDir(null), "tmp_video_$ts.mp4")
        val wavFile = File(requireContext().getExternalFilesDir(null), "tmp_audio_$ts.wav")

        videoManager.startRecording(
            outputFile = tempVideo!!,
            onStarted = {
                wavWriter = WavWriter(wavFile)
                audioEngine.onBufferReady = { buf, size -> wavWriter?.write(buf, size) }
                audioEngine.start()

                isRecording = true
                isPaused    = false
                recordStart = System.currentTimeMillis()
                recordedMs  = 0L
                freeMbAtStart = getFreeStorageMb()
                timerHandler.post(timerRunnable)

                // Iniciar foreground service
                context?.startService(Intent(context, RecordingService::class.java).also {
                    it.action = RecordingService.ACTION_START
                    it.putExtra(RecordingService.EXTRA_ELAPSED, "00:00")
                })

                _binding?.tvRecDot?.visibility      = View.VISIBLE
                _binding?.tvTimeRemaining?.visibility = View.VISIBLE
                _binding?.overlaySpl?.visibility    = View.GONE
                _binding?.etSessionName?.visibility = View.GONE
                _binding?.exposureRow?.visibility   = View.VISIBLE
                _binding?.waveformView?.visibility  = View.VISIBLE
                _binding?.btnStop?.isEnabled             = true
                _binding?.btnStop?.text                  = "■ Parar"
                _binding?.btnPause?.isEnabled            = true
                _binding?.btnFlipCamera?.isEnabled       = false
                _binding?.btnStabilization?.isEnabled    = false
            },
            onFinalized = { success ->
                _binding?.overlayFinalizing?.visibility = View.GONE
                val vFile = tempVideo
                val wFile = pendingWavFile
                if (success && vFile != null && wFile != null) {
                    (activity as? Navigator)?.showExport(vFile.absolutePath, wFile.absolutePath, sessionName)
                } else if (isAdded) {
                    Toast.makeText(requireContext(), "Erro ao finalizar gravação.", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    private fun togglePause() {
        if (!isRecording) return
        if (!isPaused) {
            recordedMs += System.currentTimeMillis() - recordStart
            timerHandler.removeCallbacks(timerRunnable)
            audioEngine.pause()
            videoManager.pauseRecording()
            isPaused = true
            _binding?.btnPause?.text = "▶ Retomar"
            _binding?.tvRecDot?.visibility = View.INVISIBLE
            context?.startService(Intent(context, RecordingService::class.java).also {
                it.action = RecordingService.ACTION_UPDATE
                it.putExtra(RecordingService.EXTRA_PAUSED, true)
                it.putExtra(RecordingService.EXTRA_ELAPSED, binding.tvTimer.text.toString())
            })
        } else {
            recordStart = System.currentTimeMillis()
            timerHandler.post(timerRunnable)
            audioEngine.resume()
            videoManager.resumeRecording()
            isPaused = false
            _binding?.btnPause?.text = "⏸ Pausar"
            _binding?.tvRecDot?.visibility = View.VISIBLE
        }
    }

    private fun stopRecording() {
        if (!isRecording) return
        timerHandler.removeCallbacks(timerRunnable)
        audioEngine.stop()
        pendingWavFile = wavWriter?.file
        wavWriter?.finish()
        wavWriter = null
        isRecording = false
        isPaused    = false

        // Parar serviço
        context?.startService(Intent(context, RecordingService::class.java).also {
            it.action = RecordingService.ACTION_STOP
        })

        binding.tvRecDot.visibility       = View.GONE
        binding.tvTimeRemaining.visibility = View.GONE
        binding.tvZoomLevel.visibility    = View.GONE
        binding.waveformView.visibility   = View.GONE
        binding.exposureRow.visibility    = View.GONE
        binding.btnStop.isEnabled         = false
        binding.btnStop.text              = "■ Parar"
        binding.btnPause.isEnabled        = false
        binding.btnPause.text             = "⏸ Pausar"
        binding.overlayFinalizing.visibility = View.VISIBLE

        videoManager.stopRecording()
    }

    private fun getFreeStorageMb(): Long {
        val path = requireContext().getExternalFilesDir(null)?.absolutePath
            ?: Environment.getExternalStorageDirectory().absolutePath
        return StatFs(path).let { it.availableBlocksLong * it.blockSizeLong / (1024L * 1024L) }
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

    override fun onResume() {
        super.onResume()
        if (orientationListener.canDetectOrientation()) orientationListener.enable()
    }

    override fun onPause() {
        super.onPause()
        orientationListener.disable()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timerHandler.removeCallbacks(timerRunnable)
        audioEngine.stop()
        wavWriter?.finish()
        wavWriter = null
        videoManager.unbind()
        if (isRecording) {
            context?.startService(Intent(context, RecordingService::class.java).also {
                it.action = RecordingService.ACTION_STOP
            })
        }
        _binding = null
    }

    private fun rotateUiElements(degrees: Float) {
        listOfNotNull(
            _binding?.btnStop,
            _binding?.btnPause,
            _binding?.btnFlipCamera,
            _binding?.btnStabilization,
            _binding?.tvRecDot,
            _binding?.tvZoomLevel,
        ).forEach { v ->
            v.animate()
                .rotation(degrees)
                .setDuration(250)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
    }

    private fun toggleStabilization() {
        if (isRecording) return
        stabilizationEnabled = !stabilizationEnabled
        binding.btnStabilization.isEnabled = false
        videoManager.bindToLifecycle(viewLifecycleOwner, binding.previewView, preset.copy(stabilization = stabilizationEnabled)) {
            activity?.runOnUiThread { enterPreviewState() }
        }
    }

    private fun updateStabilizationButton() {
        binding.btnStabilization.backgroundTintList = ColorStateList.valueOf(
            if (stabilizationEnabled) Color.parseColor("#EF9F27") else Color.parseColor("#80000000")
        )
        binding.btnStabilization.setTextColor(
            if (stabilizationEnabled) Color.parseColor("#0D0D0D") else Color.WHITE
        )
    }

    private fun sanitize(s: String) = s.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(40)

    companion object {
        const val ARG_PRESET_ID = "preset_id"
        fun newInstance(presetId: String) = RecordingFragment().apply {
            arguments = Bundle().apply { putString(ARG_PRESET_ID, presetId) }
        }
    }
}
