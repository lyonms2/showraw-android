package com.showraw.android.ui.export

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.showraw.android.Navigator
import com.showraw.android.databinding.FragmentExportBinding
import com.showraw.android.video.ExportResult
import com.showraw.android.video.VideoExporter
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

class ExportFragment : Fragment() {

    private var _binding: FragmentExportBinding? = null
    private val binding get() = _binding!!

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var exportResult: ExportResult? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentExportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val videoPath   = arguments?.getString(ARG_VIDEO_PATH)   ?: return
        val wavPath     = arguments?.getString(ARG_WAV_PATH)     ?: return
        val sessionName = arguments?.getString(ARG_SESSION_NAME) ?: ""

        binding.btnNewRecording.setOnClickListener {
            (requireActivity() as? Navigator)?.newRecording()
        }
        binding.btnShareVideo.setOnClickListener { shareFile(exportResult?.videoMp4, "video/mp4") }
        binding.btnShareAudio.setOnClickListener { shareFile(exportResult?.audioM4a, "audio/mp4") }

        startExport(File(videoPath), File(wavPath), sessionName)
    }

    private fun startExport(videoFile: File, wavFile: File, sessionName: String) {
        val ts     = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val prefix = if (sessionName.isNotEmpty()) sessionName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(40) else "showraw"
        val out    = File(requireContext().getExternalFilesDir(null), "${prefix}_$ts.mp4")

        scope.launch {
            try {
                val result = VideoExporter.mux(videoFile, wavFile, out) { progress ->
                    activity?.runOnUiThread {
                        binding.exportProgress.progress = (progress * 100).toInt()
                        binding.tvExportStatus.text = when {
                            progress < 0.70f -> "Codificando AAC… (${"%.0f".format(progress * 100)}%)"
                            progress < 0.95f -> "Combinando vídeo + áudio…"
                            else             -> "Finalizando…"
                        }
                    }
                }

                exportResult = result

                // Extrair thumbnail off-thread
                val thumb = withContext(Dispatchers.IO) {
                    runCatching {
                        MediaMetadataRetriever().use { r ->
                            r.setDataSource(result.videoMp4.absolutePath)
                            r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        }
                    }.getOrNull()
                }

                val videoMb = "%.1f".format(result.videoMp4.length() / 1_048_576f)
                val audioMb = "%.1f".format(result.audioM4a.length() / 1_048_576f)

                activity?.runOnUiThread {
                    binding.exportProgress.progress = 100
                    binding.tvExportStatus.text     = "Concluído"
                    binding.tvResultVideo.text      = "$videoMb MB · ${result.videoMp4.name}"
                    binding.tvResultAudio.text      = "$audioMb MB · ${result.audioM4a.name}"
                    if (thumb != null) {
                        binding.ivThumbnail.setImageBitmap(thumb)
                        binding.ivThumbnail.setOnClickListener { playVideo(result.videoMp4) }
                    }
                    binding.resultCard.visibility      = View.VISIBLE
                    binding.btnShareRow.visibility     = View.VISIBLE
                    binding.btnNewRecording.visibility = View.VISIBLE
                }

            } catch (e: Exception) {
                activity?.runOnUiThread {
                    binding.tvExportStatus.text        = "❌ Erro: ${e.message}"
                    binding.btnNewRecording.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun shareFile(file: File?, mimeType: String) {
        file ?: return
        val uri: Uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.provider",
            file,
        )
        val label = if (mimeType.startsWith("video")) "Compartilhar vídeo" else "Compartilhar áudio"
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, label
        ))
    }

    private fun playVideo(file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.provider",
            file,
        )
        startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scope.cancel()
        _binding = null
    }

    companion object {
        private const val ARG_VIDEO_PATH   = "video_path"
        private const val ARG_WAV_PATH     = "wav_path"
        private const val ARG_SESSION_NAME = "session_name"

        fun newInstance(videoPath: String, wavPath: String, sessionName: String = "") =
            ExportFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_VIDEO_PATH,   videoPath)
                    putString(ARG_WAV_PATH,     wavPath)
                    putString(ARG_SESSION_NAME, sessionName)
                }
            }
    }
}
