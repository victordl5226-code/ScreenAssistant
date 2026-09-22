package com.screenassistant.service.system.action

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.screenassistant.core.domain.model.DeviceInfoType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceInfoAction @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getInfo(type: DeviceInfoType): String = when (type) {
        DeviceInfoType.MODEL -> getModelInfo()
        DeviceInfoType.BATTERY -> getBatteryInfo()
        DeviceInfoType.STORAGE -> getStorageInfo()
    }

    private fun getModelInfo(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        val androidVersion = Build.VERSION.RELEASE
        val sdk = Build.VERSION.SDK_INT
        return "Tu teléfono es un $manufacturer $model, con Android $androidVersion (SDK $sdk)."
    }

    private fun getBatteryInfo(): String {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = batteryManager.isCharging
        val chargingText = if (isCharging) " y está cargando" else ""
        return "Tu batería está al $level%$chargingText."
    }

    private fun getStorageInfo(): String {
        val stat = StatFs(Environment.getDataDirectory().path)
        val totalBytes = stat.totalBytes
        val freeBytes = stat.availableBytes
        val usedBytes = totalBytes - freeBytes
        val totalGB = totalBytes / (1024.0 * 1024 * 1024)
        val usedGB = usedBytes / (1024.0 * 1024 * 1024)
        val freeGB = freeBytes / (1024.0 * 1024 * 1024)
        return "Tienes ${String.format("%.1f", freeGB)} GB libres de ${String.format("%.1f", totalGB)} GB totales. Usados: ${String.format("%.1f", usedGB)} GB."
    }
}
