package com.selfness.mapswork.location

import android.content.Context
import androidx.work.*
import com.selfness.mapswork.MapsworkApplication
import java.util.concurrent.TimeUnit

class RestoreWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as MapsworkApplication).container
        container.registrar.synchronize()
        return if (container.registrar.status.value.phase == RegistrationPhase.ERROR) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        } else Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<RestoreWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork("restore-geofences", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
