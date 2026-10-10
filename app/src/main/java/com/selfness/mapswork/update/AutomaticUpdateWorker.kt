package com.selfness.mapswork.update

import android.content.Context
import androidx.work.*
import com.selfness.mapswork.MapsworkApplication
import java.util.concurrent.TimeUnit

class AutomaticUpdateWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val checker = (applicationContext as MapsworkApplication).container.automaticUpdates
        return when (checker.check()) {
            AutomaticUpdateResult.RETRY -> if (runAttemptCount < 2) Result.retry() else Result.success()
            else -> Result.success()
        }
    }

    companion object {
        const val WORK_NAME = "automatic-app-updates"

        fun schedule(context: Context, enabled: Boolean) {
            val manager = WorkManager.getInstance(context)
            if (!enabled) {
                manager.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<AutomaticUpdateWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES).build()
            manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
