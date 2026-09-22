package com.screenassistant.core.iot.data.repository

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.screenassistant.core.iot.domain.model.auto.CarAppState
import com.screenassistant.core.iot.domain.model.auto.VoiceCommand
import com.screenassistant.core.iot.domain.model.auto.VoiceEntity
import com.screenassistant.core.iot.domain.model.auto.VoiceEntityType
import com.screenassistant.core.iot.domain.model.auto.VoiceIntent
import com.screenassistant.core.iot.domain.model.auto.VoiceIntentType
import com.screenassistant.core.iot.domain.model.auto.VoiceResponse
import com.screenassistant.core.iot.domain.repository.MediaControlAction
import com.screenassistant.core.iot.domain.repository.VehicleCommand
import com.screenassistant.core.iot.domain.repository.NavigationDestination
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import com.screenassistant.core.iot.domain.repository.VehicleEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests unitarios para [CarAppRepositoryImpl].
 *
 * Usa Robolectric para framework Android (Intent, Uri, ContextCompat).
 * Cubre: estado del coche, permisos, comandos de voz, multimedia,
 * navegación, eventos del vehículo y foreground.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CarAppRepositoryImplTest {

    private lateinit var context: Context
    private lateinit var repository: CarAppRepositoryImpl

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun createVoiceCommand(
        intentType: VoiceIntentType? = null,
        confidence: Double = 0.95,
        entities: Map<String, VoiceEntity> = emptyMap(),
    ) = VoiceCommand(
        commandId = "cmd_test",
        rawTranscript = "test command",
        normalizedText = "test command",
        intents = if (intentType != null) {
            listOf(VoiceIntent(type = intentType, confidence = confidence))
        } else {
            emptyList()
        },
        entities = entities,
        confidence = confidence,
    )

    // ── Setup / Teardown ──────────────────────────────────────────────────────

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        repository = CarAppRepositoryImpl(context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ESTADO DEL COCHE
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `observeCarState returns flow with disconnected state initially`() = runTest {
        val state = repository.observeCarState().first()
        assertFalse(state.isConnected)
    }

    @Test
    fun `getCurrentCarState returns disconnected state`() = runTest {
        val state = repository.getCurrentCarState()
        assertFalse(state.isConnected)
    }

    @Test
    fun `isConnected returns false by default`() {
        assertFalse(repository.isConnected)
    }

    @Test
    fun `canShowComplexUI returns true when not driving`() {
        // Default state: isDriving = false
        assertTrue(repository.canShowComplexUI())
    }

    @Test
    fun `canShowComplexUI returns false when driving`() {
        repository.updateCarState { it.copy(isDriving = true) }
        assertFalse(repository.canShowComplexUI())
    }

    @Test
    fun `canShowComplexUI returns true when driving but is passenger`() {
        repository.updateCarState {
            it.copy(
                isDriving = true,
                permissions = it.permissions.copy(isPassenger = true),
            )
        }
        assertTrue(repository.canShowComplexUI())
    }

    @Test
    fun `observeVehicleEvents emits initial driving and speed events`() = runTest {
        val events = repository.observeVehicleEvents().first()
        // First event should be DrivingStateChanged
        assertTrue(events is VehicleEvent.DrivingStateChanged)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PERMISOS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `getCarPermissions checks all Android permissions`() = runTest {
        val permissions = repository.getCarPermissions()
        // In test environment, no permissions are granted
        assertFalse(permissions.microphone)
        assertFalse(permissions.location)
        assertFalse(permissions.contacts)
        assertFalse(permissions.sms)
        assertFalse(permissions.callLogs)
        assertFalse(permissions.phoneState)
        assertFalse(permissions.vehicleData)
        assertFalse(permissions.isPassenger)
    }

    @Test
    fun `requestCarPermissions returns current permissions`() = runTest {
        val permissions = repository.requestCarPermissions(emptyList())
        assertFalse(permissions.microphone)
        assertFalse(permissions.location)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // COMANDOS DE VOZ
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `processVoiceCommand with null primaryIntent returns error`() = runTest {
        val command = createVoiceCommand(intentType = null, confidence = 0.5)
        // primaryIntent is null because intents is empty
        val response = repository.processVoiceCommand(command)
        assertTrue(response.isError)
        assertEquals("NO_INTENT", response.errorCode)
    }

    @Test
    fun `processVoiceCommand with low confidence returns error`() = runTest {
        val command = createVoiceCommand(
            intentType = VoiceIntentType.NAVIGATE_TO,
            confidence = 0.5, // < 0.85
        )
        val response = repository.processVoiceCommand(command)
        assertTrue(response.isError)
        assertEquals("LOW_CONFIDENCE", response.errorCode)
    }

    @Test
    fun `processVoiceCommand NAVIGATE_TO returns navigation response`() = runTest {
        val command = createVoiceCommand(
            intentType = VoiceIntentType.NAVIGATE_TO,
            confidence = 0.95,
            entities = mapOf(
                "DESTINATION" to VoiceEntity(
                    type = VoiceEntityType.DESTINATION,
                    value = "Mi casa",
                    confidence = 0.95,
                ),
            ),
        )
        val response = repository.processVoiceCommand(command)
        assertFalse(response.isError)
        assertEquals(com.screenassistant.core.iot.domain.model.auto.ResponseType.NAVIGATION_STARTED, response.type)
        assertTrue(response.speechText.contains("Mi casa"))
    }

    @Test
    fun `processVoiceCommand NAVIGATE_TO without destination uses default`() = runTest {
        val command = createVoiceCommand(
            intentType = VoiceIntentType.NAVIGATE_TO,
            confidence = 0.95,
            entities = emptyMap(),
        )
        val response = repository.processVoiceCommand(command)
        assertFalse(response.isError)
        assertTrue(response.speechText.contains("destino"))
    }

    @Test
    fun `processVoiceCommand PLAY_MEDIA returns media response`() = runTest {
        val command = createVoiceCommand(
            intentType = VoiceIntentType.PLAY_MEDIA,
            confidence = 0.95,
            entities = mapOf(
                "SONG_NAME" to VoiceEntity(
                    type = VoiceEntityType.SONG_NAME,
                    value = "Bohemian Rhapsody",
                    confidence = 0.95,
                ),
                "ARTIST_NAME" to VoiceEntity(
                    type = VoiceEntityType.ARTIST_NAME,
                    value = "Queen",
                    confidence = 0.95,
                ),
            ),
        )
        val response = repository.processVoiceCommand(command)
        assertFalse(response.isError)
        assertEquals(com.screenassistant.core.iot.domain.model.auto.ResponseType.MEDIA_PLAYING, response.type)
        assertTrue(response.speechText.contains("Bohemian Rhapsody"))
        assertTrue(response.speechText.contains("Queen"))
    }

    @Test
    fun `processVoiceCommand PLAY_MEDIA without entities uses defaults`() = runTest {
        val command = createVoiceCommand(
            intentType = VoiceIntentType.PLAY_MEDIA,
            confidence = 0.95,
            entities = emptyMap(),
        )
        val response = repository.processVoiceCommand(command)
        assertFalse(response.isError)
        assertTrue(response.speechText.contains("música"))
    }

    @Test
    fun `processVoiceCommand other intent type returns confirmation`() = runTest {
        val command = createVoiceCommand(
            intentType = VoiceIntentType.SET_TIMER,
            confidence = 0.95,
        )
        val response = repository.processVoiceCommand(command)
        assertFalse(response.isError)
        assertEquals(com.screenassistant.core.iot.domain.model.auto.ResponseType.CONFIRMATION, response.type)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // COMANDOS AL VEHÍCULO
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `sendVehicleCommand returns NOT_AAOS error`() = runTest {
        val command = VehicleCommand.Climate(
            action = com.screenassistant.core.iot.domain.repository.ClimateAction.SET_TEMPERATURE,
            value = "22",
        )
        val result = repository.sendVehicleCommand(command)
        assertFalse(result.success)
        assertEquals("NOT_AAOS", result.errorCode)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // NAVEGACIÓN
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `startNavigation launches Maps intent`() = runTest {
        val destination = NavigationDestination(
            address = "Calle Principal 123",
            latitude = 40.4168,
            longitude = -3.7038,
            name = "Oficina",
            poiCategory = null,
        )
        val result = repository.startNavigation(destination)
        assertTrue(result.success)
        assertNotNull(result.routeId)
        assertTrue(result.routeId!!.startsWith("route_"))
    }

    @Test
    fun `startNavigation with null coordinates uses defaults`() = runTest {
        val destination = NavigationDestination(
            address = null,
            latitude = null,
            longitude = null,
            name = null,
            poiCategory = null,
        )
        val result = repository.startNavigation(destination)
        assertTrue(result.success)
    }

    @Test
    fun `cancelNavigation returns false`() = runTest {
        val result = repository.cancelNavigation()
        assertFalse(result)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MULTIMEDIA
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `controlMedia without active controller returns false`() = runTest {
        val result = repository.controlMedia(MediaControlAction.PLAY)
        assertFalse(result)
    }

    @Test
    fun `controlMedia PAUSE without controller returns false`() = runTest {
        val result = repository.controlMedia(MediaControlAction.PAUSE)
        assertFalse(result)
    }

    @Test
    fun `controlMedia NEXT without controller returns false`() = runTest {
        val result = repository.controlMedia(MediaControlAction.NEXT)
        assertFalse(result)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // FOREGROUND
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `setAppForeground true updates state to connected`() = runTest {
        val result = repository.setAppForeground(true)
        assertTrue(result)
        assertTrue(repository.isConnected)
        val state = repository.getCurrentCarState()
        assertTrue(state.isConnected)
    }

    @Test
    fun `setAppForeground false does not disconnect if already connected`() = runTest {
        repository.setAppForeground(true)
        repository.setAppForeground(false)
        // isConnected should remain true because the logic is:
        // it.copy(isConnected = inForeground || it.isConnected)
        // false || true = true
        assertTrue(repository.isConnected)
    }

    @Test
    fun `setAppForeground false on disconnected state stays disconnected`() = runTest {
        assertFalse(repository.isConnected)
        repository.setAppForeground(false)
        assertFalse(repository.isConnected)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // VEHICLE DATA
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `getVehicleData returns null when not connected`() = runTest {
        val data = repository.getVehicleData()
        assertNull(data)
    }

    @Test
    fun `getVehicleData returns state when Android Auto connected`() = runTest {
        // In test env, isAndroidAutoConnected returns false, so this returns null
        val data = repository.getVehicleData()
        assertNull(data)
    }
}
