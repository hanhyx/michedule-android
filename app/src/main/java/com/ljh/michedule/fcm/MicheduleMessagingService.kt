package com.ljh.michedule.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ljh.michedule.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
        val app = applicationContext as? com.ljh.michedule.MicheduleApp ?: return
        val prefs = app.prefsManager

        val type = message.data["type"] ?: return
        CoroutineScope(Dispatchers.IO).launch {
            when (type) {
                "date_plan" -> {
                    if (prefs.pushDatePlanEnabled.first()) {
                        showDatePlanNotification(message.data)
                    }
                }
                "date_plan_response" -> {
                    if (prefs.pushDatePlanResponseEnabled.first()) {
                        showDatePlanResponseNotification(message.data)
                    }
                }
                "chat" -> {
                    val roomCode = message.data["room_code"] ?: ""
                    val senderCode = message.data["sender_code"] ?: ""
                    val content = message.data["content"] ?: ""
                    val msgId = message.data["msg_id"] ?: ""
                    val createdAtStr = message.data["created_at"] ?: ""
                    val createdAt = createdAtStr.toLongOrNull() ?: System.currentTimeMillis()
                    val msgType = message.data["message_type"] ?: "text"
                    if (roomCode.isNotBlank() && senderCode.isNotBlank() && msgId.isNotBlank()) {
                        app.chatRepository.insertMessageFromPush(
                            msgId = msgId,
                            roomCode = roomCode,
                            senderCode = senderCode,
                            content = content,
                            messageType = msgType,
                            createdAt = createdAt
                        )
                    }
                    if (prefs.pushChatEnabled.first() && !app.isChatScreenActive) {
                        showChatNotification(message.data)
                    }
                }
            }
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

        val acceptIntent = Intent(this, ChatReplyReceiver::class.java).apply {
            action = ChatReplyReceiver.ACTION_DATE_PLAN_RESPOND
            putExtra(ChatReplyReceiver.EXTRA_DATE, date)
            putExtra(ChatReplyReceiver.EXTRA_RESPONSE, "accepted")
        }
        val acceptPi = PendingIntent.getBroadcast(
            this, "accept_$date".hashCode(), acceptIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val thinkIntent = Intent(this, ChatReplyReceiver::class.java).apply {
            action = ChatReplyReceiver.ACTION_DATE_PLAN_RESPOND
            putExtra(ChatReplyReceiver.EXTRA_DATE, date)
            putExtra(ChatReplyReceiver.EXTRA_RESPONSE, "thinking")
        }
        val thinkPi = PendingIntent.getBroadcast(
            this, "think_$date".hashCode(), thinkIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_DATE_PLAN)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("💕 ${senderName}님의 만나요!")
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 300, 150, 300))
            .addAction(R.drawable.ic_launcher_foreground, "좋아! 💕", acceptPi)
            .addAction(R.drawable.ic_launcher_foreground, "생각해볼게 💭", thinkPi)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(date.hashCode(), notification)
    }

    private fun showDatePlanResponseNotification(data: Map<String, String>) {
        val senderName = data["sender_name"] ?: "상대방"
        val date = data["date"] ?: ""
        val response = data["response"] ?: ""

        createChannel()

        val body = when (response) {
            "accepted" -> "${senderName}님이 만나요를 수락했어요! 💕"
            "thinking" -> "${senderName}님이 생각해본다고 해요 💭"
            else -> "${senderName}님이 만나요에 응답했어요"
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_DATE_PLAN)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("💕 만나요 응답")
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 300, 150, 300))
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify("response_$date".hashCode(), notification)
    }

    private fun showChatNotification(data: Map<String, String>) {
        val senderName = data["sender_name"] ?: "상대방"
        val content = data["content"] ?: "새 메시지"
        val roomCode = data["room_code"] ?: ""
        val partnerCode = data["sender_code"] ?: ""

        createChatChannel()

        val remoteInput = RemoteInput.Builder(ChatReplyReceiver.KEY_TEXT_REPLY)
            .setLabel("답장")
            .build()

        val replyIntent = Intent(this, ChatReplyReceiver::class.java).apply {
            action = ChatReplyReceiver.ACTION_REPLY
            putExtra(ChatReplyReceiver.EXTRA_ROOM_CODE, roomCode)
            putExtra(ChatReplyReceiver.EXTRA_PARTNER_CODE, partnerCode)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            this, 7777, replyIntent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_launcher_foreground, "답장", replyPendingIntent
        ).addRemoteInput(remoteInput).build()

        val notification = NotificationCompat.Builder(this, CHANNEL_CHAT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("💬 $senderName")
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 200, 100, 200))
            .addAction(replyAction)
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
