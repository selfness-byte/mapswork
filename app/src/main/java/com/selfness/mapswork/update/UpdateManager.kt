package com.selfness.mapswork.update

import com.selfness.mapswork.core.*
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class UpdatePhase { IDLE, CHECKING, CURRENT, AVAILABLE, UNSUPPORTED, DOWNLOADING, READY, ERROR }
data class UpdateStatus(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val release: UpdateRelease? = null,
    val message: String? = null,
    val downloadedBytes: Long = 0,
    val totalBytes: Long? = null,
)

class UpdateManager(
    private val directory: File,
    private val source: UpdateSource,
    private val installedCode: Long,
    private val sdk: Int,
    private val verifyArtifact: (File, UpdateRelease) -> Unit,
) {
    private val mutableStatus = MutableStateFlow(UpdateStatus())
    val status = mutableStatus.asStateFlow()
    private val mutex = Mutex()
    private val partial get() = File(directory, "update.part")
    private val candidate get() = File(directory, "update.apk")

    suspend fun check() = withContext(Dispatchers.IO) {
        if (!mutex.tryLock()) return@withContext
        try {
            mutableStatus.value = UpdateStatus(UpdatePhase.CHECKING)
            partial.delete(); candidate.delete()
            val release = UpdateRules.validate(source.fetchRelease())
            val phase = when (UpdateRules.availability(release, installedCode, sdk)) {
                UpdateAvailability.CURRENT -> UpdatePhase.CURRENT
                UpdateAvailability.AVAILABLE -> UpdatePhase.AVAILABLE
                UpdateAvailability.UNSUPPORTED -> UpdatePhase.UNSUPPORTED
            }
            mutableStatus.value = UpdateStatus(phase, release)
        } catch (cancelled: CancellationException) {
            mutableStatus.value = UpdateStatus()
            throw cancelled
        } catch (error: Exception) {
            mutableStatus.value = UpdateStatus(UpdatePhase.ERROR, message = explanation(error))
        } finally { mutex.unlock() }
    }

    suspend fun download() = withContext(Dispatchers.IO) {
        if (!mutex.tryLock()) return@withContext
        val state = mutableStatus.value
        val release = state.release
        try {
            if (release == null || state.phase !in listOf(UpdatePhase.AVAILABLE, UpdatePhase.ERROR) ||
                UpdateRules.availability(release, installedCode, sdk) != UpdateAvailability.AVAILABLE) return@withContext
            mutableStatus.value = UpdateStatus(UpdatePhase.DOWNLOADING, release)
            check(directory.isDirectory || directory.mkdirs())
            partial.delete(); candidate.delete()
            source.download(release, partial) { bytes, total ->
                mutableStatus.value = UpdateStatus(UpdatePhase.DOWNLOADING, release, downloadedBytes = bytes, totalBytes = total)
            }
            currentCoroutineContext().ensureActive()
            verify(partial, release)
            check(partial.renameTo(candidate))
            mutableStatus.value = UpdateStatus(UpdatePhase.READY, release)
        } catch (cancelled: CancellationException) {
            partial.delete(); candidate.delete()
            mutableStatus.value = UpdateStatus(UpdatePhase.AVAILABLE, release)
            throw cancelled
        } catch (error: Exception) {
            partial.delete(); candidate.delete()
            mutableStatus.value = UpdateStatus(UpdatePhase.ERROR, release, explanation(error))
        } finally { mutex.unlock() }
    }

    suspend fun installationFile(): File? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val state = mutableStatus.value
            val release = state.release
            if (state.phase != UpdatePhase.READY || release == null) return@withLock null
            try { verify(candidate, release); candidate }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                candidate.delete()
                mutableStatus.value = UpdateStatus(UpdatePhase.ERROR, release, explanation(error))
                null
            }
        }
    }

    private fun verify(file: File, release: UpdateRelease) {
        if (!file.isFile || file.length() !in 1..UpdateRules.MAX_APK_BYTES) throw UpdateProblem("업데이트 파일이 완전하지 않아요. 다시 받아 주세요.")
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) { val size = input.read(buffer); if (size == -1) break; digest.update(buffer, 0, size) }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(release.sha256, ignoreCase = true)) throw UpdateProblem("업데이트 파일 검증에 실패했어요. 다시 받아 주세요.")
        verifyArtifact(file, release)
    }

    private fun explanation(error: Exception) = if (error is UpdateProblem) error.message
        else "업데이트를 완료하지 못했어요. 인터넷 연결과 저장 공간을 확인하고 다시 시도해 주세요."
}
