package com.selfness.mapswork.update

import com.selfness.mapswork.core.AutomaticUpdateRules
import com.selfness.mapswork.core.UpdateAvailability
import com.selfness.mapswork.core.UpdateRelease
import com.selfness.mapswork.core.UpdateRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

data class AutomaticUpdateState(val lastCheckedAtMillis: Long = 0, val lastNotifiedVersion: Long = 0)

interface AutomaticUpdateStore {
    fun read(): AutomaticUpdateState
    fun write(state: AutomaticUpdateState)
}

enum class AutomaticUpdateResult { SKIPPED, CHECKED, RETRY }

class AutomaticUpdateChecker(
    private val source: UpdateSource,
    private val store: AutomaticUpdateStore,
    private val installedCode: Long,
    private val sdk: Int,
    private val isEnabled: () -> Boolean,
    private val notify: (UpdateRelease) -> Boolean,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    suspend fun check(): AutomaticUpdateResult = withContext(Dispatchers.IO) {
        if (!mutex.tryLock()) return@withContext AutomaticUpdateResult.SKIPPED
        try {
            if (!isEnabled()) return@withContext AutomaticUpdateResult.SKIPPED
            val state = store.read()
            val now = nowMillis()
            if (!AutomaticUpdateRules.isDue(now, state.lastCheckedAtMillis)) {
                return@withContext AutomaticUpdateResult.SKIPPED
            }
            val release = UpdateRules.validate(source.fetchRelease())
            // Settings disables and cancels on main; keep the final check and post in that same turn.
            val notified = withContext(Dispatchers.Main.immediate) delivery@{
                currentCoroutineContext().ensureActive()
                if (!isEnabled()) return@delivery null
                val available = UpdateRules.availability(release, installedCode, sdk) == UpdateAvailability.AVAILABLE
                val delivered = available && release.versionCode > state.lastNotifiedVersion && notify(release)
                if (delivered) {
                    // Once posted, persist delivery even if WorkManager cancels while dispatching back.
                    withContext(NonCancellable + Dispatchers.IO) {
                        store.write(state.copy(lastCheckedAtMillis = now, lastNotifiedVersion = release.versionCode))
                    }
                }
                delivered
            } ?: return@withContext AutomaticUpdateResult.SKIPPED
            if (!notified) store.write(state.copy(lastCheckedAtMillis = now))
            AutomaticUpdateResult.CHECKED
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AutomaticUpdateResult.RETRY
        } finally { mutex.unlock() }
    }
}
