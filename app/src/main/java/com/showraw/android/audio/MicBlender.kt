package com.showraw.android.audio

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

enum class MicOrientation { FRONT, REAR, UNKNOWN }

/**
 * Monitora orientação do celular via acelerômetro e nível RMS do sinal
 * para detectar quando o usuário girou o dispositivo (null point).
 *
 * Não expõe streams individuais por cápsula (limitação Android),
 * mas sinaliza qual orientação está sendo priorizada.
 */
class MicBlender(private val sensorManager: SensorManager) : SensorEventListener {

    var currentOrientation: MicOrientation = MicOrientation.UNKNOWN
        private set

    private var lastRms = 0f
    private var lastRmsTimestamp = 0L
    var onOrientationChanged: ((MicOrientation) -> Unit)? = null

    fun start() {
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    /** Chamado a cada ~50ms com o RMS atual do buffer de áudio. */
    fun feedRms(rms: Float) {
        val now  = System.currentTimeMillis()
        val drop = lastRms > 0f && rms < lastRms * 0.5f && (now - lastRmsTimestamp) < 100
        if (drop) {
            // Queda de > 6dB em < 100ms — possível null point
        }
        lastRms = rms
        lastRmsTimestamp = now
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val (x, _, z) = event.values
        val orientation = when {
            z > 7f  -> MicOrientation.FRONT  // Face up / normal portrait
            z < -7f -> MicOrientation.REAR   // Face down / rotated
            else    -> MicOrientation.UNKNOWN
        }
        if (orientation != currentOrientation) {
            currentOrientation = orientation
            onOrientationChanged?.invoke(orientation)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    companion object {
        fun computeRms(buffer: ShortArray, size: Int): Float {
            var sum = 0.0
            for (i in 0 until size) sum += (buffer[i] / 32768.0).let { it * it }
            return sqrt(sum / size).toFloat()
        }
    }
}
