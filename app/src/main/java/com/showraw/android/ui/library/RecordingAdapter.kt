package com.showraw.android.ui.library

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.showraw.android.databinding.ItemRecordingBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class RecordingItem(
    val videoFile: File,
    val audioFile: File?,
)

class RecordingAdapter(
    private val context: Context,
    private val scope:   CoroutineScope,
    private val onShare: (File, String) -> Unit,
    private val onDelete: (RecordingItem, Int) -> Unit,
) : RecyclerView.Adapter<RecordingAdapter.VH>() {

    private val items = mutableListOf<RecordingItem>()

    fun submitList(list: List<RecordingItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemRecordingBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position], position)

    inner class VH(private val b: ItemRecordingBinding) : RecyclerView.ViewHolder(b.root) {

        fun bind(item: RecordingItem, pos: Int) {
            val name = item.videoFile.nameWithoutExtension
            b.tvItemName.text = name

            // Duração e data
            scope.launch {
                val (durationMs, dateMs) = withContext(Dispatchers.IO) {
                    val dur = runCatching {
                        MediaMetadataRetriever().use { r ->
                            r.setDataSource(item.videoFile.absolutePath)
                            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                        }
                    }.getOrDefault(0L)
                    Pair(dur, item.videoFile.lastModified())
                }

                val mins  = TimeUnit.MILLISECONDS.toMinutes(durationMs)
                val secs  = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
                val date  = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(dateMs))
                val meta  = "%02d:%02d  ·  %s".format(mins, secs, date)

                val videoMb = "%.1f MB vídeo".format(item.videoFile.length() / 1_048_576f)
                val audioMb = item.audioFile?.let { " + %.1f MB áudio".format(it.length() / 1_048_576f) } ?: ""

                withContext(Dispatchers.Main) {
                    b.tvItemMeta.text = meta
                    b.tvItemSize.text = videoMb + audioMb
                }
            }

            // Thumbnail
            b.ivThumb.setImageBitmap(null)
            scope.launch {
                val thumb: Bitmap? = withContext(Dispatchers.IO) {
                    runCatching {
                        MediaMetadataRetriever().use { r ->
                            r.setDataSource(item.videoFile.absolutePath)
                            r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        }
                    }.getOrNull()
                }
                withContext(Dispatchers.Main) {
                    if (thumb != null) b.ivThumb.setImageBitmap(thumb)
                }
            }

            val playClick = { _: android.view.View -> playVideo(item.videoFile) }
            b.ivThumb.setOnClickListener(playClick)
            b.tvItemName.setOnClickListener(playClick)

            b.btnShareVideo.setOnClickListener { onShare(item.videoFile, "video/mp4") }
            b.btnShareAudio.isEnabled = item.audioFile != null
            b.btnShareAudio.alpha     = if (item.audioFile != null) 1f else 0.35f
            b.btnShareAudio.setOnClickListener {
                item.audioFile?.let { onShare(it, "audio/mp4") }
            }
            b.btnDelete.setOnClickListener { onDelete(item, pos) }
        }

        private fun playVideo(file: File) {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file,
            )
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
