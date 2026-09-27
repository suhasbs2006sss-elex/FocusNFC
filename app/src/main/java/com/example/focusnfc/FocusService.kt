package com.example.focusnfc

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

class FocusService : Service() {

    companion object {
        var isSessionActive = false
        var blockedApps = setOf<String>()
        var remainingSeconds = 0L
        var endTimestamp = 0L
    }

    private var countDownTimer: CountDownTimer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val minutes = intent?.getIntExtra("DURATION_MINUTES", 25) ?: 25
        val appsArray = intent?.getStringArrayExtra("BLOCKED_APPS")

        val prefs = getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
        blockedApps = appsArray?.toSet() ?: prefs.getStringSet("saved_blocked_apps", setOf(
            "com.instagram.android", "com.google.android.youtube"
        )) ?: emptySet()

        startSession(minutes)
        return START_NOT_STICKY
    }

    private fun startSession(minutes: Int) {
        isSessionActive = true
        val totalMillis = minutes * 60 * 1000L
        endTimestamp = System.currentTimeMillis() + totalMillis

        startForegroundService()
        enableDND(true)

        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(totalMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                remainingSeconds = millisUntilFinished / 1000
            }

            override fun onFinish() {
                triggerCompletionVibration()
                stopSelf()
            }
        }.start()
    }

    private fun triggerCompletionVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator.vibrate(1000)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun enableDND(enable: Boolean) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && notificationManager.isNotificationPolicyAccessGranted) {
            val filter = if (enable) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL
            notificationManager.setInterruptionFilter(filter)
        }
    }

    private fun startForegroundService() {
        val channelId = "FocusChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Focus Mode Timer", NotificationManager.IMPORTANCE_DEFAULT).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Focus Session Active 🧠")
            .setContentText("Selected Apps Blocked • DND Active")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(endTimestamp)
            .setOngoing(true)
            .build()

        startForeground(101, notification)
    }

    override fun onDestroy() {
        isSessionActive = false
        countDownTimer?.cancel()
        enableDND(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
