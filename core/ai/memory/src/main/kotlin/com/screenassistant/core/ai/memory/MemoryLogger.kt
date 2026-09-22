package com.screenassistant.core.ai.memory

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wrapper delgado sobre Android Log para permitir testing unitario.
 * En tests, se puede usar un mock relajado.
 */
@Singleton
class MemoryLogger @Inject constructor() {

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
    }
}
