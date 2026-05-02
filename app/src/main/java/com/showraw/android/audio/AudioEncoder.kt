package com.showraw.android.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer

object AudioEncoder {

    private const val MIME        = MediaFormat.MIMETYPE_AUDIO_AAC
    private const val BITRATE     = 256_000
    private const val SAMPLE_RATE = 48_000
    private const val CHANNELS    = 2

    /**
     * Converte WAV PCM 16-bit stereo gerado pelo WavWriter num MP4 de áudio AAC 256kbps.
     * O MP4 de saída é compatível com MediaExtractor para uso no mux final.
     */
    suspend fun encodeWavToAacMp4(wavFile: File, onProgress: (Float) -> Unit = {}): File =
        withContext(Dispatchers.IO) {
            val outFile = File(wavFile.parent, "${wavFile.nameWithoutExtension}_audio.mp4")

            val format = MediaFormat.createAudioFormat(MIME, SAMPLE_RATE, CHANNELS).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 65_536)
            }

            val codec = MediaCodec.createEncoderByType(MIME)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            val muxer         = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var audioTrack    = -1
            var muxerStarted  = false

            val wavDataSize = maxOf(0L, wavFile.length() - 44)
            val input       = BufferedInputStream(FileInputStream(wavFile))
            input.skip(44) // pular os 44 bytes do header WAV

            val pcmBuf     = ByteArray(8_192)
            val info       = MediaCodec.BufferInfo()
            val bytesPerUs = (SAMPLE_RATE * CHANNELS * 2).toLong() // bytes / segundo
            var ptsUs      = 0L
            var totalRead  = 0L
            var endOfInput = false

            fun drainOutput(drain: Boolean = false) {
                var idx = codec.dequeueOutputBuffer(info, if (drain) 100_000 else 10_000)
                while (idx >= 0 || idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    when {
                        idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (!muxerStarted) {
                                audioTrack   = muxer.addTrack(codec.outputFormat)
                                muxer.start()
                                muxerStarted = true
                            }
                        }
                        idx >= 0 -> {
                            val buf = codec.getOutputBuffer(idx)!!
                            if (muxerStarted && info.size > 0 &&
                                info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                muxer.writeSampleData(audioTrack, buf, info)
                            }
                            codec.releaseOutputBuffer(idx, false)
                        }
                    }
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    idx = codec.dequeueOutputBuffer(info, 0)
                }
            }

            while (true) {
                if (!endOfInput) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val cap   = minOf(pcmBuf.size, inBuf.capacity())
                        val read  = input.read(pcmBuf, 0, cap)
                        if (read < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            endOfInput = true
                        } else {
                            inBuf.clear()
                            inBuf.put(pcmBuf, 0, read)
                            codec.queueInputBuffer(inIdx, 0, read, ptsUs, 0)
                            ptsUs     += read * 1_000_000L / bytesPerUs
                            totalRead += read
                            if (wavDataSize > 0) onProgress((totalRead.toFloat() / wavDataSize).coerceIn(0f, 1f))
                        }
                    }
                }
                drainOutput(drain = endOfInput)
                if (endOfInput && (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0)) break
            }

            codec.stop()
            codec.release()
            muxer.stop()
            muxer.release()
            input.close()

            outFile
        }
}
