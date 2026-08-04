package com.screenassistant.service.system.action

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Abre aplicaciones instaladas por package name exacto o por nombre visible (label).
 *
 * Mantiene un índice memoizado (packageName -> label) con TTL de 10 minutos para no
 * escanear el PackageManager en cada petición.
 */
@Singleton
class AppLauncherAction @Inject constructor(
    private val context: Context,
    private val now: () -> Long = System::currentTimeMillis
) {

    companion object {
        const val TAG = "AppLauncherAction"
        internal const val INDEX_TTL_MS = 600_000L // 10 min
    }

    @Volatile
    private var appIndex: List<Pair<String, String?>>? = null

    @Volatile
    private var lastFetchedAt: Long = 0L

    /**
     * BLOQUEANTE (300-600ms en el primer escaneo por label). Debe ejecutarse fuera del main thread.
     */
    fun launchApp(appQuery: String): String {
        val query = appQuery.trim()
        if (query.isEmpty()) return "Error: No me dijiste qué aplicación abrir."
        return try {
            val pm = context.packageManager
            val directIntent = pm.getLaunchIntentForPackage(query)
            if (directIntent != null) {
                launch(directIntent)
                "Éxito: Abriendo $query."
            } else {
                val match = matchApp(query, appCandidates(pm))
                if (match != null) {
                    val intent = pm.getLaunchIntentForPackage(match.first)
                    if (intent != null) {
                        launch(intent)
                        "Éxito: Abriendo ${match.second}."
                    } else {
                        "Error: No se pudo abrir ${match.first}."
                    }
                } else {
                    "Error: No encontré ninguna aplicación llamada '$query'."
                }
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e  // B5: la cancelación nunca se traga
        } catch (e: Exception) {
            android.util.Log.w(TAG, "No se pudo abrir la aplicación '$query'", e)
            "Error: No se pudo abrir la aplicación."
        }
    }

    /**
     * Lógica pura (sin Android): busca el candidato ganador para una query.
     * Normaliza con trim + lowercase; primero coincidencia EXACTA de label
     * (case-insensitive), luego CONTAINS. Empates resueltos por orden del índice
     * (packageName ascendente, ver [appCandidates]). Labels null se ignoran.
     */
    internal fun matchApp(query: String, candidates: List<Pair<String, String?>>): Pair<String, String>? {
        val normalizedQuery = query.trim().lowercase()
        val ordered = candidates.sortedBy { it.first }
        val exact = ordered.firstOrNull { (_, label) -> label?.trim()?.lowercase() == normalizedQuery }
        if (exact != null) return exact.first to exact.second.orEmpty()
        val contains = ordered.firstOrNull { (_, label) ->
            label?.trim()?.lowercase()?.contains(normalizedQuery) == true
        }
        if (contains != null) return contains.first to contains.second.orEmpty()
        return null
    }

    /**
     * Índice packageName -> label memoizado con TTL (10 min) y double-checked locking
     * (mismo patrón que currentChat() en GeminiRepository). Se ordena por packageName
     * para un tie-break determinista entre labels duplicados.
     */
    internal fun appCandidates(pm: PackageManager): List<Pair<String, String?>> {
        appIndex?.let { cached ->
            if (now() - lastFetchedAt <= INDEX_TTL_MS) return cached
        }
        synchronized(this) {
            appIndex?.let { cached ->
                if (now() - lastFetchedAt <= INDEX_TTL_MS) return cached
            }
            val fresh = pm.getInstalledApplications(0)
                .map { info: ApplicationInfo -> info.packageName to pm.getApplicationLabel(info)?.toString() }
                .sortedBy { it.first }
            appIndex = fresh
            lastFetchedAt = now()
            return fresh
        }
    }

    private fun launch(intent: Intent) {
        // FLAG_ACTIVITY_NEW_TASK es obligatorio al lanzar desde ApplicationContext
        // (no hay activity de origen en la task stack). Se hace OR para no
        // descartar flags que ya trajera el intent (p.ej. CLEAR_TOP).
        context.startActivity(
            intent.apply { flags = flags or Intent.FLAG_ACTIVITY_NEW_TASK }
        )
    }
}
