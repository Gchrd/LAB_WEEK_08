package com.example.lab_week_08

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class NotificationService : Service() {

    private lateinit var notificationBuilder: NotificationCompat.Builder
    private lateinit var serviceHandler: Handler

    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        // Siapkan notif & start sebagai foreground
        notificationBuilder = startForegroundServiceInternal()

        // Siapkan thread khusus untuk pekerjaan service
        val handlerThread = HandlerThread("SecondThread").apply { start() }
        serviceHandler = Handler(handlerThread.looper)
    }

    // Buat & mulai foreground notification
    private fun startForegroundServiceInternal(): NotificationCompat.Builder {
        val pendingIntent = getPendingIntent()
        val channelId = createNotificationChannel()
        val builder = getNotificationBuilder(pendingIntent, channelId)

        startForeground(NOTIFICATION_ID, builder.build())
        return builder
    }

    private fun getPendingIntent(): PendingIntent {
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), flag
        )
    }

    private fun createNotificationChannel(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "001"
            val channelName = "001 Channel"
            val channelPriority = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, channelName, channelPriority)

            val manager = requireNotNull(
                ContextCompat.getSystemService(this, NotificationManager::class.java)
            )
            manager.createNotificationChannel(channel)
            channelId
        } else {
            "" // channel tidak diperlukan < API 26
        }

    private fun getNotificationBuilder(
        pendingIntent: PendingIntent,
        channelId: String
    ) = NotificationCompat.Builder(this, channelId)
        .setContentTitle("Second worker process is done")
        .setContentText("Check it out!")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentIntent(pendingIntent)
        .setTicker("Second worker process is done, check it out!")
        .setOngoing(true)

    // Dipanggil saat service dimulai (setelah startForeground())
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val returnValue = super.onStartCommand(intent, flags, startId)

        val id = intent?.getStringExtra(EXTRA_ID)
            ?: throw IllegalStateException("Channel ID must be provided")

        // Jalankan pekerjaan di thread service
        serviceHandler.post {
            countDownFromTenToZero(notificationBuilder)
            notifyCompletion(id)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return returnValue
    }

    // Update isi notif hitung mundur 10 → 0
    private fun countDownFromTenToZero(builder: NotificationCompat.Builder) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        for (i in 10 downTo 0) {
            Thread.sleep(1000L)
            builder.setContentText("$i seconds until last warning")
                .setSilent(true)
            manager.notify(NOTIFICATION_ID, builder.build())
        }
    }

    // Kabarin UI kalau selesai (via LiveData di Main Thread)
    private fun notifyCompletion(id: String) {
        Handler(Looper.getMainLooper()).post {
            mutableID.value = id
        }
    }

    companion object {
        const val NOTIFICATION_ID = 0xCA7
        const val EXTRA_ID = "Id"

        private val mutableID = MutableLiveData<String>()
        val trackingCompletion: LiveData<String> = mutableID
    }
}
