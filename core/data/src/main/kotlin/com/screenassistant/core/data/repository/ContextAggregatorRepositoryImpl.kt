package com.screenassistant.core.data.repository

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.provider.CalendarContract
import com.screenassistant.core.domain.model.proactive.AggregatedContext
import com.screenassistant.core.domain.model.proactive.BatteryInfo
import com.screenassistant.core.domain.model.proactive.CalendarEvent
import com.screenassistant.core.domain.model.proactive.ConnectivityInfo
import com.screenassistant.core.domain.model.proactive.LocationType
import com.screenassistant.core.domain.model.proactive.ScreenInfo
import com.screenassistant.core.domain.repository.ContextAggregatorRepository
import com.screenassistant.core.domain.usecase.GetTemporalContextUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de [ContextAggregatorRepository] que obtiene la información
 * contextual del dispositivo usando las APIs de Android del sistema.
 *
 * Consolida las siguientes fuentes de datos:
 * - **Batería**: [BatteryManager] para nivel y estado de carga
 * - **Conectividad**: [ConnectivityManager] y [NetworkCapabilities] para WiFi/datos
 * - **Calendario**: [CalendarContract] para próximos eventos (requiere permiso)
 * - **Temporal**: [GetTemporalContextUseCase] para hora, día y período
 *
 * La información de pantalla y ubicación no está disponible directamente desde
 * APIs del sistema en la capa data; se retornan valores por defecto. Estas
 * señales se obtienen en capas superiores (servicio de monitoreo, accessibility).
 *
 * @property context Contexto de aplicación inyectado por Hilt
 * @property temporalContextUseCase Caso de uso para obtener el contexto temporal
 */
@Singleton
class ContextAggregatorRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val temporalContextUseCase: GetTemporalContextUseCase
) : ContextAggregatorRepository {

    override suspend fun getAggregatedContext(): AggregatedContext {
        return withContext(Dispatchers.IO) {
            AggregatedContext(
                temporal = temporalContextUseCase(),
                battery = getBatteryInfo(),
                upcomingEvents = getUpcomingEvents(),
                screen = ScreenInfo(packageName = null, text = ""),
                connectivity = getConnectivityInfo(),
                location = LocationType.UNKNOWN
            )
        }
    }

    override suspend fun getBatteryInfo(): BatteryInfo {
        return withContext(Dispatchers.IO) {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, intentFilter)

            if (batteryStatus != null) {
                val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val percentage = if (scale > 0) (level * 100) / scale else 0
                val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
                BatteryInfo.from(level = percentage, isCharging = isCharging)
            } else {
                BatteryInfo.from(level = 0, isCharging = false)
            }
        }
    }

    override suspend fun getUpcomingEvents(withinMinutes: Int): List<CalendarEvent> {
        return withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val endWindow = now + (withinMinutes.toLong() * 60_000L)

                val projection = arrayOf(
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND,
                    CalendarContract.Events.EVENT_LOCATION
                )

                val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
                val selectionArgs = arrayOf(now.toString(), endWindow.toString())

                val events = mutableListOf<CalendarEvent>()

                context.contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    "${CalendarContract.Events.DTSTART} ASC"
                )?.use { cursor ->
                    val titleIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                    val startIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
                    val endIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
                    val locationIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)

                    while (cursor.moveToNext()) {
                        events.add(
                            CalendarEvent(
                                title = cursor.getString(titleIdx) ?: "",
                                startMillis = cursor.getLong(startIdx),
                                endMillis = cursor.getLong(endIdx),
                                location = cursor.getString(locationIdx)
                            )
                        )
                    }
                }

                events
            } catch (_: SecurityException) {
                // Permiso de calendario no concedido
                emptyList()
            } catch (_: Exception) {
                // Error accediendo al calendario
                emptyList()
            }
        }
    }

    override suspend fun getConnectivityInfo(): ConnectivityInfo {
        return withContext(Dispatchers.IO) {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager

            if (connectivityManager == null) {
                return@withContext ConnectivityInfo.from(wifi = false, mobile = false)
            }

            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = if (activeNetwork != null) {
                connectivityManager.getNetworkCapabilities(activeNetwork)
            } else {
                null
            }

            val wifi = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)

            val mobile = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

            ConnectivityInfo.from(wifi = wifi, mobile = mobile)
        }
    }
}
