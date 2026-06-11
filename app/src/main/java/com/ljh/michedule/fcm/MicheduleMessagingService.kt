package com.ljh.michedule.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ljh.michedule.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MicheduleMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCM"
        const val CHANNEL_DATE_PLAN = "michedule_date_plan"
        const val CHANNEL_CHAT = "michedule_chat"
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "New FCM token: ${token.take(20)}...")
        val app = applicationContext as? com.ljh.michedule.MicheduleApp ?: return
        CoroutineScope(Dispatchers.IO).launch {
            app.prefsManager.setFcmToken(token)
            app.supabaseSync?.uploadCurrentData()
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "Message received: ${message.data}")

        val type = message.data["type"] ?: return
        when (type) {
            "date_plan" -> showDatePlanNotification(message.data)
            "chat" -> showChatNotification(message.data)
        }
    }

    private fun showDatePlanNotification(data: Map<String, String>) {
        val senderName = data["sender_name"] ?: "상대방"
        val date = data["date"] ?: ""
        val memo = data["memo"] ?: ""

        val displayDate = if (date.length >= 10) {
            val parts = date.split("-")
            if (parts.size == 3) "${parts[1].toIntOrNull() ?: parts[1]}월 ${parts[2].toIntOrNull() ?: parts[2]}일"
            else date
        } else date

        createChannel()

        val body = if (memo.isNotBlank()) {
            "${displayDate}에 만나요! 📝 $memo"
        } else {
            "${displayDate}에 만나자고 해요! 💕"
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_DATE_PLAN)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("💕 ${senderName}님의 만나요!")
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 300, 150, 300))
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(date.hashCode(), notification)
    }

    private fun showChatNotification(data: Map<String, String>) {
        val senderName = data["sender_name"] ?: "상대방"
        val content = data["content"] ?: "새 메시지"

        createChatChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_CHAT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("💬 $senderName")
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 200, 100, 200))
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(7777, notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_DATE_PLAN,
                "만나요 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "상대방이 만나요를 설정했을 때 알림"
                enableVibration(true)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun createChatChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_CHAT,
                "채팅 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "채팅 메시지 수신 알림"
                enableVibration(true)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
