package com.screenassistant.service.system.action

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothAction @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun setBluetooth(enabled: Boolean): String {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter

        if (adapter == null) {
            return "Error: Tu dispositivo no tiene Bluetooth."
        }

        return if (enabled) {
            if (adapter.isEnabled) {
                "El Bluetooth ya está activado."
            } else {
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                "Abriendo ajustes de Bluetooth para activarlo."
            }
        } else {
            if (adapter.isEnabled) {
                @Suppress("DEPRECATION")
                adapter.disable()
                "Bluetooth desactivado."
            } else {
                "El Bluetooth ya está desactivado."
            }
        }
    }
}
