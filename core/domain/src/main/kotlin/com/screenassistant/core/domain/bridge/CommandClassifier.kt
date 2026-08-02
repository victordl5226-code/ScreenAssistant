package com.screenassistant.core.domain.bridge

import com.screenassistant.core.domain.bridge.model.TipoComando
import com.screenassistant.core.domain.model.SystemCommand

/**
 * Clasificador por EFECTO (ADR-013, H1): CONSULTA = no muta estado
 * (ReadNotes, ReadNote, SearchFile); el resto (19) son ACCION.
 * `when` exhaustivo: si se añade un subtipo a SystemCommand, el compilador obliga a decidir.
 */
object CommandClassifier {

    fun clasificar(command: SystemCommand): TipoComando = when (command) {
        is SystemCommand.ReadNotes, is SystemCommand.ReadNote, is SystemCommand.SearchFile ->
            TipoComando.CONSULTA
        else -> TipoComando.ACCION
    }
}
