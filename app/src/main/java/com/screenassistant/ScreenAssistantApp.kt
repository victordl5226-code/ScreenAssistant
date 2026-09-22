package com.screenassistant

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.screenassistant.core.data.util.ApiKeyProvider
import com.screenassistant.core.domain.repository.ai.LocalInferenceEngine
import com.screenassistant.core.domain.repository.ai.ModelManager
import com.screenassistant.service.system.action.AlarmAction
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Aplicación principal configurada con Hilt.
 * Protocolos J.A.R.V.I.S. v3.2 - Estabilidad y Eficiencia Stark.
 */
@HiltAndroidApp
class ScreenAssistantApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var alarmAction: AlarmAction
    @Inject lateinit var apiKeyProvider: ApiKeyProvider
    @Inject lateinit var localEngine: LocalInferenceEngine
    @Inject lateinit var modelManager: ModelManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        
        // Restauración de alarmas en segundo plano
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { alarmAction.restoreActiveAlarms() }
        }
        
        // Sincronizar API Key desde BuildConfig
        runCatching { apiKeyProvider.sembrarDesdeBuildConfig(com.screenassistant.BuildConfig.GEMINI_API_KEY) }

        // M28: Inicialización diferida del motor LLM para evitar bloqueos en el arranque.
        // Protocolo de seguridad: espera 5 segundos para carga de sistema antes del motor de IA.
        CoroutineScope(Dispatchers.Default).launch {
            try {
                delay(5000)
                if (modelManager.isModelDownloaded()) {
                    Log.i(TAG, "Protocolos J.A.R.V.I.S.: Cargando motor de inferencia...")
                    if (modelManager.loadModel()) {
                        Log.i(TAG, "Protocolos J.A.R.V.I.S.: Sistemas en línea y operativos.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Protocolos J.A.R.V.I.S.: Fallo menor en el núcleo de datos.", e)
            }
        }
    }

    companion object {
        private const val TAG = "ScreenAssistantApp"
    }
}
