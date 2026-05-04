package com.showraw.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

/**
 * Mantém o processo vivo durante gravações longas e exibe notificação persistente.
 * Segura um PARTIAL_WAKE_LOCK para impedir que o CPU entre em sleep.
 *
 * Controlado por intents via startService():
 *   ACTION_START  — inicia foreground + wake lock
 *   ACTION_UPDATE — atualiza texto da notificação (tempo / estado)
 *   ACTION_STOP   — remove foreground + libera wake lock + para o serviço
 */
class RecordingService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val isPaused = intent.getBooleanExtra(EXTRA_PAUSED, false)
                val elapsed  = intent.getStringExtra(EXTRA_ELAPSED) ?: "00:00"
                acquireWakeLock()
                val notification = buildNotification(isPaused, elapsed)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                } else {
                    // API 29: FOREGROUND_SERVICE_TYPE_MICROPHONE not yet available
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
                }
            }
            ACTION_UPDATE -> {
                val isPaused = intent.getBooleanExtra(EXTRA_PAUSED, false)
                val elapsed  = intent.getStringExtra(EXTRA_ELAPSED) ?: "00:00"
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, buildNotification(isPaused, elapsed))
            }
            ACTION_STOP -> {
                releaseWakeLock()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "showraw:recording")
            .also { it.acquire(6 * 60 * 60 * 1000L) }  // máximo 6 horas
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun buildNotification(isPaused: Boolean, elapsed: String): Notification {
        val title = if (isPaused) "⏸ Gravação pausada" else "● Gravando"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(title)
            .setContentText(elapsed)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Gravação em andamento",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START  = "showraw.ACTION_START"
        const val ACTION_UPDATE = "showraw.ACTION_UPDATE"
        const val ACTION_STOP   = "showraw.ACTION_STOP"
        const val EXTRA_PAUSED  = "paused"
        const val EXTRA_ELAPSED = "elapsed"
        private const val CHANNEL_ID      = "rec_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
