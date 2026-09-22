package com.screenassistant.service.system.action

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.screenassistant.core.data.local.MessageQueueManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MessagingActionTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var messageQueue: MessageQueueManager
    private lateinit var messagingAction: MessagingAction

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = mockk(relaxed = true)
        messageQueue = mockk(relaxed = true)

        messagingAction = MessagingAction(
            context = context,
            messageQueue = messageQueue,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ===== openWhatsApp =====

    @Test
    fun `openWhatsApp with installed WhatsApp returns success`() {
        val intent = mockk<Intent>(relaxed = true)
        every { context.packageManager.getLaunchIntentForPackage("com.whatsapp") } returns intent
        every { context.startActivity(any()) } returns Unit

        val result = messagingAction.openWhatsApp()

        assertEquals("Éxito: WhatsApp abierto.", result)
        verify(exactly = 1) { context.startActivity(intent) }
    }

    @Test
    fun `openWhatsApp with WhatsApp not installed returns error`() {
        every { context.packageManager.getLaunchIntentForPackage("com.whatsapp") } returns null

        val result = messagingAction.openWhatsApp()

        assertEquals("Error: WhatsApp no parece estar instalado.", result)
    }

    @Test
    fun `openWhatsApp with exception returns error`() {
        every { context.packageManager.getLaunchIntentForPackage("com.whatsapp") } throws RuntimeException("error")

        val result = messagingAction.openWhatsApp()

        assertEquals("Error: No se pudo abrir WhatsApp.", result)
    }

    // ===== queueWhatsApp with network =====

    @Test
    fun `queueWhatsApp with network opens WhatsApp with text`() = runTest {
        val callAction = mockk<CallAction>()
        every { callAction.findContactNumber("Ana") } returns "+34600000000"
        every { context.startActivity(any()) } returns Unit

        val result = messagingAction.queueWhatsApp(
            contactName = "Ana",
            message = "Hola",
            hasNetwork = true,
            callAction = callAction
        )

        assertEquals("Éxito: Abriendo WhatsApp para enviar mensaje a Ana.", result)
        verify(exactly = 1) { context.startActivity(any()) }
        coVerify(exactly = 0) { messageQueue.addPendingMessage(any(), any(), any(), any()) }
    }

    @Test
    fun `queueWhatsApp without network queues message`() = runTest {
        val callAction = mockk<CallAction>()
        every { callAction.findContactNumber("Ana") } returns "+34600000000"
        coEvery { messageQueue.addPendingMessage(any(), any(), any(), any()) } returns Unit

        val result = messagingAction.queueWhatsApp(
            contactName = "Ana",
            message = "Hola",
            hasNetwork = false,
            callAction = callAction
        )

        assertTrue(result.contains("No tienes internet"))
        assertTrue(result.contains("Ana"))
        coVerify(exactly = 1) {
            messageQueue.addPendingMessage("WhatsApp", "Ana", "+34600000000", "Hola")
        }
    }

    @Test
    fun `queueWhatsApp without network and null number still queues`() = runTest {
        val callAction = mockk<CallAction>()
        every { callAction.findContactNumber("Desconocido") } returns null
        coEvery { messageQueue.addPendingMessage(any(), any(), any(), any()) } returns Unit

        val result = messagingAction.queueWhatsApp(
            contactName = "Desconocido",
            message = "Test",
            hasNetwork = false,
            callAction = callAction
        )

        assertTrue(result.contains("No tienes internet"))
        coVerify(exactly = 1) {
            messageQueue.addPendingMessage("WhatsApp", "Desconocido", null, "Test")
        }
    }

    // ===== queueWhatsApp openWhatsAppWithText error paths =====

    @Test
    fun `queueWhatsApp with network but exception returns error`() = runTest {
        val callAction = mockk<CallAction>()
        every { callAction.findContactNumber("Ana") } returns "+34600000000"
        every { context.startActivity(any()) } throws RuntimeException("crash")

        val result = messagingAction.queueWhatsApp(
            contactName = "Ana",
            message = "Hola",
            hasNetwork = true,
            callAction = callAction
        )

        assertEquals("Error: No pude abrir WhatsApp.", result)
    }

    @Test
    fun `queueWhatsApp with network and null number opens WhatsApp without phone`() = runTest {
        val callAction = mockk<CallAction>()
        every { callAction.findContactNumber("Ana") } returns null
        every { context.startActivity(any()) } returns Unit

        val result = messagingAction.queueWhatsApp(
            contactName = "Ana",
            message = "Hola",
            hasNetwork = true,
            callAction = callAction
        )

        assertEquals("Éxito: Abriendo WhatsApp para enviar mensaje a Ana.", result)
        verify(exactly = 1) { context.startActivity(any()) }
    }
}
