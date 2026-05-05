package com.showraw.android.video

import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.util.Range
import android.view.Surface
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.showraw.android.presets.Preset
import com.showraw.android.presets.Resolution
import java.io.File

class VideoCaptureManager(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var camera: Camera? = null
    private var cameraFacing = CameraSelector.LENS_FACING_BACK

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    fun bindToLifecycle(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        preset: Preset,
        onReady: () -> Unit,
    ) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            cameraProvider = future.get()

            val rotation = previewView.display?.rotation ?: Surface.ROTATION_0

            val quality = resolveQuality(preset.videoResolution)
            val recorderBuilder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(quality, FallbackStrategy.lowerQualityThan(quality)))
            if (preset.targetBitrateKbps > 0) {
                recorderBuilder.setTargetVideoEncodingBitRate(preset.targetBitrateKbps * 1_000)
            }

            val vcBuilder = VideoCapture.Builder(recorderBuilder.build()).setTargetRotation(rotation)

            val ext = Camera2Interop.Extender(vcBuilder)
            if (preset.stabilization) {
                ext.setCaptureRequestOption(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON,
                )
            }
            // Forçar FPS alvo — sem isso R4K_60/R1080P_60 ficam no padrão do dispositivo (geralmente 30)
            val targetFps = when (preset.videoResolution) {
                Resolution.R4K_60, Resolution.R1080P_60 -> 60
                else -> 30
            }
            ext.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                Range(targetFps, targetFps),
            )

            videoCapture = vcBuilder.build()

            val preview = Preview.Builder()
                .setTargetRotation(rotation)
                .build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val selector = CameraSelector.Builder()
                .requireLensFacing(cameraFacing)
                .build()

            try {
                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner, selector, preview, videoCapture,
                )
                onReady()
            } catch (e: Exception) {
                android.util.Log.e("VideoCaptureManager", "bindToLifecycle falhou", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    fun setWbMode(mode: WbMode) {
        val cam = camera ?: return
        Camera2CameraControl.from(cam.cameraControl).addCaptureRequestOptions(
            CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, mode.awbMode)
                .build()
        )
    }

    fun flipCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        preset: Preset,
        onReady: () -> Unit,
    ) {
        cameraFacing = if (cameraFacing == CameraSelector.LENS_FACING_BACK)
            CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        bindToLifecycle(lifecycleOwner, previewView, preset, onReady)
    }

    /**
     * Inicia a gravação de vídeo SEM áudio — o áudio é capturado pelo AudioEngine.
     * [onStarted] é chamado quando a câmera confirma início; inicie o AudioEngine aqui
     * para garantir sincronização máxima.
     */
    fun startRecording(
        outputFile: File,
        onStarted:   () -> Unit,
        onFinalized: (success: Boolean) -> Unit,
    ): Boolean {
        val vc = videoCapture ?: return false
        val opts = FileOutputOptions.Builder(outputFile).build()

        activeRecording = vc.output
            .prepareRecording(context, opts)
            // Sem withAudioEnabled() — áudio é nosso
            .start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start    -> onStarted()
                    is VideoRecordEvent.Finalize -> onFinalized(!event.hasError())
                    else -> {}
                }
            }
        return true
    }

    fun pauseRecording()  { activeRecording?.pause() }
    fun resumeRecording() { activeRecording?.resume() }

    fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    fun updateVideoRotation(rotation: Int) {
        videoCapture?.setTargetRotation(rotation)
    }

    fun getCameraControl(): CameraControl? = camera?.cameraControl
    fun getCameraInfo(): CameraInfo?       = camera?.cameraInfo

    fun unbind() {
        cameraProvider?.unbindAll()
    }

    private fun resolveQuality(res: Resolution): Quality = when (res) {
        Resolution.R4K_30, Resolution.R4K_60       -> Quality.UHD
        Resolution.R1080P_30, Resolution.R1080P_60 -> Quality.FHD
    }
}
