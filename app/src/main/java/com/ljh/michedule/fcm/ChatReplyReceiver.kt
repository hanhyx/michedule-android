package com.ljh.michedule.fcm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import com.ljh.michedule.MicheduleApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ChatReplyReceiver : BroadcastReceiver() {

    companion object {
        const val KEY_TEXT_REPLY = "key_text_reply"
        const val EXTRA_ROOM_CODE = "extra_room_code"
        const val EXTRA_PARTNER_CODE = "extra_partner_code"
        const val ACTION_REPLY = "com.ljh.michedule.ACTION_CHAT_REPLY"
        const val ACTION_DATE_PLAN_RESPOND = "com.ljh.michedule.ACTION_DATE_PLAN_RESPOND"
        const val EXTRA_DATE = "extra_date"
        const val EXTRA_RESPONSE = "extra_response"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? MicheduleApp ?: return

        when (intent.action) {
            ACTION_REPLY -> handleChatReply(context, intent, app)
            ACTION_DATE_PLAN_RESPOND -> handleDatePlanResponse(context, intent, app)
        }
    }

    private fun handleChatReply(context: Context, intent: Intent, app: MicheduleApp) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInput?.getCharSequence(KEY_TEXT_REPLY)?.toString() ?: return
        val roomCode = intent.getStringExtra(EXTRA_ROOM_CODE) ?: return
        val partnerCode = intent.getStringExtra(EXTRA_PARTNER_CODE) ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val myCode = app.prefsManager.ensureMyCode()
                val myName = app.prefsManager.myName.first().ifBlank { "상대방" }
                app.chatRepository.sendMessage(roomCode, myCode, replyText)
                app.chatRepository.sendChatPush(partnerCode, myName, replyText, "text")
                Log.d("ChatReply", "Quick reply sent: $replyText")
            } catch (e: Exception) {
                Log.e("ChatReply", "Failed to send reply", e)
            }
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(7777)
    }

    private fun handleDatePlanResponse(context: Context, intent: Intent, app: MicheduleApp) {
        val date = intent.getStringExtra(EXTRA_DATE) ?: return
        val response = intent.getStringExtra(EXTRA_RESPONSE) ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val localDate = java.time.LocalDate.parse(date)
                app.repository.respondToDatePlan(localDate, response)
                app.triggerUpload()
                app.sendDatePlanResponsePush(date, response)
                Log.d("ChatReply", "Date plan response: $response for $date")
            } catch (e: Exception) {
                Log.e("ChatReply", "Failed to respond to date plan", e)
            }
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(date.hashCode())
    }
}
