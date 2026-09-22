package com.screenassistant.service.system.action

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BluetoothActionTest {

    private lateinit var context: Context
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var adapter: BluetoothAdapter
    private lateinit var action: BluetoothAction

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        bluetoothManager = mockk<BluetoothManager>(relaxed = true)
        adapter = mockk<BluetoothAdapter>(relaxed = true)
        every { context.getSystemService(Context.BLUETOOTH_SERVICE) } returns bluetoothManager
        every { bluetoothManager.adapter } returns adapter
        action = BluetoothAction(context)
    }

    @Test
    fun `activar ya encendido devuelve ya activado`() {
        every { adapter.isEnabled } returns true
        val result = action.setBluetooth(true)
        assertEquals("El Bluetooth ya está activado.", result)
    }

    @Test
    fun `activar apagado abre ajustes`() {
        every { adapter.isEnabled } returns false
        val result = action.setBluetooth(true)
        assertEquals("Abriendo ajustes de Bluetooth para activarlo.", result)
    }

    @Test
    fun `desactivar encendido lo apaga`() {
        every { adapter.isEnabled } returns true
        val result = action.setBluetooth(false)
        assertEquals("Bluetooth desactivado.", result)
    }

    @Test
    fun `desactivar ya apagado devuelve ya desactivado`() {
        every { adapter.isEnabled } returns false
        val result = action.setBluetooth(false)
        assertEquals("El Bluetooth ya está desactivado.", result)
    }

    @Test
    fun `sin adaptador devuelve error`() {
        every { bluetoothManager.adapter } returns null
        val result = action.setBluetooth(true)
        assertEquals("Error: Tu dispositivo no tiene Bluetooth.", result)
    }

    @Test
    fun `activar lanza intent bluetooth settings`() {
        every { adapter.isEnabled } returns false
        action.setBluetooth(true)
        val intentSlot = slot<Intent>()
        verify(exactly = 1) { context.startActivity(capture(intentSlot)) }
    }
}