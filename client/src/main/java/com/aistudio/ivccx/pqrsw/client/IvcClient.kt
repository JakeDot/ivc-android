package com.aistudio.ivccx.pqrsw.client

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson
import java.util.concurrent.TimeUnit

typealias CommandHandler = (args: String, senderNick: String) -> String

data class RegisteredCommand(
    val name: String,
    val description: String,
    val handler: CommandHandler
)

sealed interface CommandResult {
    data class ChatMessage(val text: String) : CommandResult
    data class Action(val actionText: String, val formattedMessage: String) : CommandResult
    data class NickChange(val oldNick: String, val newNick: String) : CommandResult
    data class JoinChannel(val oldChannel: String, val newChannel: String) : CommandResult
    data class TopicChange(val channel: String, val newTopic: String) : CommandResult
    data class ShowTopic(val channel: String, val topic: String?) : CommandResult
    data class PrivateMessage(val target: String, val message: String) : CommandResult
    data class ServiceResponse(val serviceName: String, val response: String) : CommandResult
    data class Help(val helpText: String) : CommandResult
    object Clear : CommandResult
    data class Quit(val reason: String?) : CommandResult
    data class Error(val message: String) : CommandResult
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

class IvcClient {

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var identity: IvcIdentity? = null
    private var baseUrl: String? = null
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val gson = Gson()
    private var eventSource: EventSource? = null
    private var sseMessageCallback: ((String) -> Unit)? = null

    private val _nickname = MutableStateFlow("Anonymous")
    val nickname: StateFlow<String> = _nickname.asStateFlow()

    private val _currentChannel = MutableStateFlow("£general")
    val currentChannel: StateFlow<String> = _currentChannel.asStateFlow()

    private val _topic = MutableStateFlow<String?>(null)
    val topic: StateFlow<String?> = _topic.asStateFlow()

    private val _backendProtocol = MutableStateFlow<String?>(null)
    val backendProtocol: StateFlow<String?> = _backendProtocol.asStateFlow()

    private val registeredCommands = mutableMapOf<String, RegisteredCommand>()

    fun onSseMessage(callback: (String) -> Unit) {
        sseMessageCallback = callback
    }

