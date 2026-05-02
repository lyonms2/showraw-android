package com.showraw.android.video

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.showraw.android.presets.Preset
import com.showraw.android.presets.Resolution

/**
 * Gerencia o pipeline CameraX.
 * A sincronização com o AudioEngine é feita via timestamps de hardware em VideoExporter.
 */
class VideoCaptureManager(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null

    fun bindToLifecycle(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        preset: Preset,
        onReady: () -> Unit,
    ) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            cameraProvider = providerFuture.get()
            // TODO: configurar Preview, VideoCapture e resolver resolução do preset
            onReady()
        }, ContextCompat.getMainExecutor(context))
    }

    fun unbind() {
        cameraProvider?.unbindAll()
    }

    private fun resolveQuality(resolution: Resolution): androidx.camera.video.Quality =
        when (resolution) {
            Resolution.R4K_30, Resolution.R4K_60     -> androidx.camera.video.Quality.UHD
            Resolution.R1080P_30, Resolution.R1080P_60 -> androidx.camera.video.Quality.FHD
        }
}
