package com.screenassistant.core.domain.nlp.model

import com.screenassistant.core.domain.model.AssistantLanguage
import com.screenassistant.core.domain.model.CalculatorOperator
import com.screenassistant.core.domain.model.TranslateLanguage
import com.screenassistant.core.domain.model.VolumeAction

/**
 * Entidades extraídas del texto del usuario.
 * Cada tipo representa un dato concreto que el usuario mencionó.
 */
sealed class NlpEntity {
    /** Texto original de la entidad en el mensaje. */
    abstract val textoOriginal: String

    /** Hora (para alarmas, temporizadores). */
    data class Hora(
        val hora: Int,
        val minuto: Int,
        override val textoOriginal: String
    ) : NlpEntity()

    /** Nombre de contacto. */
    data class Contacto(
        val nombre: String,
        override val textoOriginal: String
    ) : NlpEntity()

    /** Número de teléfono. */
    data class Telefono(
        val numero: String,
        override val textoOriginal: String
    ) : NlpEntity()

    /** Nombre de aplicación. */
    data class Aplicacion(
        val consulta: String,
        override val textoOriginal: String
    ) : NlpEntity()

    /** Destino de navegación. */
    data class Destino(
        val destino: String,
        override val textoOriginal: String
    ) : NlpEntity()

    /** Contenido de texto (nota, búsqueda, etc.). */
    data class Texto(
        val contenido: String,
        override val textoOriginal: String
    ) : NlpEntity()

    /** Número entero. */
    data class Numero(
        val valor: Int,
        override val textoOriginal: String
    ) : NlpEntity()

    /** Duración en minutos (para temporizador). */
    data class Duracion(
        val minutos: Int,
        override val textoOriginal: String
    ) : NlpEntity()

    /** Acción de volumen. */
    data class AccionVolumen(
        val accion: VolumeAction,
        override val textoOriginal: String
    ) : NlpEntity()

    /** Estado on/off. */
    data class OnOff(
        val activado: Boolean,
        override val textoOriginal: String
    ) : NlpEntity()

    /** Idioma del asistente. */
    data class Idioma(
        val idioma: AssistantLanguage,
        override val textoOriginal: String
    ) : NlpEntity()

    /** Idioma de traducción. */
    data class IdiomaTraduccion(
        val idioma: TranslateLanguage,
        override val textoOriginal: String
    ) : NlpEntity()

    /** Expresión matemática (calculadora). */
    data class ExpresionCalculadora(
        val operando1: Double,
        val operador: CalculatorOperator,
        val operando2: Double,
        override val textoOriginal: String
    ) : NlpEntity()

    /** Patrón de vibración. */
    data class Vibracion(
        val duracionMs: Long,
        override val textoOriginal: String
    ) : NlpEntity()

    /** Opciones de foto. */
    data class Foto(
        val frontal: Boolean,
        override val textoOriginal: String
    ) : NlpEntity()
}
