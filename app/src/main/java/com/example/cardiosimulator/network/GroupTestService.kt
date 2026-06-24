package com.example.cardiosimulator.network

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.cardiosimulator.MainActivity
import com.example.cardiosimulator.R
import com.example.cardiosimulator.domain.ExamResult
import com.example.cardiosimulator.domain.Test
import java.io.File

class GroupTestService : Service() {

    private var server: GroupTestServer? = null
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): GroupTestService = this@GroupTestService
    }

    override fun onBind(intent: Intent): IBinder = binder

    fun startServer(
        port: Int,
        generateTest: (String, String) -> Test,
        resolveImage: (String) -> File?,
        onResult: (ExamResult) -> Unit
    ) {
        if (server != null) return

        server = GroupTestServer(port, generateTest, resolveImage, onResult)
        server?.start()

        startForeground(NOTIFICATION_ID, createNotification(), 
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
    }

    fun stopServer() {
        server?.stop()
        server = null
        stopForeground(true)
        stopSelf()
    }

    fun getParticipants() = server?.getParticipants() ?: emptyList()

    private fun createNotification(): Notification {
        val channelId = "group_test_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Group Test Server", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Group Test Session")
            .setContentText("Server is running...")
            .setSmallIcon(R.mipmap.app_logo)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
