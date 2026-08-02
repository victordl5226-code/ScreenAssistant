package com.screenassistant.service.system

import android.content.Context
import com.screenassistant.core.domain.model.ActionResult
import com.screenassistant.core.domain.model.AssistantLanguage
import com.screenassistant.core.domain.model.SystemCommand
import com.screenassistant.core.domain.model.VolumeAction
import com.screenassistant.service.system.action.AlarmAction
import com.screenassistant.service.system.action.AppLauncherAction
import com.screenassistant.service.system.action.CallAction
import com.screenassistant.service.system.action.LanguageAction
import com.screenassistant.service.system.action.MapsAction
import com.screenassistant.service.system.action.MediaAction
import com.screenassistant.service.system.action.MemoryAction
import com.screenassistant.service.system.action.MessagingAction
import com.screenassistant.service.system.action.NoteAction
import com.screenassistant.service.system.action.SearchAction
import com.screenassistant.service.system.action.SettingsAction
import com.screenassistant.service.system.action.SystemVolumeAction
import com.screenassistant.service.system.action.TimerAction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SystemActionHandlerTest {

    private lateinit var callAction: CallAction
    private lateinit var messagingAction: MessagingAction
    private lateinit var mediaAction: MediaAction
    private lateinit var alarmAction: AlarmAction
    private lateinit var searchAction: SearchAction
    private lateinit var appLauncherAction: AppLauncherAction
    private lateinit var systemVolumeAction: SystemVolumeAction
    private lateinit var timerAction: TimerAction
    private lateinit var mapsAction: MapsAction
    private lateinit var settingsAction: SettingsAction
    private lateinit var languageAction: LanguageAction
    private lateinit var memoryAction: MemoryAction
    private lateinit var noteAction: NoteAction
    private lateinit var context: Context
    private lateinit var handler: SystemActionHandler

    @Before
    fun setup() {
        callAction = mockk()
        messagingAction = mockk()
        mediaAction = mockk()
        alarmAction = mockk()
        searchAction = mockk()
        appLauncherAction = mockk()
        systemVolumeAction = mockk()
        timerAction = mockk()
        mapsAction = mockk()
        settingsAction = mockk()
        languageAction = mockk()
        memoryAction = mockk()
        noteAction = mockk()
        context = mockk(relaxed = true)
        handler = SystemActionHandler(
            callAction, messagingAction, mediaAction,
            alarmAction, searchAction, appLauncherAction,
            systemVolumeAction, timerAction, mapsAction,
            settingsAction, languageAction, memoryAction,
            noteAction, context
        )
    }

    // 1. OpenApp delega en AppLauncherAction y envuelve el resultado en Success
    @Test
    fun `OpenApp delega en AppLauncherAction y devuelve Success`() = runTest {
        every { appLauncherAction.launchApp("whatsapp") } returns "Éxito: Abriendo WhatsApp."

        val result = handler.execute(SystemCommand.OpenApp("whatsapp"))

        assertEquals(ActionResult.Success("Éxito: Abriendo WhatsApp."), result)
        coVerify(exactly = 1) { appLauncherAction.launchApp("whatsapp") }
        // Las demás acciones no deben tocarse
        coVerify(exactly = 0) { callAction.makeCall(any()) }
        coVerify(exactly = 0) { alarmAction.setAlarm(any(), any(), any()) }
        coVerify(exactly = 0) { messagingAction.openWhatsApp() }
        coVerify(exactly = 0) { mediaAction.playMusic(any()) }
    }

    // 2. launchApp lanza excepción -> el handler la captura como Error
    @Test
    fun `launchApp lanzando excepcion devuelve Error con el mensaje`() = runTest {
        every { appLauncherAction.launchApp(any()) } throws RuntimeException("boom")

        val result = handler.execute(SystemCommand.OpenApp("whatsapp"))

        assertEquals(ActionResult.Error("boom"), result)
    }

    // 3. launchApp devuelve "Error: ..." sin lanzar -> Success (el catch solo captura excepciones)
    @Test
    fun `mensaje de error de launchApp se envuelve en Success`() = runTest {
        every { appLauncherAction.launchApp(any()) } returns "Error: No se pudo abrir la aplicación."

        val result = handler.execute(SystemCommand.OpenApp("whatsapp"))

        assertEquals(ActionResult.Success("Error: No se pudo abrir la aplicación."), result)
    }

    // 4. SetVolume delega en SystemVolumeAction
    @Test
    fun `SetVolume delega en SystemVolumeAction y devuelve Success`() = runTest {
        every { systemVolumeAction.setVolume(VolumeAction.UP) } returns "Éxito: Volumen subido a 10."

        val result = handler.execute(SystemCommand.SetVolume(VolumeAction.UP))

        assertEquals(ActionResult.Success("Éxito: Volumen subido a 10."), result)
        coVerify(exactly = 1) { systemVolumeAction.setVolume(VolumeAction.UP) }
        coVerify(exactly = 0) { timerAction.setTimer(any()) }
        coVerify(exactly = 0) { callAction.makeCall(any()) }
    }

    // 5. SetLanguage delega en LanguageAction
    @Test
    fun `SetLanguage delega en LanguageAction y devuelve Success`() = runTest {
        every { languageAction.setLanguage(AssistantLanguage.ENGLISH) } returns
            "Éxito: Entendido. A partir de ahora hablaré en inglés."

        val result = handler.execute(SystemCommand.SetLanguage(AssistantLanguage.ENGLISH))

        assertEquals(ActionResult.Success("Éxito: Entendido. A partir de ahora hablaré en inglés."), result)
        coVerify(exactly = 1) { languageAction.setLanguage(AssistantLanguage.ENGLISH) }
        coVerify(exactly = 0) { memoryAction.saveMemory(any()) }
    }

    // 6. SetTimer delega en TimerAction
    @Test
    fun `SetTimer delega en TimerAction y devuelve Success`() = runTest {
        every { timerAction.setTimer(5) } returns "Éxito: Temporizador configurado para 5 minutos."

        val result = handler.execute(SystemCommand.SetTimer(5))

        assertEquals(ActionResult.Success("Éxito: Temporizador configurado para 5 minutos."), result)
        coVerify(exactly = 1) { timerAction.setTimer(5) }
        coVerify(exactly = 0) { mapsAction.navigateTo(any()) }
    }

    // 7. Navigate delega en MapsAction
    @Test
    fun `Navigate delega en MapsAction y devuelve Success`() = runTest {
        every { mapsAction.navigateTo("la oficina") } returns "Éxito: Abriendo Maps hacia la oficina."

        val result = handler.execute(SystemCommand.Navigate("la oficina"))

        assertEquals(ActionResult.Success("Éxito: Abriendo Maps hacia la oficina."), result)
        coVerify(exactly = 1) { mapsAction.navigateTo("la oficina") }
        coVerify(exactly = 0) { settingsAction.openSettings() }
    }

    // 8. OpenSettings delega en SettingsAction
    @Test
    fun `OpenSettings delega en SettingsAction y devuelve Success`() = runTest {
        every { settingsAction.openSettings() } returns "Éxito: Abriendo ajustes del sistema."

        val result = handler.execute(SystemCommand.OpenSettings)

        assertEquals(ActionResult.Success("Éxito: Abriendo ajustes del sistema."), result)
        coVerify(exactly = 1) { settingsAction.openSettings() }
        coVerify(exactly = 0) { appLauncherAction.launchApp(any()) }
    }

    // 9. CallNumber delega en CallAction sin pasar por makeCall
    @Test
    fun `CallNumber delega en CallAction y devuelve Success`() = runTest {
        every { callAction.makeCallToNumber("600123456") } returns "Éxito: Llamando al 600123456..."

        val result = handler.execute(SystemCommand.CallNumber("600123456"))

        assertEquals(ActionResult.Success("Éxito: Llamando al 600123456..."), result)
        coVerify(exactly = 1) { callAction.makeCallToNumber("600123456") }
        coVerify(exactly = 0) { callAction.makeCall(any()) }
    }

    // 10. SaveMemory delega en MemoryAction (suspend)
    @Test
    fun `SaveMemory delega en MemoryAction y devuelve Success`() = runTest {
        coEvery { memoryAction.saveMemory("me gusta el cafe") } returns "Éxito: Entendido, lo recordaré."

        val result = handler.execute(SystemCommand.SaveMemory("me gusta el cafe"))

        assertEquals(ActionResult.Success("Éxito: Entendido, lo recordaré."), result)
        coVerify(exactly = 1) { memoryAction.saveMemory("me gusta el cafe") }
        coVerify(exactly = 0) { languageAction.setLanguage(any()) }
    }

    // 11. Excepción en acción nueva -> Error con el mensaje
    @Test
    fun `excepcion en SetVolume devuelve Error con el mensaje`() = runTest {
        every { systemVolumeAction.setVolume(any()) } throws RuntimeException("boom")

        val result = handler.execute(SystemCommand.SetVolume(VolumeAction.DOWN))

        assertEquals(ActionResult.Error("boom"), result)
    }

    // 12. CreateNote delega en NoteAction (suspend) y envuelve el resultado en Success
    @Test
    fun `CreateNote delega en NoteAction y devuelve Success`() = runTest {
        coEvery { noteAction.saveNote("comprar leche") } returns
            "Éxito: Nota guardada. Empieza así: «comprar leche»"

        val result = handler.execute(SystemCommand.CreateNote("comprar leche"))

        assertEquals(ActionResult.Success("Éxito: Nota guardada. Empieza así: «comprar leche»"), result)
        coVerify(exactly = 1) { noteAction.saveNote("comprar leche") }
    }

    // 13. saveNote lanza excepción -> el handler la captura como Error con el mensaje
    @Test
    fun `excepcion en saveNote devuelve Error con el mensaje`() = runTest {
        coEvery { noteAction.saveNote(any()) } throws RuntimeException("boom")

        val result = handler.execute(SystemCommand.CreateNote("texto de prueba"))

        assertEquals(ActionResult.Error("boom"), result)
    }

    // 14. CreateNote no toca las demás acciones
    @Test
    fun `CreateNote no toca las demas acciones`() = runTest {
        coEvery { noteAction.saveNote(any()) } returns "Éxito: Nota guardada. Empieza así: «x»"

        handler.execute(SystemCommand.CreateNote("texto"))

        coVerify(exactly = 0) { alarmAction.setAlarm(any(), any(), any()) }
        coVerify(exactly = 0) { timerAction.setTimer(any()) }
        coVerify(exactly = 0) { memoryAction.saveMemory(any()) }
    }

    // 15. CancelAlarm con hora delega en AlarmAction.cancelAlarm
    @Test
    fun `CancelAlarm con hora delega en AlarmAction y devuelve Success`() = runTest {
        coEvery { alarmAction.cancelAlarm(7, 30) } returns
            "Éxito: Alarma de las 7:30 cancelada."

        val result = handler.execute(SystemCommand.CancelAlarm(7, 30))

        assertEquals(
            ActionResult.Success("Éxito: Alarma de las 7:30 cancelada."),
            result
        )
        coVerify(exactly = 1) { alarmAction.cancelAlarm(7, 30) }
        coVerify(exactly = 0) { alarmAction.setAlarm(any(), any(), any()) }
    }

    // 16. CancelAlarm sin hora delega con null/null
    @Test
    fun `CancelAlarm sin hora delega en AlarmAction con null y null`() = runTest {
        coEvery { alarmAction.cancelAlarm(null, null) } returns
            "Éxito: He cancelado todas las alarmas."

        val result = handler.execute(SystemCommand.CancelAlarm(null, null))

        assertEquals(
            ActionResult.Success("Éxito: He cancelado todas las alarmas."),
            result
        )
        coVerify(exactly = 1) { alarmAction.cancelAlarm(null, null) }
    }

    // 16b. OpenAlarms delega en AlarmAction.openAlarms (O4-P2: ya no abre la lista del sistema)
    @Test
    fun `OpenAlarms delega en AlarmAction y devuelve Success`() = runTest {
        every { alarmAction.openAlarms() } returns
            "Error: Dime a qué hora quieres la alarma, por ejemplo: 'pon una alarma a las 7:30'."

        val result = handler.execute(SystemCommand.OpenAlarms)

        assertEquals(
            ActionResult.Success("Error: Dime a qué hora quieres la alarma, por ejemplo: 'pon una alarma a las 7:30'."),
            result
        )
        coVerify(exactly = 1) { alarmAction.openAlarms() }
        coVerify(exactly = 0) { alarmAction.cancelAlarm(any(), any()) }
    }

    // 17. Regresión: SetAlarm sigue delegando en setAlarm (no en cancelAlarm)
    @Test
    fun `SetAlarm sigue delegando en setAlarm`() = runTest {
        coEvery { alarmAction.setAlarm(8, 0, null) } returns "Éxito: Alarma configurada para las 8:00."

        val result = handler.execute(SystemCommand.SetAlarm(8, 0, null))

        assertEquals(ActionResult.Success("Éxito: Alarma configurada para las 8:00."), result)
        coVerify(exactly = 1) { alarmAction.setAlarm(8, 0, null) }
        coVerify(exactly = 0) { alarmAction.cancelAlarm(any(), any()) }
    }

    // 18. SetAlarm propaga la label "para X" extraída por el parser (O7)
    @Test
    fun `SetAlarm propaga la label del comando a setAlarm`() = runTest {
        coEvery { alarmAction.setAlarm(7, 30, "despertarme") } returns
            "Éxito: Alarma configurada para las 7:30."

        val result = handler.execute(SystemCommand.SetAlarm(7, 30, "despertarme"))

        assertEquals(ActionResult.Success("Éxito: Alarma configurada para las 7:30."), result)
        coVerify(exactly = 1) { alarmAction.setAlarm(7, 30, "despertarme") }
    }

    // 19. ReadNotes delega en NoteAction.readNotesSummary (suspend) y envuelve en Success
    @Test
    fun `ReadNotes delega en NoteAction y devuelve Success`() = runTest {
        coEvery { noteAction.readNotesSummary() } returns
            "Éxito: Tienes 2 notas. La más reciente empieza así: «comprar leche»"

        val result = handler.execute(SystemCommand.ReadNotes)

        assertEquals(
            ActionResult.Success("Éxito: Tienes 2 notas. La más reciente empieza así: «comprar leche»"),
            result
        )
        coVerify(exactly = 1) { noteAction.readNotesSummary() }
    }

    // 20. ReadNote delega en NoteAction.readNote (suspend) con la query y envuelve en Success
    @Test
    fun `ReadNote delega en NoteAction y devuelve Success`() = runTest {
        coEvery { noteAction.readNote("x") } returns
            "Éxito: La nota dice: «pan»"

        val result = handler.execute(SystemCommand.ReadNote("x"))

        assertEquals(ActionResult.Success("Éxito: La nota dice: «pan»"), result)
        coVerify(exactly = 1) { noteAction.readNote("x") }
    }
}
