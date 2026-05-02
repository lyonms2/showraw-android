package com.showraw.android.ui.export

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.showraw.android.Navigator
import com.showraw.android.databinding.FragmentExportBinding
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

class ExportFragment : Fragment() {

    private var _binding: FragmentExportBinding? = null
    private val binding get() = _binding!!

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var outputFile: File? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentExportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val videoPath = arguments?.getString(ARG_VIDEO_PATH) ?: return
        val wavPath   = arguments?.getString(ARG_WAV_PATH)   ?: return

        binding.btnNewRecording.setOnClickListener {
            (requireActivity() as? Navigator)?.newRecording()
        }
        binding.btnShare.setOnClickListener { shareOutput() }

        startExport(File(videoPath), File(wavPath))
    }

    private fun startExport(videoFile: File, wavFile: File) {
        val ts  = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val out = File(requireContext().getExternalFilesDir(null), "showraw_$ts.mp4")
        outputFile = out

        scope.launch {
            try {
                VideoExporter.mux(videoFile, wavFile, out) { progress ->
                    activity?.runOnUiThread {
                        binding.exportProgress.progress = (progress * 100).toInt()
                        binding.tvExportStatus.text = when {
                            progress < 0.70f -> "Codificando AAC… (${"%.0f".format(progress * 100)}%)"
                            progress < 0.95f -> "Combinando vídeo + áudio…"
                            else             -> "Finalizando…"
                        }
                    }
                }

                val mb = "%.1f".format(out.length() / 1_048_576f)
                activity?.runOnUiThread {
                    binding.exportProgress.progress = 100
                    binding.tvExportStatus.text = "Concluído"
                    binding.resultCard.visibility    = View.VISIBLE
                    binding.tvResultPath.text        = "$mb MB · ${out.name}"
                    binding.btnShare.visibility         = View.VISIBLE
                    binding.btnNewRecording.visibility  = View.VISIBLE
                }

            } catch (e: Exception) {
                activity?.runOnUiThread {
                    binding.tvExportStatus.text = "❌ Erro: ${e.message}"
                    binding.btnNewRecording.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun shareOutput() {
        val file = outputFile ?: return
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.provider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Compartilhar gravação"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scope.cancel()
        _binding = null
    }

    companion object {
        private const val ARG_VIDEO_PATH = "video_path"
        private const val ARG_WAV_PATH   = "wav_path"

        fun newInstance(videoPath: String, wavPath: String) = ExportFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_VIDEO_PATH, videoPath)
                putString(ARG_WAV_PATH,   wavPath)
            }
        }
    }
}
