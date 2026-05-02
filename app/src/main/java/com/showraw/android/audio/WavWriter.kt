package com.showraw.android.audio

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Escreve PCM 16-bit stereo em arquivo WAV durante a gravação (streaming).
 * Chame write() a cada buffer processado e finish() para fechar o arquivo
 * e corrigir o header com o tamanho real dos dados.
 */
class WavWriter(
    val file: File,
    private val sampleRate: Int = 48_000,
    private val channels: Int   = 2,
) {
    private val stream   = BufferedOutputStream(FileOutputStream(file), 64 * 1024)
    private var dataSize = 0

    init {
        writeHeader()
    }

    fun write(buffer: ShortArray, size: Int) {
        val bytes = ByteArray(size * 2)
        for (i in 0 until size) {
            bytes[i * 2]     = (buffer[i].toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = (buffer[i].toInt() ushr 8 and 0xFF).toByte()
        }
        stream.write(bytes)
        dataSize += bytes.size
    }

    fun finish(): File {
        stream.flush()
        stream.close()
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(4)
            raf.write(intLE(36 + dataSize))
            raf.seek(40)
            raf.write(intLE(dataSize))
        }
        return file
    }

    private fun writeHeader() {
        val byteRate = sampleRate * channels * 2
        stream.write("RIFF".toByteArray())
        stream.write(intLE(0))              // tamanho total — corrigido em finish()
        stream.write("WAVE".toByteArray())
        stream.write("fmt ".toByteArray())
        stream.write(intLE(16))             // tamanho do bloco fmt
        stream.write(shortLE(1))            // PCM
        stream.write(shortLE(channels.toShort()))
        stream.write(intLE(sampleRate))
        stream.write(intLE(byteRate))
        stream.write(shortLE((channels * 2).toShort()))
        stream.write(shortLE(16))           // bits por sample
        stream.write("data".toByteArray())
        stream.write(intLE(0))              // tamanho dos dados — corrigido em finish()
    }

    private fun intLE(v: Int)   = byteArrayOf(v.toByte(), (v ushr 8).toByte(), (v ushr 16).toByte(), (v ushr 24).toByte())
    private fun shortLE(v: Short) = byteArrayOf(v.toByte(), (v.toInt() ushr 8).toByte())
}
