package com.screenassistant.core.iot.data.repository

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import com.screenassistant.core.iot.domain.model.auto.CarAppState
import com.screenassistant.core.iot.domain.model.auto.CarPermissions
import com.screenassistant.core.iot.domain.model.auto.VoiceCommand
import com.screenassistant.core.iot.domain.model.auto.VoiceIntentType
import com.screenassistant.core.iot.domain.model.auto.VoiceResponse
import com.screenassistant.core.iot.domain.repository.CarAppRepository
import com.screenassistant.core.iot.domain.repository.CarPermission
import com.screenassistant.core.iot.domain.repository.MediaControlAction
import com.screenassistant.core.iot.domain.repository.NavigationDestination as DomainNavDestination
import com.screenassistant.core.iot.domain.repository.NavigationResult as DomainNavResult
import com.screenassistant.core.iot.domain.repository.VehicleCommand as DomainVehicleCommand
import com.screenassistant.core.iot.domain.repository.VehicleCommandResult as DomainVehicleCommandResult
import com.screenassistant.core.iot.domain.repository.VehicleEvent as DomainVehicleEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Implementación de [CarAppRepository].
 *
 * Implementación híbrida que combina:
 * 1. **Android standard APIs**: permisos, media control, navigación via Intent
 * 2. **Car App Library**: proyección Android Auto (template rendering)
 * 3. **AAOS APIs (optativo)**: VehiclePropertyManager (solo en AAOS)
 *
 * Para dispositivos **phone-to-car** (Android Auto projection):
 * - La app se ejecuta en el teléfono y proyecta en la pantalla del coche
 * - Los permisos se gestionan via Android permission system
 * - La navegación se delega a Google Maps via Intent
 * - El control multimedia usa MediaBrowser/MediaController
 *
 * @param context Contexto de la aplicación
 */
@Singleton
class CarAppRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : CarAppRepository {

    companion object {
        private const val TAG = "CarAppRepository"
        private const val GEO_URI_PREFIX = "google.navigation:q="
    }

    private val _carState = MutableStateFlow(CarAppState.disconnected())

    private val mediaSessionManager: MediaSessionManager? by lazy {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
    }

    // ── Estado del coche ─────────────────────────────────────────────────────

    override fun observeCarState(): Flow<CarAppState> = _carState.asStateFlow()

    override suspend fun getCurrentCarState(): CarAppState = _carState.value

    override val isConnected: Boolean
        get() = _carState.value.isConnected

    override fun canShowComplexUI(): Boolean = _carState.value.canShowComplexUI

    override fun observeVehicleEvents(): Flow<DomainVehicleEvent> = callbackFlow {
        val previous = _carState.value
        trySend(DomainVehicleEvent.DrivingStateChanged(previous.isDriving))
        trySend(DomainVehicleEvent.SpeedChanged(previous.speedKmh))
        awaitClose { /* no cleanup needed */ }
    }

    // ── Permisos ─────────────────────────────────────────────────────────────

