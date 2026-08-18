package com.example.ui

import com.aistudio.ivccx.pqrsw.client.IvcClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChatViewModelTest {

    private lateinit var client: IvcClient
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        client = IvcClient()
        viewModel = ChatViewModel(client)
    }

    @Test
    fun testInitialState() {
        assertEquals("#general", viewModel.currentChannel.value)
        assertEquals(null, viewModel.topic.value)
        assertEquals("Anonymous", viewModel.nickname.value)
    }

    @Test
    fun testSendMessageNickChange() {
        viewModel.sendMessage("/nick Alice")
        assertEquals("Alice", viewModel.nickname.value)

        val msgs = viewModel.messages.value
        assertTrue(msgs.isNotEmpty())
        val lastMsg = msgs.last()
        assertEquals("*", lastMsg.senderName)
        assertTrue(lastMsg.text.contains("Anonymous is now known as Alice"))
    }

    @Test
    fun testSendMessageAction() {
        client.setNickname("Bob")
        viewModel.sendMessage("/me waves")

        val msgs = viewModel.messages.value
        assertTrue(msgs.isNotEmpty())
        val lastMsg = msgs.last()
        assertEquals("*", lastMsg.senderName)
        assertEquals("* Bob waves", lastMsg.text)
    }

    @Test
    fun testSendMessageJoinChannelAndTopic() {
        viewModel.sendMessage("/join #kotlin")
        assertEquals("#kotlin", viewModel.currentChannel.value)

        viewModel.sendMessage("/topic Kotlin rocks")
        assertEquals("Kotlin rocks", viewModel.topic.value)
    }

    @Test
    fun testComservBotIntegration() {
        viewModel.sendMessage("/ping test")

        val msgs = viewModel.messages.value
        assertTrue(msgs.isNotEmpty())
        val lastMsg = msgs.last()
        assertEquals("COMSERV", lastMsg.senderName)
        assertTrue(lastMsg.text.contains("Pong to Anonymous! Args: test"))
    }

    @Test
    fun testClearCommand() {
        viewModel.sendMessage("Hello")
        assertTrue(viewModel.messages.value.isNotEmpty())

        viewModel.sendMessage("/clear")
        assertTrue(viewModel.messages.value.isEmpty())
    }
}