    fun connect(serverUrl: String = "ivc+https://IVC.cx", fallbackUrl: String = "ivc+irc://IVC.cx") {
        _connectionState.value = ConnectionState.CONNECTING

        // Parse backend protocol from URLs like ivc+https://...
        var selectedUrl = serverUrl
        var protocolMatch = Regex("^ivc\\+([a-zA-Z0-9]+)://(.*)").find(serverUrl)
        if (protocolMatch != null) {
            _backendProtocol.value = protocolMatch.groupValues[1]
            baseUrl = protocolMatch.groupValues[1] + "://" + protocolMatch.groupValues[2]
        } else {
            val fallbackMatch = Regex("^ivc\\+([a-zA-Z0-9]+)://(.*)").find(fallbackUrl)
            _backendProtocol.value = fallbackMatch?.groupValues?.get(1)
            baseUrl = fallbackMatch?.let { it.groupValues[1] + "://" + it.groupValues[2] }
        }

        if (baseUrl != null) {
            baseUrl = baseUrl!!.replace(Regex("/$"), "")
        }

        if (identity == null) {
            identity = IvcIdentity(_nickname.value)
        }

        // Connection logic (SSE)
        val sseUrl = "$baseUrl/api/ivc/stream"
        val request = Request.Builder()
            .url(sseUrl)
            .header("Accept", "text/event-stream")
            .build()

        val factory = EventSources.createFactory(httpClient)
        eventSource = factory.newEventSource(request, object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: okhttp3.Response) {
                _connectionState.value = ConnectionState.CONNECTED
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                sseMessageCallback?.invoke(data)
            }

            override fun onClosed(eventSource: EventSource) {
                _connectionState.value = ConnectionState.DISCONNECTED
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                _connectionState.value = ConnectionState.DISCONNECTED
                t?.printStackTrace()
            }
        })
    }

    fun disconnect() {
        eventSource?.cancel()
        eventSource = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    fun sendMessage(targetChannel: String, message: String) {
        val base = baseUrl ?: throw IllegalStateException("Not connected")
        val ident = identity ?: throw IllegalStateException("Identity not set")

        val payload = mapOf("msg" to message)
        val bodyString = gson.toJson(payload)

        val path = "/" + targetChannel.replace("#", "%23").replace("£", "%23")
        val authHeaders = ident.generateAuthHeaders("POST", path, bodyString)

        val requestBody = bodyString.toRequestBody("application/json".toMediaType())
        val reqBuilder = Request.Builder()
            .url(base + path)
            .post(requestBody)

        for ((key, value) in authHeaders) {
            reqBuilder.header(key, value)
        }

        val request = reqBuilder.build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("HTTP ${response.code}: ${response.body?.string()}")
            }
        }
    }

    fun putData(endpoint: String, payload: String): Boolean {
        // Simulated HTTP PUT
        return true
    }

    fun deleteData(endpoint: String): Boolean {
        // Simulated HTTP DELETE
        return true
    }

    fun setNickname(newNick: String) {
        val trimmed = newNick.trim()
        if (trimmed.isNotBlank()) {
            _nickname.value = trimmed
            if (_connectionState.value != ConnectionState.CONNECTED) {
                identity = IvcIdentity(trimmed)
            }
        }
    }

    fun setChannel(channel: String) {
        val trimmed = channel.trim()
        if (trimmed.isNotBlank()) {
            _currentChannel.value = if (trimmed.startsWith("£")) trimmed else "£$trimmed"
        }
    }

    fun setTopic(newTopic: String?) {
        _topic.value = newTopic?.trim()
    }

    fun registerCommand(name: String, description: String, handler: CommandHandler) {
        val cleanName = name.trim().removePrefix("/").lowercase()
        require(cleanName.isNotBlank()) { "Command name cannot be blank" }
        registeredCommands[cleanName] = RegisteredCommand(cleanName, description, handler)
    }

    fun unregisterCommand(name: String) {
        val cleanName = name.trim().removePrefix("/").lowercase()
        registeredCommands.remove(cleanName)
    }

    fun getRegisteredCommands(): List<RegisteredCommand> = registeredCommands.values.toList()

    private fun handleComservCommand(args: String): CommandResult {
        val trimmedArgs = args.trim()
        if (trimmedArgs.isBlank() || trimmedArgs.equals("help", ignoreCase = true) || trimmedArgs.equals("list", ignoreCase = true)) {
            val cmds = registeredCommands.values
            val response = if (cmds.isEmpty()) {
                "COMSERV: No custom commands currently registered."
            } else {
                val cmdLines = cmds.joinToString("\n") { "  /${it.name} - ${it.description}" }
                "COMSERV registered commands:\n$cmdLines\n\nUsage: /<command> [args] or /comserv <command> [args]"
            }
            return CommandResult.ServiceResponse("COMSERV", response)
        }

        val parts = trimmedArgs.split("\\s+".toRegex(), limit = 2)
        var cmdName = parts[0].lowercase()
        var cmdArgs = if (parts.size > 1) parts[1].trim() else ""

        if (cmdName == "exec" && cmdArgs.isNotBlank()) {
            val execParts = cmdArgs.split("\\s+".toRegex(), limit = 2)
            cmdName = execParts[0].lowercase()
            cmdArgs = if (execParts.size > 1) execParts[1].trim() else ""
        }

        val regCmd = registeredCommands[cmdName]
        return if (regCmd != null) {
            val resultText = regCmd.handler(cmdArgs, _nickname.value)
            CommandResult.ServiceResponse("COMSERV", resultText)
        } else {
            CommandResult.ServiceResponse("COMSERV", "Error: Unknown command '$cmdName'. Use '/comserv list' to see available commands.")
        }
    }

    fun processInput(input: String): CommandResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return CommandResult.ChatMessage("")
        }

        if (!trimmed.startsWith("/")) {
            return CommandResult.ChatMessage(trimmed)
        }

        // Process slash command
        val parts = trimmed.substring(1).split("\\s+".toRegex(), limit = 2)
        val command = parts[0].lowercase()
        val args = if (parts.size > 1) parts[1].trim() else ""

        // Check if command is a registered custom command
        val customCmd = registeredCommands[command]
        if (customCmd != null) {
            val response = customCmd.handler(args, _nickname.value)
            return CommandResult.ServiceResponse("COMSERV", response)
        }

        return when (command) {
            "nick", "nickname" -> {
                if (args.isBlank()) {
                    CommandResult.Error("Usage: /nick <new_nickname>")
                } else {
                    val oldNick = _nickname.value
                    val newNick = args
                    _nickname.value = newNick
                    CommandResult.NickChange(oldNick, newNick)
                }
            }

            "me" -> {
                if (args.isBlank()) {
                    CommandResult.Error("Usage: /me <action>")
                } else {
                    val currentNick = _nickname.value
                    CommandResult.Action(args, "* $currentNick $args")
                }
            }

            "join", "j" -> {
                if (args.isBlank()) {
                    CommandResult.Error("Usage: /join <£channel>")
                } else {
                    val oldChannel = _currentChannel.value
                    val newChannel = if (args.startsWith("£")) args else "£$args"
                    _currentChannel.value = newChannel
                    CommandResult.JoinChannel(oldChannel, newChannel)
                }
            }

            "topic", "t" -> {
                val channel = _currentChannel.value
                if (args.isBlank()) {
                    CommandResult.ShowTopic(channel, _topic.value)
                } else {
                    _topic.value = args
                    CommandResult.TopicChange(channel, args)
                }
            }

            "clear", "cls" -> {
                CommandResult.Clear
            }

            "comserv" -> {
                handleComservCommand(args)
            }

            "help", "h", "?" -> {
                val helpText = """
                    Available IRC commands:
                    /nick <new_nick> (alias: /nickname) - Change your nickname
                    /me <action> - Perform an action
                    /join <£channel> (alias: /j) - Join a channel
                    /topic [new_topic] (alias: /t) - Display or set the channel topic
                    /comserv [command] - COMSERV bot & registered commands
                    /msg <user> <message> (alias: /query) - Send a private message
                    /clear (alias: /cls) - Clear the screen
                    /quit [reason] (alias: /q) - Disconnect from the server
                    /help (alias: /h, /?) - Show this help message
                """.trimIndent()
                CommandResult.Help(helpText)
            }

            "quit", "q" -> {
                disconnect()
                CommandResult.Quit(if (args.isBlank()) null else args)
            }

            "msg", "query" -> {
                val msgParts = args.split("\\s+".toRegex(), limit = 2)
                if (msgParts.size < 2 || msgParts[0].isBlank() || msgParts[1].isBlank()) {
                    CommandResult.Error("Usage: /msg <user> <message>")
                } else {
                    val target = msgParts[0]
                    val message = msgParts[1]
                    if (target.equals("COMSERV", ignoreCase = true)) {
                        handleComservCommand(message)
                    } else {
                        CommandResult.PrivateMessage(target, message)
                    }
                }
            }

            else -> {
                CommandResult.Error("Unknown command: /$command. Type /help or /comserv for available commands.")
            }
        }
    }
}