    override suspend fun getCarPermissions(): CarPermissions {
        return CarPermissions(
            microphone = hasPermission(Manifest.permission.RECORD_AUDIO),
            location = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
                hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION),
            contacts = hasPermission(Manifest.permission.READ_CONTACTS),
            sms = hasPermission(Manifest.permission.READ_SMS),
            callLogs = hasPermission(Manifest.permission.READ_CALL_LOG),
            phoneState = hasPermission(Manifest.permission.READ_PHONE_STATE),
            vehicleData = hasPermission("android.car.permission.CAR_INFO"),
            isPassenger = false,
        )
    }

    override suspend fun requestCarPermissions(permissions: List<CarPermission>): CarPermissions {
        Log.d(TAG, "requestCarPermissions called — permissions should be requested via UI")
        return getCarPermissions()
    }

    override suspend fun getVehicleData(): CarAppState? {
        return if (isAndroidAutoConnected()) _carState.value else null
    }

    // ── Comandos de voz ──────────────────────────────────────────────────────

    override suspend fun processVoiceCommand(command: VoiceCommand): VoiceResponse {
        return when {
            command.primaryIntent == null -> VoiceResponse.error(
                commandId = command.commandId,
                message = "No pude entender el comando",
                errorCode = "NO_INTENT",
            )
            !command.isHighConfidence -> VoiceResponse.error(
                commandId = command.commandId,
                message = "No estoy seguro de lo que quieres. ¿Puedes repetir?",
                errorCode = "LOW_CONFIDENCE",
            )
            else -> {
                val intent = command.primaryIntent!!
                when (intent.type) {
                    VoiceIntentType.NAVIGATE_TO -> {
                        val destination = command.getEntity("DESTINATION")?.value ?: "destino"
                        VoiceResponse.navigationResult(
                            commandId = command.commandId,
                            destination = destination,
                            eta = "calculando",
                            distance = "calculando",
                        )
                    }
                    VoiceIntentType.PLAY_MEDIA -> {
                        val song = command.getEntity("SONG_NAME")?.value ?: "música"
                        val artist = command.getEntity("ARTIST_NAME")?.value
                        VoiceResponse.mediaPlaying(command.commandId, song, artist)
                    }
                    else -> VoiceResponse.confirm(commandId = command.commandId)
                }
            }
        }
    }

    // ── Comandos al vehículo ─────────────────────────────────────────────────

    override suspend fun sendVehicleCommand(command: DomainVehicleCommand): DomainVehicleCommandResult {
        Log.d(TAG, "sendVehicleCommand: $command")
        return DomainVehicleCommandResult(
            success = false,
            errorCode = "NOT_AAOS",
            errorMessage = "Vehicle commands require AAOS or OEM API",
        )
    }

    override suspend fun startNavigation(destination: DomainNavDestination): DomainNavResult {
        return try {
            val lat = destination.latitude ?: 0.0
            val lng = destination.longitude ?: 0.0
            val name = destination.name ?: "destino"

            val uri = Uri.parse("$GEO_URI_PREFIX$lat,$lng ($name)")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage("com.google.android.apps.maps")
            }
            context.startActivity(intent)

            Log.d(TAG, "Navigation started to $name ($lat, $lng)")
            DomainNavResult(
                success = true,
                routeId = "route_${UUID.randomUUID()}",
                eta = null,
                distanceMeters = null,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start navigation", e)
            DomainNavResult(
                success = false,
                routeId = null,
                eta = null,
                distanceMeters = null,
                errorCode = "MAPS_UNAVAILABLE",
                errorMessage = "Google Maps not available",
            )
        }
    }

    override suspend fun cancelNavigation(): Boolean {
        Log.d(TAG, "cancelNavigation called — no direct API from phone")
        return false
    }

    // ── Control multimedia ───────────────────────────────────────────────────

    override suspend fun controlMedia(action: MediaControlAction): Boolean {
        return try {
            val controller = getActiveMediaController() ?: run {
                Log.w(TAG, "No active media controller")
                return false
            }
            val controls = controller.transportControls ?: return false

            when (action) {
                MediaControlAction.PLAY -> { controls.play(); true }
                MediaControlAction.PAUSE -> { controls.pause(); true }
                MediaControlAction.NEXT -> { controls.skipToNext(); true }
                MediaControlAction.PREVIOUS -> { controls.skipToPrevious(); true }
                MediaControlAction.SET_VOLUME -> false
                MediaControlAction.SET_SHUFFLE -> {
                    // setShuffleMode is not public API in TransportControls
                    Log.d(TAG, "setShuffle not available via public TransportControls API")
                    false
                }
                MediaControlAction.SET_REPEAT -> {
                    // setRepeatMode is not public API in TransportControls
                    Log.d(TAG, "setRepeat not available via public TransportControls API")
                    false
                }
                MediaControlAction.SELECT_SOURCE -> false
                MediaControlAction.SELECT_QUEUE_ITEM -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error controlling media: $action", e)
            false
        }
    }

    // ── Foreground ───────────────────────────────────────────────────────────

    override suspend fun setAppForeground(inForeground: Boolean): Boolean {
        Log.d(TAG, "setAppForeground: $inForeground")
        _carState.update { it.copy(isConnected = inForeground || it.isConnected) }
        return true
    }

    // ── Helpers privados ─────────────────────────────────────────────────────

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun isAndroidAutoConnected(): Boolean {
        return try {
            val pm = context.packageManager
            val intent = Intent("androidx.car.app.ACTION_MANAGE_HOST").apply {
                setPackage(context.packageName)
            }
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
        } catch (e: Exception) {
            false
        }
    }

    private fun getActiveMediaController(): MediaController? {
        return try {
            val sessionManager = mediaSessionManager ?: return null
            @Suppress("DEPRECATION")
            val sessions = sessionManager.getActiveSessions(null)
            sessions.firstOrNull { it.playbackState != null }
        } catch (e: SecurityException) {
            Log.w(TAG, "MediaSession permission not granted", e)
            null
        }
    }

    fun updateCarState(update: (CarAppState) -> CarAppState) {
        _carState.update(update)
    }
}
