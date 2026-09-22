package com.screenassistant.service.system.action

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests para AlarmNotificationHelper.
 *
 * Nota: showAlarm() usa Notification.Builder que requiere un Context real
 * (Android framework). En unit tests puros sin Robolectric, el Builder
 * retorna null en cadenas de llamadas. Testamos lo que es testeable:
 * la constante CHANNEL_ID.
 *
 * Para tests completos de showAlarm, se necesita Robolectric o androidTest.
 */
class AlarmNotificationHelperTest {

    @Test
    fun `CHANNEL_ID es alarm_channel`() {
        assertEquals("alarm_channel", AlarmNotificationHelper.CHANNEL_ID)
    }

    @Test
    fun `CHANNEL_ID no esta vacio`() {
        assertTrue(AlarmNotificationHelper.CHANNEL_ID.isNotEmpty())
    }

    @Test
    fun `CHANNEL_ID contiene alarm`() {
        assertTrue(AlarmNotificationHelper.CHANNEL_ID.contains("alarm"))
    }
}
