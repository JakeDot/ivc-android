package com.aistudio.ivccx.pqrsw.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IvcClientTest {

    private lateinit var client: IvcClient

    @Before
    fun setUp() {
        client = IvcClient()
    }

    @Test
    fun testConnectionStateLifecycle() {
        assertEquals(ConnectionState.DISCONNECTED, client.connectionState.value)
        client.connect()
        assertEquals(ConnectionState.CONNECTED, client.connectionState.value)
        client.disconnect()
        assertEquals(ConnectionState.DISCONNECTED, client.connectionState.value)
    }

    @Test
    fun testSetNicknameDirectly() {
        assertEquals("Anonymous", client.nickname.value)
        client.setNickname("Alice")
        assertEquals("Alice", client.nickname.value)
    }

    @Test
    fun testNickCommandSuccess() {
        val result = client.processInput("/nick Bob")
        assertTrue(result is CommandResult.NickChange)
        val nickChange = result as CommandResult.NickChange
        assertEquals("Anonymous", nickChange.oldNick)
        assertEquals("Bob", nickChange.newNick)
        assertEquals("Bob", client.nickname.value)
    }

    @Test
    fun testNicknameAliasSuccess() {
        val result = client.processInput("/nickname Charlie")
        assertTrue(result is CommandResult.NickChange)
        val nickChange = result as CommandResult.NickChange
        assertEquals("Anonymous", nickChange.oldNick)
        assertEquals("Charlie", nickChange.newNick)
        assertEquals("Charlie", client.nickname.value)
    }

    @Test
    fun testNickCommandMissingArg() {
        val result = client.processInput("/nick")
        assertTrue(result is CommandResult.Error)
        assertEquals("Anonymous", client.nickname.value)
    }

    @Test
    fun testActionCommand() {
        client.setNickname("Alice")
        val result = client.processInput("/me dances")
        assertTrue(result is CommandResult.Action)
        val action = result as CommandResult.Action
        assertEquals("dances", action.actionText)
        assertEquals("* Alice dances", action.formattedMessage)
    }

    @Test
    fun testJoinChannelCommandAndAlias() {
        val result1 = client.processInput("/join £android")
        assertTrue(result1 is CommandResult.JoinChannel)
        val join1 = result1 as CommandResult.JoinChannel
        assertEquals("£general", join1.oldChannel)
        assertEquals("£android", join1.newChannel)
        assertEquals("£android", client.currentChannel.value)

        val result2 = client.processInput("/j kotlin")
        assertTrue(result2 is CommandResult.JoinChannel)
        val join2 = result2 as CommandResult.JoinChannel
        assertEquals("£android", join2.oldChannel)
        assertEquals("£kotlin", join2.newChannel)
        assertEquals("£kotlin", client.currentChannel.value)
    }

    @Test
    fun testTopicCommandAndShowTopic() {
        val showResult1 = client.processInput("/topic")
        assertTrue(showResult1 is CommandResult.ShowTopic)
        val show1 = showResult1 as CommandResult.ShowTopic
        assertEquals("£general", show1.channel)
        assertEquals(null, show1.topic)

        val setTopicResult = client.processInput("/topic Welcome to IVC chat!")
        assertTrue(setTopicResult is CommandResult.TopicChange)
        val topicChange = setTopicResult as CommandResult.TopicChange
        assertEquals("£general", topicChange.channel)
        assertEquals("Welcome to IVC chat!", topicChange.newTopic)
        assertEquals("Welcome to IVC chat!", client.topic.value)

        val showResult2 = client.processInput("/t")
        assertTrue(showResult2 is CommandResult.ShowTopic)
        val show2 = showResult2 as CommandResult.ShowTopic
        assertEquals("Welcome to IVC chat!", show2.topic)
    }

    @Test
    fun testClearCommandAndAlias() {
        val result1 = client.processInput("/clear")
        assertEquals(CommandResult.Clear, result1)

        val result2 = client.processInput("/cls")
        assertEquals(CommandResult.Clear, result2)
    }

    @Test
    fun testHelpCommandAndAliases() {
        val result1 = client.processInput("/help")
        assertTrue(result1 is CommandResult.Help)

        val result2 = client.processInput("/h")
        assertTrue(result2 is CommandResult.Help)

        val result3 = client.processInput("/?")
        assertTrue(result3 is CommandResult.Help)
    }

    @Test
    fun testQuitCommandAndAlias() {
        client.connect()
        assertEquals(ConnectionState.CONNECTED, client.connectionState.value)

        val result = client.processInput("/q Bye everyone!")
        assertTrue(result is CommandResult.Quit)
        val quit = result as CommandResult.Quit
        assertEquals("Bye everyone!", quit.reason)
        assertEquals(ConnectionState.DISCONNECTED, client.connectionState.value)
    }

    @Test
    fun testPrivateMessageCommandAndAlias() {
        val result1 = client.processInput("/msg Bob Hello Bob!")
        assertTrue(result1 is CommandResult.PrivateMessage)
        val msg1 = result1 as CommandResult.PrivateMessage
        assertEquals("Bob", msg1.target)
        assertEquals("Hello Bob!", msg1.message)

        val result2 = client.processInput("/query Alice Hi Alice!")
        assertTrue(result2 is CommandResult.PrivateMessage)
        val msg2 = result2 as CommandResult.PrivateMessage
        assertEquals("Alice", msg2.target)
        assertEquals("Hi Alice!", msg2.message)
    }

    @Test
    fun testCommandRegistrationAndDirectExecution() {
        client.setNickname("Tester")
        client.registerCommand("ping", "Responds with pong") { args, nick ->
            "Pong to $nick! Args: $args"
        }

        val commands = client.getRegisteredCommands()
        assertEquals(1, commands.size)
        assertEquals("ping", commands[0].name)

        val result = client.processInput("/ping hello")
        assertTrue(result is CommandResult.ServiceResponse)
        val response = result as CommandResult.ServiceResponse
        assertEquals("COMSERV", response.serviceName)
        assertEquals("Pong to Tester! Args: hello", response.response)
    }

    @Test
    fun testComservBotInteractionAndMsgComserv() {
        client.setNickname("Alice")
        client.registerCommand("status", "Gets server status") { _, _ -> "All systems operational." }

        // /comserv list
        val listResult = client.processInput("/comserv list")
        assertTrue(listResult is CommandResult.ServiceResponse)
        val listResp = listResult as CommandResult.ServiceResponse
        assertEquals("COMSERV", listResp.serviceName)
        assertTrue(listResp.response.contains("/status"))

        // /comserv status
        val execResult = client.processInput("/comserv status")
        assertTrue(execResult is CommandResult.ServiceResponse)
        val execResp = execResult as CommandResult.ServiceResponse
        assertEquals("All systems operational.", execResp.response)

        // /msg COMSERV status
        val msgResult = client.processInput("/msg COMSERV status")
        assertTrue(msgResult is CommandResult.ServiceResponse)
        val msgResp = msgResult as CommandResult.ServiceResponse
        assertEquals("All systems operational.", msgResp.response)

        // Unregister command
        client.unregisterCommand("status")
        assertEquals(0, client.getRegisteredCommands().size)

        val unregResult = client.processInput("/comserv status")
        assertTrue(unregResult is CommandResult.ServiceResponse)
        val unregResp = unregResult as CommandResult.ServiceResponse
        assertTrue(unregResp.response.contains("Error: Unknown command"))
    }

    @Test
    fun testRegularChatMessage() {
        val result = client.processInput("Hello world!")
        assertTrue(result is CommandResult.ChatMessage)
        val chat = result as CommandResult.ChatMessage
        assertEquals("Hello world!", chat.text)
    }

    @Test
    fun testUnknownCommand() {
        val result = client.processInput("/unknowncommand test")
        assertTrue(result is CommandResult.Error)
        val error = result as CommandResult.Error
        assertTrue(error.message.contains("Unknown command"))
    }
}
