package com.showraw.android.ui.recording

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.AudioDeviceInfo
import android.media.AudioManager
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
import com.showraw.android.video.ShutterMode
import com.showraw.android.video.VideoCaptureManager
import com.showraw.android.video.WbMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.lifecycle.lifecycleScope
import kotlin.math.abs
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private var monitoringEnabled = false
    private var isRecording = false
    private var currentUiRotation = 0f
    private var isPaused    = false
    private var recordStart = 0L
    private var recordedMs  = 0L
    private var freeMbAtStart = 0L
    private var storageTickCount = 0
    private var currentWbMode      = WbMode.AUTO
    private var currentShutterMode = ShutterMode.AUTO
    private var currentIso         = 400

    private val headsetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_HEADSET_PLUG) updateAudioSourceBadge()
        }
    }

    private val orientationListener by lazy {
        object : OrientationEventListener(requireContext()) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val surfaceRotation = when {
                    orientation <= 45 || orientation > 315 -> Surface.ROTATION_0
                    orientation in 46..134                 -> Surface.ROTATION_270
                    orientation in 135..224                -> Surface.ROTATION_180
                    else                                   -> Surface.ROTATION_90
                }
                videoManager.updateVideoRotation(surfaceRotation)
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

            if (preset.estimatedMbPerMin > 0) {
                val totalSecs  = freeMbAtStart * 60L / preset.estimatedMbPerMin
                val remainSecs = (totalSecs - secs).coerceAtLeast(0L)
                _binding?.tvTimeRemaining?.text = "−%02d:%02d".format(remainSecs / 60, remainSecs % 60)
            }

            val maxMs = preset.maxDurationMinutes * 60_000L
            if (maxMs > 0 && elapsed >= maxMs) {
                stopRecording()
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Duração máxima atingida — gravação finalizada.", Toast.LENGTH_SHORT).show()
                }
                return
            }

            context?.startService(Intent(context, RecordingService::class.java).also {
                it.action = RecordingService.ACTION_UPDATE
                it.putExtra(RecordingService.EXTRA_ELAPSED, "%02d:%02d".format(secs / 60, secs % 60))
                it.putExtra(RecordingService.EXTRA_PAUSED, false)
            })

            if (++storageTickCount >= 30) { storageTickCount = 0; updateStorageBadge() }

            timerHandler.postDelayed(this, 1_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val presetId = arguments?.getString(ARG_PRESET_ID) ?: "show"
        preset = PresetRepository.findById(presetId) ?: PresetRepository.all.first()
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
        binding.tvResolution.text = preset.videoResolution.label
        updateAudioSourceBadge()
        updateStorageBadge()

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
        binding.btnMonitoring.setOnClickListener { toggleMonitoring() }
        binding.btnWb.setOnClickListener { cycleWbMode() }

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
        setupShutterButtons()
        setupZoomButtons()
        checkAndRequestPermissions()
    }

    // ── Gestures ──────────────────────────────────────────────────────────────

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
                    updateZoomButtons(newRatio)
                    return true
                }
            })

        val tapDetector = GestureDetector(requireContext(),
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    val control = videoManager.getCameraControl() ?: return false
                    val point   = binding.previewView.meteringPointFactory.createPoint(e.x, e.y)
                    control.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                    showFocusRing(e.x, e.y)
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

    // ── Exposure (EV em auto, ISO em manual) ──────────────────────────────────

    private fun setupExposureSlider() {
        binding.sbExposure.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                if (currentShutterMode == ShutterMode.AUTO) {
                    val control = videoManager.getCameraControl() ?: return
                    val info    = videoManager.getCameraInfo()    ?: return
                    val range   = info.exposureState.exposureCompensationRange
                    val index   = (range.lower + (range.upper - range.lower) * p / 100)
                    control.setExposureCompensationIndex(index)
                    _binding?.tvExposureVal?.text = if (index >= 0) "+$index" else "$index"
                } else {
                    val iso = isoFromProgress(p)
                    currentIso = iso
                    videoManager.setIso(iso)
                    _binding?.tvExposureVal?.text = "ISO $iso"
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar)  = Unit
        })
    }

    private fun isoFromProgress(p: Int): Int {
        // 0 → ISO 100, 100 → ISO 3200 (5 stops, log scale)
        val stops = p / 100f * 5f
        return (100.0 * 2.0.pow(stops.toDouble())).toInt().coerceIn(100, 3200)
    }

    // ── Shutter speed ─────────────────────────────────────────────────────────

    private fun setupShutterButtons() {
        listOf(
            binding.btnShutterAuto  to ShutterMode.AUTO,
            binding.btnShutter50    to ShutterMode.S1_50,
            binding.btnShutter60    to ShutterMode.S1_60,
            binding.btnShutter100   to ShutterMode.S1_100,
            binding.btnShutter120   to ShutterMode.S1_120,
        ).forEach { (btn, mode) -> btn.setOnClickListener { selectShutter(mode) } }
    }

    private fun selectShutter(mode: ShutterMode) {
        currentShutterMode = mode
        if (mode == ShutterMode.AUTO) {
            videoManager.setShutterMode(mode, currentIso)
            binding.sbExposure.progress = 50
            binding.tvExposureVal.text  = "0"
        } else {
            currentIso = 400
            videoManager.setShutterMode(mode, currentIso)
            binding.sbExposure.progress = 33  // ISO ~400
            binding.tvExposureVal.text  = "ISO 400"
        }
        updateShutterUi()
        binding.tvExposureLabel.text = if (mode == ShutterMode.AUTO) "EV" else "ISO"
    }

    private fun updateShutterUi() {
        listOf(
            binding.btnShutterAuto  to ShutterMode.AUTO,
            binding.btnShutter50    to ShutterMode.S1_50,
            binding.btnShutter60    to ShutterMode.S1_60,
            binding.btnShutter100   to ShutterMode.S1_100,
            binding.btnShutter120   to ShutterMode.S1_120,
        ).forEach { (btn, mode) ->
            val active = mode == currentShutterMode
            btn.backgroundTintList = ColorStateList.valueOf(
                if (active) Color.parseColor("#EF9F27") else Color.parseColor("#80000000")
            )
            btn.setTextColor(if (active) Color.parseColor("#0D0D0D") else Color.WHITE)
        }
    }

    // ── Zoom shortcuts ────────────────────────────────────────────────────────

    private fun setupZoomButtons() {
        binding.btnZoom05.setOnClickListener { setZoomRatio(0.5f) }
        binding.btnZoom1.setOnClickListener  { setZoomRatio(1.0f) }
        binding.btnZoom2.setOnClickListener  { setZoomRatio(2.0f) }
    }

    private fun setZoomRatio(ratio: Float) {
        val info    = videoManager.getCameraInfo()    ?: return
        val control = videoManager.getCameraControl() ?: return
        val state   = info.zoomState.value            ?: return
        val clamped = ratio.coerceIn(state.minZoomRatio, state.maxZoomRatio)
        control.setZoomRatio(clamped)
        _binding?.tvZoomLevel?.let { it.text = "%.1f×".format(clamped); it.visibility = View.VISIBLE }
        updateZoomButtons(clamped)
    }

    private fun updateZoomButtons(ratio: Float) {
        val b = _binding ?: return
        listOf(b.btnZoom05 to 0.5f, b.btnZoom1 to 1.0f, b.btnZoom2 to 2.0f).forEach { (btn, target) ->
            val active = abs(ratio - target) < 0.05f
            btn.backgroundTintList = ColorStateList.valueOf(
                if (active) Color.parseColor("#EF9F27") else Color.parseColor("#80000000")
            )
            btn.setTextColor(if (active) Color.parseColor("#0D0D0D") else Color.WHITE)
        }
    }

    // ── Focus ring ────────────────────────────────────────────────────────────

    private fun showFocusRing(x: Float, y: Float) {
        val ring = _binding?.focusRing ?: return
        val sizePx = 60f * resources.displayMetrics.density
        ring.translationX = x - sizePx / 2f
        ring.translationY = y - sizePx / 2f
        ring.visibility = View.VISIBLE
        ring.alpha = 1f
        ring.scaleX = 1.4f
        ring.scaleY = 1.4f
        ring.animate()
            .scaleX(1f).scaleY(1f)
            .setDuration(180)
            .withEndAction {
                ring.animate()
                    .alpha(0f)
                    .setStartDelay(700)
                    .setDuration(300)
                    .withEndAction { _binding?.focusRing?.visibility = View.GONE }
                    .start()
            }
            .start()
    }

    // ── Camera lifecycle ──────────────────────────────────────────────────────

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
        binding.waveformView.visibility  = View.GONE
        binding.tvZoomLevel.visibility   = View.GONE
        updateStabilizationButton()
        updateZoomButtons(1.0f)
        updateZoom05Button()
    }

    private fun updateZoom05Button() {
        val minZoom = videoManager.getCameraInfo()?.zoomState?.value?.minZoomRatio ?: 1.0f
        val supported = minZoom < 0.9f
        binding.btnZoom05.isEnabled = supported
        binding.btnZoom05.alpha     = if (supported) 1f else 0.4f
    }

    // ── Recording control ─────────────────────────────────────────────────────

    private fun startRecording() {
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

                context?.startService(Intent(context, RecordingService::class.java).also {
                    it.action = RecordingService.ACTION_START
                    it.putExtra(RecordingService.EXTRA_ELAPSED, "00:00")
                })

                _binding?.tvRecDot?.visibility        = View.VISIBLE
                _binding?.tvTimeRemaining?.visibility = View.VISIBLE
                _binding?.overlaySpl?.visibility      = View.GONE
                _binding?.etSessionName?.visibility   = View.GONE
                _binding?.waveformView?.visibility    = View.VISIBLE
                _binding?.btnStop?.isEnabled               = true
                _binding?.btnStop?.text                    = "■ Parar"
                _binding?.btnPause?.isEnabled              = true
                _binding?.btnFlipCamera?.isEnabled         = false
                _binding?.btnStabilization?.isEnabled      = false
                _binding?.btnMonitoring?.visibility        = View.VISIBLE
                _binding?.btnMonitoring?.isEnabled         = true
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

        context?.startService(Intent(context, RecordingService::class.java).also {
            it.action = RecordingService.ACTION_STOP
        })

        binding.tvRecDot.visibility        = View.GONE
        binding.tvTimeRemaining.visibility  = View.GONE
        binding.tvZoomLevel.visibility     = View.GONE
        binding.waveformView.visibility    = View.GONE
        binding.btnStop.isEnabled          = false
        binding.btnStop.text               = "■ Parar"
        binding.btnPause.isEnabled         = false
        binding.btnPause.text              = "⏸ Pausar"
        binding.btnMonitoring.isEnabled    = false
        binding.btnMonitoring.visibility   = View.GONE
        monitoringEnabled = false
        binding.overlayFinalizing.visibility = View.VISIBLE

        videoManager.stopRecording()
    }

    // ── Stats / badges ────────────────────────────────────────────────────────

    private fun getFreeStorageMb(): Long {
        val path = requireContext().getExternalFilesDir(null)?.absolutePath
            ?: Environment.getExternalStorageDirectory().absolutePath
        return StatFs(path).let { it.availableBlocksLong * it.blockSizeLong / (1024L * 1024L) }
    }

    private fun updateStats(stats: AudioStats) {
        val b = _binding ?: return
        val peak = stats.peakDbFs.coerceIn(-96f, 0f)
        b.tvPeak.text   = "${"%.1f".format(peak)} dBFS"
        b.tvGr.text     = "${"%.1f".format(stats.gainReductionDb)} dB"
        b.tvCompGr.text = "${"%.1f".format(stats.compressorGrDb)} dB"

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

    private fun updateAudioSourceBadge() {
        val b = _binding ?: return
        val am = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val inputs = am.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val hasExternal = inputs.any { d ->
            d.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            d.type == AudioDeviceInfo.TYPE_USB_HEADSET   ||
            d.type == AudioDeviceInfo.TYPE_USB_DEVICE    ||
            d.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
        }
        val isUsb = inputs.any { d ->
            d.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            d.type == AudioDeviceInfo.TYPE_USB_DEVICE  ||
            d.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
        }
        b.tvAudioSource.text = when {
            isUsb       -> "USB"
            hasExternal -> "MIC EXT"
            else        -> "MIC INT"
        }
        b.tvAudioSource.setTextColor(
            if (hasExternal) Color.parseColor("#EF9F27") else Color.parseColor("#999999")
        )
    }

    private fun updateStorageBadge() {
        val ctx = context ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val path  = ctx.getExternalFilesDir(null)?.absolutePath
                    ?: Environment.getExternalStorageDirectory().absolutePath
                val sf    = StatFs(path)
                val total = sf.totalBytes
                val free  = sf.availableBytes
                val pct   = if (total > 0) (free * 100L / total).toInt() else 0
                val freeGb = free.toDouble() / (1024.0 * 1024.0 * 1024.0)
                val text  = String.format(java.util.Locale.US, "%.1f GB · %d%%", freeGb, pct)
                val color = when {
                    pct < 10 -> Color.parseColor("#FF4444")
                    pct < 25 -> Color.parseColor("#EF9F27")
                    else     -> Color.parseColor("#999999")
                }
                withContext(Dispatchers.Main) {
                    _binding?.tvStorage?.text = text
                    _binding?.tvStorage?.setTextColor(color)
                }
            } catch (_: Exception) { }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        if (orientationListener.canDetectOrientation()) orientationListener.enable()
        @Suppress("DEPRECATION")
        requireContext().registerReceiver(headsetReceiver, IntentFilter(Intent.ACTION_HEADSET_PLUG))
        updateAudioSourceBadge()
        updateStorageBadge()
    }

    override fun onPause() {
        super.onPause()
        orientationListener.disable()
        requireContext().unregisterReceiver(headsetReceiver)
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

    // ── UI helpers ────────────────────────────────────────────────────────────

    private fun rotateUiElements(degrees: Float) {
        listOfNotNull(
            _binding?.btnStop,
            _binding?.btnPause,
            _binding?.btnFlipCamera,
            _binding?.btnStabilization,
            _binding?.btnMonitoring,
            _binding?.btnWb,
            _binding?.tvRecDot,
            _binding?.tvZoomLevel,
        ).forEach { v ->
            v.animate()
                .rotation(degrees)
                .setDuration(250)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
        val compact = degrees == 90f || degrees == -90f
        _binding?.btnStop?.text = when {
            compact && isRecording -> "■"
            compact                -> "●"
            isRecording            -> "■ Parar"
            else                   -> "● Gravar"
        }
        _binding?.btnPause?.text = when {
            compact && isPaused -> "▶"
            compact             -> "⏸"
            isPaused            -> "▶ Retomar"
            else                -> "⏸ Pausar"
        }
    }

    private fun toggleMonitoring() {
        monitoringEnabled = !monitoringEnabled
        audioEngine.enableMonitoring(monitoringEnabled)
        if (monitoringEnabled) {
            Toast.makeText(requireContext(),
                "Monitoramento ativo — pode haver latência de 50–200ms dependendo do dispositivo.",
                Toast.LENGTH_LONG).show()
        }
        binding.btnMonitoring.backgroundTintList = ColorStateList.valueOf(
            if (monitoringEnabled) Color.parseColor("#EF9F27") else Color.parseColor("#80000000")
        )
        binding.btnMonitoring.setTextColor(
            if (monitoringEnabled) Color.parseColor("#0D0D0D") else Color.WHITE
        )
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

    private fun cycleWbMode() {
        val modes = WbMode.entries
        currentWbMode = modes[(modes.indexOf(currentWbMode) + 1) % modes.size]
        videoManager.setWbMode(currentWbMode)
        binding.btnWb.text = currentWbMode.label
        val isActive = currentWbMode != WbMode.AUTO
        binding.btnWb.backgroundTintList = ColorStateList.valueOf(
            if (isActive) Color.parseColor("#EF9F27") else Color.parseColor("#80000000")
        )
        binding.btnWb.setTextColor(
            if (isActive) Color.parseColor("#0D0D0D") else Color.WHITE
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
