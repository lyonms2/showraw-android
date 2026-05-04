package com.showraw.android.ui.library

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.showraw.android.databinding.FragmentLibraryBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var adapter: RecordingAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = RecordingAdapter(
            context  = requireContext(),
            scope    = scope,
            onShare  = { file, mime -> shareFile(file, mime) },
            onDelete = { item, pos -> confirmDelete(item, pos) },
        )
        binding.rvLibrary.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLibrary.adapter = adapter

        loadRecordings()
    }

    private fun loadRecordings() {
        scope.launch {
            val items = withContext(Dispatchers.IO) {
                val dir = requireContext().getExternalFilesDir(null) ?: return@withContext emptyList()
                dir.listFiles { f ->
                    f.name.endsWith(".mp4") &&
                    !f.name.contains("_audio") &&
                    !f.name.startsWith("tmp_")
                }
                    ?.sortedByDescending { it.lastModified() }
                    ?.map { video ->
                        val audioName = "${video.nameWithoutExtension}_audio.m4a"
                        val audioFile = File(video.parent, audioName).takeIf { it.exists() }
                        RecordingItem(video, audioFile)
                    }
                    ?: emptyList()
            }

            adapter.submitList(items)
            binding.tvLibraryEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun shareFile(file: File, mimeType: String) {
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

    private fun confirmDelete(item: RecordingItem, pos: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle("Excluir gravação?")
            .setMessage("${item.videoFile.name}\nEssa ação não pode ser desfeita.")
            .setPositiveButton("Excluir") { _, _ ->
                item.videoFile.delete()
                item.audioFile?.delete()
                loadRecordings()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scope.cancel()
        _binding = null
    }
}
