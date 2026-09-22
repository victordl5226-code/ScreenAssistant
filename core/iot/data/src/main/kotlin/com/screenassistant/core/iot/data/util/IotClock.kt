package com.screenassistant.core.iot.data.util

import java.time.Instant as JavaInstant
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reloj configurable para operaciones IoT.
 *
 * Permite inyectar un reloj (real o fake) para testing determinista
 * de lógica temporal: timeouts, expiración de tokens, sincronización,
 * programaciones, etc.
 *
 * Uso en producción: inyecta [RealIotClock] (implementación por defecto).
 * Uso en tests: inyecta [FakeIotClock] para controlar el tiempo.
 */
interface IotClock {
    /** Tiempo actual en UTC. */
    fun now(): Instant

    /** Tiempo actual en milisegundos epoch (para APIs legacy). */
    fun nowMillis(): Long = now().toEpochMilliseconds()
}

/**
 * Implementación real usando [Clock.System].
 */
@Singleton
class RealIotClock @Inject constructor() : IotClock {
    override fun now(): Instant = Clock.System.now()
}

/**
 * Implementación fake para testing.
 *
 * Permite avanzar el tiempo manualmente para probar:
 * - Expiración de tokens
 * - Timeouts de comandos
 * - Programaciones recurrentes
 * - Limpieza de datos antiguos
 */
class FakeIotClock(private var currentTime: Instant = Instant.parse("1970-01-01T00:00:00Z")) : IotClock {

    override fun now(): Instant = currentTime

    /** Avanza el reloj en milisegundos. */
    fun advance(millis: Long) {
        val javaInstant = JavaInstant.ofEpochMilli(currentTime.toEpochMilliseconds()).plusMillis(millis)
        currentTime = Instant.parse(javaInstant.toString())
    }

    /** Avanza el reloj en segundos. */
    fun advanceSeconds(seconds: Long) {
        advance(seconds * 1000)
    }

    /** Avanza el reloj en minutos. */
    fun advanceMinutes(minutes: Long) {
        advance(minutes * 60 * 1000)
    }

    /** Avanza el reloj en horas. */
    fun advanceHours(hours: Long) {
        advance(hours * 60 * 60 * 1000)
    }

    /** Avanza el reloj en días. */
    fun advanceDays(days: Long) {
        advance(days * 24 * 60 * 60 * 1000)
    }

    /** Establece un tiempo específico. */
    fun setTime(instant: Instant) {
        currentTime = instant
    }

    /** Resetea al epoch. */
    fun reset() {
        currentTime = Instant.parse("1970-01-01T00:00:00Z")
    }
}