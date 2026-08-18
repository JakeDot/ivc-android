package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Message
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val messagesCollection = db.collection("messages")

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    init {
        listenForMessages()
    }

    private fun listenForMessages() {
        messagesCollection
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val newMessages = snapshot.documents.mapNotNull { it.toObject(Message::class.java) }
                    _messages.value = newMessages
                }
            }
    }

    fun sendMessage(text: String) {
        val currentUser = auth.currentUser ?: return
        val message = Message(
            id = UUID.randomUUID().toString(),
            text = text,
            senderId = currentUser.uid,
            senderName = currentUser.displayName ?: "Anonymous",
            timestamp = System.currentTimeMillis()
        )
        
        viewModelScope.launch {
            try {
                messagesCollection.document(message.id).set(message)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}
