package com.ljh.michedule.ui.chat

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ljh.michedule.MicheduleApp
import com.ljh.michedule.data.db.ChatMessageEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch



data class ChatUiState(
    val messages: List<ChatMessageEntity> = emptyList(),
    val myCode: String = "",
    val myName: String = "",
    val myPhotoUri: String = "",
    val partnerCode: String = "",
    val partnerName: String = "",
    val partnerPhotoUri: String = "",
    val connectionMutual: Boolean = false,
    val roomCode: String = "",
    val isLoading: Boolean = true
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MicheduleApp
    private val chatRepo = app.chatRepository
    private val prefs = app.prefsManager

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.myCode.collect { code ->
                _uiState.update { it.copy(myCode = code) }
                refreshRoom()
            }
        }
        viewModelScope.launch {
            prefs.myName.collect { name -> _uiState.update { it.copy(myName = name) } }
        }
        viewModelScope.launch {
            prefs.myPhotoUri.collect { uri -> _uiState.update { it.copy(myPhotoUri = uri) } }
        }
        viewModelScope.launch {
            prefs.partnerCode.collect { code ->
                _uiState.update { it.copy(partnerCode = code) }
                refreshRoom()
            }
        }
        viewModelScope.launch {
            prefs.partnerName.collect { name -> _uiState.update { it.copy(partnerName = name) } }
        }
        viewModelScope.launch {
            prefs.partnerPhotoUri.collect { uri -> _uiState.update { it.copy(partnerPhotoUri = uri) } }
        }
        viewModelScope.launch {
            prefs.connectionMutual.collect { mutual -> _uiState.update { it.copy(connectionMutual = mutual) } }
        }
    }

    private var messagesJob: kotlinx.coroutines.Job? = null

    private fun refreshRoom() {
        val state = _uiState.value
        if (state.myCode.isBlank() || state.partnerCode.isBlank()) return
        val roomCode = chatRepo.getRoomCode(state.myCode, state.partnerCode)
        _uiState.update { it.copy(roomCode = roomCode) }

        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            chatRepo.syncHistory(roomCode)
            _uiState.update { it.copy(isLoading = false) }

            chatRepo.subscribeToChat(roomCode, viewModelScope)

            chatRepo.getMessages(roomCode).collect { msgs ->
                _uiState.update { it.copy(messages = msgs) }
                if (msgs.isNotEmpty() && app.isChatScreenActive) {
                    prefs.setChatLastReadAt(msgs.first().createdAt)
                }
            }
        }
    }

    fun sendMessage(content: String) {
        val state = _uiState.value
        if (content.isBlank() || state.roomCode.isBlank()) return
        viewModelScope.launch {
            val sent = chatRepo.sendMessage(state.roomCode, state.myCode, content)
            chatRepo.sendChatPush(state.partnerCode, state.myName.ifBlank { "상대방" }, content, "text", sent.id, sent.createdAt)
        }
    }

    fun sendImage(uri: Uri) {
        val state = _uiState.value
        if (state.roomCode.isBlank()) return
        viewModelScope.launch {
            val sent = chatRepo.sendImage(state.roomCode, state.myCode, uri)
            if (sent != null) {
                chatRepo.sendChatPush(state.partnerCode, state.myName.ifBlank { "상대방" }, "", "image", sent.id, sent.createdAt)
            }
        }
    }

    fun addReaction(messageId: String, emoji: String) {
        val state = _uiState.value
        if (state.roomCode.isBlank()) return
        viewModelScope.launch {
            chatRepo.addReaction(messageId, state.roomCode, emoji, state.myCode)
        }
    }

    fun deleteMessage(messageId: String) {
        val state = _uiState.value
        if (state.roomCode.isBlank()) return
        viewModelScope.launch {
            chatRepo.deleteMessage(messageId, state.roomCode)
        }
    }

    override fun onCleared() {
        chatRepo.unsubscribe()
        super.onCleared()
    }
}
