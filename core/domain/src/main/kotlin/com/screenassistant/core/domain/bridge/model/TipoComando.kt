package com.screenassistant.core.domain.bridge.model

/**
 * Clasificación del comando por EFECTO (ADR-013): una consulta NO muta estado
 * (lee notas, busca archivos); una acción SÍ produce un efecto en el sistema.
 * Fase 1 del puente: la clasificación se computa pero el puente SIEMPRE ejecuta
 * (diseño del Arquitecto); queda preparada para la respuesta diferencial de Fase 2.
 */
enum class TipoComando {
    CONSULTA,
    ACCION,
}
