package com.selfness.mapswork.update

import com.selfness.mapswork.core.*
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlin.test.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateManagerTest {
    @get:Rule val temporary = TemporaryFolder()
    private val bytes = "verified candidate APK bytes".toByteArray()
    private val release = UpdateRelease(2, "0.2.0", 26,
        "https://raw.githubusercontent.com/selfness-byte/mapswork/codex/nearby-shop-alert/downloads/mapswork-0.2.0-test.apk",
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, "새 이름")
    private val source = FakeSource()
    private fun manager(verify: (File, UpdateRelease) -> Unit = { _, _ -> }) = UpdateManager(temporary.newFolder(), source, 1, 35, verify)

    private inner class FakeSource : UpdateSource {
        var metadata = release
        var fail = false
        var payload = bytes
        var awaitCancellation = false
        override suspend fun fetchRelease() = metadata
        override suspend fun download(release: UpdateRelease, target: File, onProgress: (Long, Long?) -> Unit) {
            target.writeBytes(payload)
            onProgress(payload.size.toLong(), payload.size.toLong())
            if (awaitCancellation) delay(Long.MAX_VALUE)
            if (fail) throw IOException("offline")
        }
    }

    @Test fun newerVersionCanBeDownloadedAndPassedToInstaller() = runBlocking {
        val manager = manager()
        manager.check(); assertEquals(UpdatePhase.AVAILABLE, manager.status.value.phase)
        manager.download(); assertEquals(UpdatePhase.READY, manager.status.value.phase)
        assertContentEquals(bytes, manager.installationFile()!!.readBytes())
    }
    @Test fun corruptDownloadIsRemovedAndNeverInstallable() = runBlocking {
        source.payload = "damaged".toByteArray()
        val manager = manager(); manager.check(); manager.download()
        assertEquals(UpdatePhase.ERROR, manager.status.value.phase)
        assertNull(manager.installationFile())
        assertTrue(temporary.root.walk().none { it.isFile })
    }
    @Test fun networkFailureRemovesPartialFileAndCanBeRetried() = runBlocking {
        source.fail = true
        val manager = manager(); manager.check(); manager.download()
        assertEquals(UpdatePhase.ERROR, manager.status.value.phase)
        assertTrue(temporary.root.walk().none { it.isFile })
        source.fail = false; manager.download()
        assertEquals(UpdatePhase.READY, manager.status.value.phase)
    }
    @Test fun rejectedApkIdentityNeverBecomesReady() = runBlocking {
        val manager = manager { _, _ -> throw IOException("wrong signer") }
        manager.check(); manager.download()
        assertEquals(UpdatePhase.ERROR, manager.status.value.phase)
        assertNull(manager.installationFile())
        assertTrue(temporary.root.walk().none { it.isFile })
    }
    @Test fun fileIsRecheckedImmediatelyBeforeInstallation() = runBlocking {
        val manager = manager(); manager.check(); manager.download()
        temporary.root.walk().first { it.name == "update.apk" }.writeText("changed after verification")
        assertNull(manager.installationFile())
        assertEquals(UpdatePhase.ERROR, manager.status.value.phase)
    }
    @Test fun invalidMetadataCannotStartDownload() = runBlocking {
        source.metadata = release.copy(apkUrl = "https://example.com/payload.apk")
        val manager = manager(); manager.check(); manager.download()
        assertEquals(UpdatePhase.ERROR, manager.status.value.phase)
        assertNull(manager.installationFile())
        assertTrue(temporary.root.walk().none { it.isFile })
    }
    @Test fun unsupportedAndroidCannotDownloadCandidate() = runBlocking {
        source.metadata = release.copy(minSdk = 36)
        val manager = manager(); manager.check(); manager.download()
        assertEquals(UpdatePhase.UNSUPPORTED, manager.status.value.phase)
        assertNull(manager.installationFile())
    }
    @Test fun canceledDownloadCleansUpAndRestoresAvailableState() = runBlocking {
        source.awaitCancellation = true
        val manager = manager(); manager.check()
        val job = launch { manager.download() }
        withTimeout(5_000) { while (manager.status.value.downloadedBytes == 0L) delay(10) }
        job.cancelAndJoin()
        assertEquals(UpdatePhase.AVAILABLE, manager.status.value.phase)
        assertTrue(temporary.root.walk().none { it.isFile })
    }
}
