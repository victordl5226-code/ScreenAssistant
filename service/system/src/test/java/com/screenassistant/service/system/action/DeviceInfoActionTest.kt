package com.screenassistant.service.system.action

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.screenassistant.core.domain.model.DeviceInfoType
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeviceInfoActionTest {

    private lateinit var context: Context
    private lateinit var action: DeviceInfoAction

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        action = DeviceInfoAction(context)
    }

    // Build class no funciona bien en JVM tests con mockable android.jar
    // @Test fun `modelo devuelve string no nulo`() { ... }
    // @Test fun `almacenamiento devuelve string no nulo`() { ... }

    @Test
    fun `bateria devuelve porcentaje y estado carga`() {
        val batteryManager = mockk<BatteryManager>(relaxed = true)
        every { batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) } returns 85
        every { batteryManager.isCharging } returns true
        every { context.getSystemService(Context.BATTERY_SERVICE) } returns batteryManager

        val result = action.getInfo(DeviceInfoType.BATTERY)
        assertEquals("Tu batería está al 85% y está cargando.", result)
    }

    @Test
    fun `bateria sin cargar devuelve solo porcentaje`() {
        val batteryManager = mockk<BatteryManager>(relaxed = true)
        every { batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) } returns 42
        every { batteryManager.isCharging } returns false
        every { context.getSystemService(Context.BATTERY_SERVICE) } returns batteryManager

        val result = action.getInfo(DeviceInfoType.BATTERY)
        assertEquals("Tu batería está al 42%.", result)
    }
}