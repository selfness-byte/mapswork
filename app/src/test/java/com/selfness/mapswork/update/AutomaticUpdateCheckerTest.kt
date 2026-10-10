package com.selfness.mapswork.update

import com.selfness.mapswork.core.UpdateRelease
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import org.junit.Test
import kotlin.test.*

class AutomaticUpdateCheckerTest {
    private val release = UpdateRelease(4, "0.4.0", 26,
        "https://raw.githubusercontent.com/selfness-byte/mapswork/codex/nearby-shop-alert/downloads/mapswork-0.4.0-test.apk",
        "a".repeat(64), "새 버전")
    private class MemoryStore : AutomaticUpdateStore {
        var value = AutomaticUpdateState()
        override fun read() = value
        override fun write(state: AutomaticUpdateState) { value = state }
    }
    private class Source(var release: UpdateRelease) : UpdateSource {
        var error: Exception? = null
        var beforeReturn: suspend () -> Unit = {}
        override suspend fun fetchRelease(): UpdateRelease {
            beforeReturn()
            error?.let { throw it }
            return release
        }
        override suspend fun download(release: UpdateRelease, target: File, onProgress: (Long, Long?) -> Unit) {
            throw AssertionError("An automatic check must never download an APK.")
        }
    }
    private val store = MemoryStore()
    private val source = Source(release)
    private var now = 1_000_000L
    private var enabled = true
    private var notificationAccepted = true
    private val notifications = mutableListOf<UpdateRelease>()
    private fun checker() = AutomaticUpdateChecker(source, store, 3, 35, { enabled }, {
        if (notificationAccepted) notifications += it
        notificationAccepted
    }, { now })

    @Test fun aNewVersionIsAnnouncedWithoutDownloading() = runBlocking {
        assertEquals(AutomaticUpdateResult.CHECKED, checker().check())
        assertEquals(listOf(release), notifications)
        assertEquals(release.versionCode, store.value.lastNotifiedVersion)
        assertEquals(now, store.value.lastCheckedAtMillis)
    }
    @Test fun reopeningWithinADaySkipsTheNetworkAndNotification() = runBlocking {
        val checker = checker()
        checker.check()
        source.error = IOException("Should not fetch again")
        now += 86_399_999
        assertEquals(AutomaticUpdateResult.SKIPPED, checker.check())
        assertEquals(listOf(release), notifications)
    }
    @Test fun theSameVersionDoesNotNotifyAgainAfterADay() = runBlocking {
        val checker = checker()
        checker.check()
        now += 86_400_000
        assertEquals(AutomaticUpdateResult.CHECKED, checker.check())
        assertEquals(listOf(release), notifications)
    }
    @Test fun aDeniedNotificationIsNotAcknowledgedAndCanBeDeliveredLater() = runBlocking {
        val checker = checker()
        notificationAccepted = false
        checker.check()
        assertEquals(0, store.value.lastNotifiedVersion)
        notificationAccepted = true
        now += 86_400_000
        checker.check()
        assertEquals(listOf(release), notifications)
    }
    @Test fun disabledAutomaticUpdatesDoNotFetchOrNotify() = runBlocking {
        enabled = false
        source.error = IOException("Should not fetch")
        assertEquals(AutomaticUpdateResult.SKIPPED, checker().check())
        assertEquals(AutomaticUpdateState(), store.value)
        assertTrue(notifications.isEmpty())
    }
    @Test fun turningOffDuringAFetchPreventsTheNotification() = runBlocking {
        source.beforeReturn = { enabled = false }
        checker().check()
        assertTrue(notifications.isEmpty())
        assertEquals(AutomaticUpdateState(), store.value)
    }
    @Test fun networkFailureIsRetryableWithoutConsumingTheCheck() = runBlocking {
        source.error = IOException("offline")
        assertEquals(AutomaticUpdateResult.RETRY, checker().check())
        assertEquals(AutomaticUpdateState(), store.value)
        assertTrue(notifications.isEmpty())
    }
    @Test fun currentAndUnsupportedVersionsDoNotNotify() = runBlocking {
        source.release = release.copy(versionCode = 3)
        checker().check()
        assertTrue(notifications.isEmpty())
        now += 86_400_000
        source.release = release.copy(minSdk = 36)
        checker().check()
        assertTrue(notifications.isEmpty())
    }
    @Test fun simultaneousChecksProduceOneNotification() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        source.beforeReturn = { entered.complete(Unit); finish.await() }
        val checker = checker()
        val first = async { checker.check() }
        entered.await()
        assertEquals(AutomaticUpdateResult.SKIPPED, checker.check())
        finish.complete(Unit)
        assertEquals(AutomaticUpdateResult.CHECKED, first.await())
        assertEquals(listOf(release), notifications)
    }
}
