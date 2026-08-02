package com.screenassistant.service.system

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.screenassistant.core.data.local.MessageQueueManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

class ConnectivityWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ConnectivityWorkerEntryPoint {
        fun messageQueue(): MessageQueueManager
    }

    override suspend fun doWork(): androidx.work.ListenableWorker.Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            ConnectivityWorkerEntryPoint::class.java
        )
        val messageQueue = entryPoint.messageQueue()

        val pendingMessages = messageQueue.getPendingMessages().first()

        if (pendingMessages.isNotEmpty()) {
            val msg = pendingMessages.first()
            messageQueue.removeMessage(msg.id)
            // En un caso real, aquí abriríamos una notificación para completar el envío
        }

        return androidx.work.ListenableWorker.Result.success()
    }
}
