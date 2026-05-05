package com.showraw.android.ui.export

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.showraw.android.Navigator
import com.showraw.android.databinding.FragmentExportBinding
import com.showraw.android.video.ExportResult
import com.showraw.android.video.GalleryExporter
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
        binding.btnShareYoutube.setOnClickListener { shareToYouTube(exportResult?.videoMp4) }
        binding.btnSaveGallery.setOnClickListener { saveToGallery() }

        val location = getLastKnownLocation()
        startExport(File(videoPath), File(wavPath), sessionName, location)
    }

    private fun getLastKnownLocation(): Location? {
        val hasFine   = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)   == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return null
        val lm = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
    }

    private fun startExport(videoFile: File, wavFile: File, sessionName: String, location: Location? = null) {
        val ts     = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val prefix = if (sessionName.isNotEmpty()) sessionName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(40) else "showraw"
        val out    = File(requireContext().getExternalFilesDir(null), "${prefix}_$ts.mp4")

        scope.launch {
            try {
                val result = VideoExporter.mux(videoFile, wavFile, out, location) { progress ->
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

                // Extrair thumbnail e metadados off-thread
                data class ThumbAndMeta(
                    val thumb: android.graphics.Bitmap?,
                    val meta: GalleryExporter.VideoMeta,
                )
                val tm = withContext(Dispatchers.IO) {
                    val thumb = runCatching {
                        MediaMetadataRetriever().use { r ->
                            r.setDataSource(result.videoMp4.absolutePath)
                            r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        }
                    }.getOrNull()
                    val meta = GalleryExporter.readMeta(result.videoMp4)
                    ThumbAndMeta(thumb, meta)
                }

                val videoMb = "%.1f".format(result.videoMp4.length() / 1_048_576f)
                val audioMb = "%.1f".format(result.audioM4a.length() / 1_048_576f)

                activity?.runOnUiThread {
                    binding.exportProgress.progress = 100
                    binding.tvExportStatus.text     = "Concluído"
                    binding.tvResultMeta.text       = tm.meta.summary
                    binding.tvResultVideo.text      = "$videoMb MB · ${result.videoMp4.name}"
                    binding.tvResultAudio.text      = "$audioMb MB · ${result.audioM4a.name}"
                    if (tm.thumb != null) {
                        binding.ivThumbnail.setImageBitmap(tm.thumb)
                        binding.ivThumbnail.setOnClickListener { playVideo(result.videoMp4) }
                    }
                    binding.resultCard.visibility        = View.VISIBLE
                    binding.btnSaveGallery.visibility    = View.VISIBLE
                    binding.btnShareRow.visibility       = View.VISIBLE
                    binding.btnShareYoutube.visibility   = View.VISIBLE
                    binding.btnNewRecording.visibility   = View.VISIBLE
                }

            } catch (e: Exception) {
                activity?.runOnUiThread {
                    binding.tvExportStatus.text        = "❌ Erro: ${e.message}"
                    binding.btnNewRecording.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun saveToGallery() {
        val file = exportResult?.videoMp4 ?: return
        binding.btnSaveGallery.isEnabled = false
        scope.launch {
            val ok = GalleryExporter.saveToGallery(requireContext(), file)
            activity?.runOnUiThread {
                if (ok) {
                    binding.btnSaveGallery.text = "✓  Salvo na Galeria"
                    android.widget.Toast.makeText(requireContext(),
                        "Vídeo salvo em Galeria → Movies/ShowRaw", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    binding.btnSaveGallery.isEnabled = true
                    android.widget.Toast.makeText(requireContext(),
                        "Erro ao salvar na galeria.", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun shareToYouTube(file: File?) {
        file ?: return
        val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", file)
        val youtubeIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage("com.google.android.youtube")
        }
        if (youtubeIntent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(youtubeIntent)
        } else {
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Enviar vídeo"
            ))
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
