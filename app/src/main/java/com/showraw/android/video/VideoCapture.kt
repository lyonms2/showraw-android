package com.showraw.android.video

import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.view.Surface
import androidx.camera.camera2.interop.Camera2Interop
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

            // Rotação atual do display — necessária para que o MP4 grave com a
            // orientação correta. Sem isso, o sensor da câmera traseira (90° físico)
            // grava sempre em paisagem independente de como o celular está segurado.
            val rotation = previewView.display?.rotation ?: Surface.ROTATION_0

            val quality = resolveQuality(preset.videoResolution)
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(quality, FallbackStrategy.lowerQualityThan(quality)))
                .build()
            val vcBuilder = VideoCapture.Builder(recorder).setTargetRotation(rotation)
            if (preset.stabilization) {
                Camera2Interop.Extender(vcBuilder).setCaptureRequestOption(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON,
                )
            }
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
