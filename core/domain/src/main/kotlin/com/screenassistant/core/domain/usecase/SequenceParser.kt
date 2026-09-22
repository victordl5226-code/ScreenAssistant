package com.screenassistant.core.domain.usecase

import com.screenassistant.core.domain.model.CommandSequence

/**
 * Parser que detecta conectores de secuencia en el texto de entrada
 * y delega el parsing de cada segmento a SystemCommandParser.parseSingleCommand().
 *
 * Retorna null si no detecta conectores → el flujo legacy (SystemCommandParser.parse())
 * maneja el comando simple.
 *
 * Contrato puro: sin dependencias, 100% testeable unitario sin mocks.
 */
interface SequenceParser {
    /**
     * Parsea el texto buscando conectores de secuencia.
     *
     * @param text Texto original del usuario (ej: "primero alarma a las 7, luego busca el tiempo")
     * @return CommandSequence si detecta conectores, null si es un comando simple
     */
    fun parse(text: String): CommandSequence?
}