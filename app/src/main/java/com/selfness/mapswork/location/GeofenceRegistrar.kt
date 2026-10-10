package com.selfness.mapswork.location

import com.selfness.mapswork.core.*
import com.selfness.mapswork.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class RegistrationPhase { CHECKING, PAUSED, EMPTY, READY, BLOCKED, ERROR }
data class RegistrationStatus(
    val phase: RegistrationPhase = RegistrationPhase.CHECKING,
    val registeredCount: Int = 0,
    val reason: Readiness? = null,
    val message: String? = null,
)

class GeofenceRegistrar(
    private val repository: ShopRepository,
    private val settings: AppSettings,
    private val transport: GeofenceTransport,
    private val requestTimeoutMillis: Long = 15_000,
    private val deviceState: () -> DeviceState,
) {
    private val mutex = Mutex()
    private val mutableStatus = MutableStateFlow(RegistrationStatus())
    @Volatile private var lastRegisteredIds: Set<String>? = null
    val status: StateFlow<RegistrationStatus> = mutableStatus.asStateFlow()

    fun invalidate() { mutableStatus.value = RegistrationStatus() }

    suspend fun synchronize(force: Boolean = false) = mutex.withLock {
        mutableStatus.value = RegistrationStatus()
        try {
            val active = repository.all().filter { it.enabled }
            if (!settings.alertsEnabled.value) {
                clearWhenUnavailable()
                mutableStatus.value = RegistrationStatus(RegistrationPhase.PAUSED)
            } else if (active.isEmpty()) {
                clearWhenUnavailable()
                mutableStatus.value = RegistrationStatus(RegistrationPhase.EMPTY)
            } else {
                val readiness = Readiness.evaluate(deviceState())
                if (readiness != Readiness.READY) {
                    clearWhenUnavailable()
                    mutableStatus.value = RegistrationStatus(RegistrationPhase.BLOCKED, reason = readiness)
                } else if (active.size > ShopRules.MAX_ENABLED) {
                    clearWhenUnavailable()
                    mutableStatus.value = RegistrationStatus(RegistrationPhase.ERROR,
                        message = "알림은 최대 100곳까지 켤 수 있어요.")
                } else {
                    val requested = active.map { it.geofenceRequestId }.toSet()
                    if (force || requested != lastRegisteredIds) {
                        withTimeout(requestTimeoutMillis) {
                            lastRegisteredIds = null
                            transport.clear()
                            transport.register(active)
                        }
                    }
                    if (!settings.alertsEnabled.value) {
                        clearWhenUnavailable()
                        mutableStatus.value = RegistrationStatus(RegistrationPhase.PAUSED)
                    } else if (repository.all().filter { it.enabled }.map { it.geofenceRequestId }.toSet() != requested) {
                        lastRegisteredIds = null
                        mutableStatus.value = RegistrationStatus()
                    } else {
                        lastRegisteredIds = requested
                        mutableStatus.value = RegistrationStatus(RegistrationPhase.READY, active.size)
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            publishFailure()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            publishFailure()
        }
    }

    private suspend fun clearWhenUnavailable() {
        lastRegisteredIds = null
        if (!deviceState().playServices) return
        try { withTimeout(requestTimeoutMillis) { transport.clear() } }
        catch (_: TimeoutCancellationException) { /* Permissions or service recovery can retry clearing. */ }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Receiver also checks the latest settings and permissions. */ }
    }

    private fun publishFailure() {
        lastRegisteredIds = null
        val readiness = Readiness.evaluate(deviceState())
        mutableStatus.value = if (readiness != Readiness.READY) {
            RegistrationStatus(RegistrationPhase.BLOCKED, reason = readiness)
        } else {
            RegistrationStatus(RegistrationPhase.ERROR,
                message = "위치 감지를 준비하지 못했어요. 위치와 알림 설정을 확인하고 다시 시도해 주세요.")
        }
    }

    fun reportEventError() {
        lastRegisteredIds = null
        mutableStatus.value = RegistrationStatus(RegistrationPhase.ERROR,
            message = "위치 감지가 잠시 중단됐어요. 알림 설정에서 다시 확인해 주세요.")
    }
}
