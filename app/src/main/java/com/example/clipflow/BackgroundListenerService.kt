package com.example.clipflow

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.clipflow.helper.NetworkTransferManager
import com.example.clipflow.repository.ClipFlowRepository

class BackgroundListenerService : Service() {

    private var networkManager: NetworkTransferManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val bgChannel = NotificationChannel("CLIPFLOW_BG", "ClipFlow Background", NotificationManager.IMPORTANCE_LOW)
            val alertChannel = NotificationChannel("CLIPFLOW_ALERTS", "ClipFlow Thông Báo", NotificationManager.IMPORTANCE_HIGH)
            manager?.createNotificationChannel(bgChannel)
            manager?.createNotificationChannel(alertChannel)
        }

        val notification = NotificationCompat.Builder(this, "CLIPFLOW_BG")
            .setContentTitle("ClipFlow đang chạy ngầm")
            .setContentText("Sẵn sàng nhận dữ liệu từ PC")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .build()
// startForeground và notification để chạy dưới nền
        startForeground(1, notification)

        networkManager = NetworkTransferManager(this, ClipFlowRepository(this)) { _, _, _ -> }
        networkManager?.startDownloadListener()
    }
}