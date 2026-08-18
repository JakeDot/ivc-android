package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.ivccx.pqrsw.client.CommandResult
import com.aistudio.ivccx.pqrsw.client.IvcClient
import com.example.data.Message
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(
    val ivcClient: IvcClient = IvcClient()
) : ViewModel() {

    private val db get() = try { Firebase.firestore } catch (_: Throwable) { null }
    private val auth get() = try { Firebase.auth } catch (_: Throwable) { null }
    private val messagesCollection get() = db?.collection("messages")

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    val currentChannel: StateFlow<String> = ivcClient.currentChannel
    val topic: StateFlow<String?> = ivcClient.topic
    val nickname: StateFlow<String> = ivcClient.nickname

    init {
        ivcClient.connect()
        try {
            val currentUser = auth?.currentUser
            if (currentUser != null) {
                val name = currentUser.displayName ?: "Anonymous"
                ivcClient.setNickname(name)
            }
        } catch (_: Throwable) {}

        registerDefaultComservCommands()
        listenForMessages()
    }

    private fun registerDefaultComservCommands() {
        ivcClient.registerCommand("about", "Info about IVC client") { _, _ ->
            "IVC Client v1.0 - IRC-like application powered by IvcClient & COMSERV."
        }
        ivcClient.registerCommand("ping", "Ping COMSERV bot") { args, nick ->
            "Pong to $nick! ${if (args.isNotBlank()) "Args: $args" else ""}"
        }
    }

    private fun listenForMessages() {
        try {
            messagesCollection
                ?.orderBy("timestamp", Query.Direction.ASCENDING)
                ?.addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        val newMessages = snapshot.documents.mapNotNull { it.toObject(Message::class.java) }
                        _messages.value = newMessages
                    }
                }
        } catch (_: Throwable) {}
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val commandResult = ivcClient.processInput(text)
        handleCommandResult(commandResult)
    }

    private fun handleCommandResult(result: CommandResult) {
        val currentNick = ivcClient.nickname.value

        when (result) {
            is CommandResult.ChatMessage -> {
                if (result.text.isNotBlank()) {
                    postMessageToFirestore(
                        text = result.text,
                        senderName = currentNick
                    )
                }
            }

            is CommandResult.Action -> {
                postMessageToFirestore(
                    text = result.formattedMessage,
                    senderName = "*"
                )
            }

            is CommandResult.NickChange -> {
                val systemNotice = "* ${result.oldNick} is now known as ${result.newNick}"
                postMessageToFirestore(
                    text = systemNotice,
                    senderName = "*"
                )
                try {
                    val currentUser = auth?.currentUser
                    if (currentUser != null && db != null) {
                        viewModelScope.launch {
                            try {
                                db?.collection("users")?.document(currentUser.uid)
                                    ?.update("nickname", result.newNick)
                            } catch (_: Throwable) {}
                        }
                    }
                } catch (_: Throwable) {}
            }

            is CommandResult.JoinChannel -> {
                val notice = "* Joined channel ${result.newChannel}"
                postSystemMessageLocally(notice)
            }

            is CommandResult.TopicChange -> {
                val notice = "* Topic for ${result.channel} set to: ${result.newTopic}"
                postMessageToFirestore(
                    text = notice,
                    senderName = "*"
                )
            }

            is CommandResult.ShowTopic -> {
                val topicText = result.topic ?: "No topic set"
                val notice = "* Topic for ${result.channel}: $topicText"
                postSystemMessageLocally(notice)
            }

            is CommandResult.PrivateMessage -> {
                val notice = "-> *${result.target}* ${result.message}"
                postSystemMessageLocally(notice)
            }

            is CommandResult.ServiceResponse -> {
                postMessageToFirestore(
                    text = result.response,
                    senderName = result.serviceName
                )
            }

            is CommandResult.Help -> {
                postSystemMessageLocally(result.helpText)
            }

            is CommandResult.Clear -> {
                _messages.value = emptyList()
            }

            is CommandResult.Quit -> {
                val notice = "* Disconnected${if (result.reason != null) " (${result.reason})" else ""}"
                postSystemMessageLocally(notice)
                try {
                    auth?.signOut()
                } catch (_: Throwable) {}
            }

            is CommandResult.Error -> {
                postSystemMessageLocally("Error: ${result.message}")
            }
        }
    }

    private fun postSystemMessageLocally(text: String) {
        val message = Message(
            id = UUID.randomUUID().toString(),
            text = text,
            senderId = "system",
            senderName = "System",
            timestamp = System.currentTimeMillis()
        )
        _messages.value = _messages.value + message
    }

    private fun postMessageToFirestore(text: String, senderName: String) {
        val senderId = try { auth?.currentUser?.uid ?: "anonymous" } catch (_: Throwable) { "anonymous" }
        val message = Message(
            id = UUID.randomUUID().toString(),
            text = text,
            senderId = senderId,
            senderName = senderName,
            timestamp = System.currentTimeMillis()
        )

        val col = messagesCollection
        if (col != null) {
            try {
                viewModelScope.launch {
                    try {
                        col.document(message.id).set(message)
                    } catch (_: Throwable) {
                        _messages.value = _messages.value + message
                    }
                }
            } catch (_: Throwable) {
                _messages.value = _messages.value + message
            }
        } else {
            _messages.value = _messages.value + message
        }
    }
}
