package com.selfness.mapswork.update

import com.selfness.mapswork.core.UpdateRelease
import com.selfness.mapswork.core.UpdateRules
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import org.json.JSONObject

interface UpdateSource {
    suspend fun fetchRelease(): UpdateRelease
    suspend fun download(release: UpdateRelease, target: File, onProgress: (Long, Long?) -> Unit)
}

class UpdateProblem(message: String) : IOException(message)

class GitHubUpdateSource : UpdateSource {
    private val client = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS).build()
    private val metadataClient = client.newBuilder().callTimeout(20, TimeUnit.SECONDS).build()

    override suspend fun fetchRelease(): UpdateRelease {
        val request = request(UpdateRules.MANIFEST_URL)
        metadataClient.response(request).use { response ->
            if (!response.isSuccessful) throw UpdateProblem("GitHub 업데이트 정보를 읽지 못했어요. 인터넷 연결과 저장소 공개 여부를 확인해 주세요.")
            val body = response.body ?: throw IOException("Empty metadata")
            if (body.contentLength() > UpdateRules.MAX_MANIFEST_BYTES) throw IOException("Oversized metadata")
            val data = body.byteStream().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                while (output.size() <= UpdateRules.MAX_MANIFEST_BYTES) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer, 0, minOf(buffer.size, UpdateRules.MAX_MANIFEST_BYTES + 1 - output.size()))
                    if (count == -1) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            if (data.size > UpdateRules.MAX_MANIFEST_BYTES) throw IOException("Oversized metadata")
            val json = JSONObject(String(data, Charsets.UTF_8))
            return UpdateRules.validate(UpdateRelease(json.getLong("versionCode"), json.getString("versionName"),
                json.getInt("minSdk"), json.getString("apkUrl"), json.getString("sha256"), json.optString("notes")))
        }
    }

    override suspend fun download(release: UpdateRelease, target: File, onProgress: (Long, Long?) -> Unit) {
        UpdateRules.validate(release)
        client.response(request(release.apkUrl)).use { response ->
            if (!response.isSuccessful) throw UpdateProblem("업데이트 파일을 받지 못했어요. 잠시 후 다시 시도해 주세요.")
            val body = response.body ?: throw IOException("Empty APK")
            val total = body.contentLength().takeIf { it >= 0 }
            if (total != null && total > UpdateRules.MAX_APK_BYTES) throw IOException("Oversized APK")
            var count = 0L
            body.byteStream().use { input -> target.outputStream().use { output ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val size = input.read(buffer)
                    if (size == -1) break
                    count += size
                    if (count > UpdateRules.MAX_APK_BYTES) throw IOException("Oversized APK")
                    output.write(buffer, 0, size)
                    onProgress(count, total)
                }
            } }
            if (count == 0L || (total != null && count != total)) throw IOException("Incomplete APK")
        }
    }

    private fun request(url: String) = Request.Builder().url(url).header("Cache-Control", "no-cache")
        .header("User-Agent", "Mapswork-Android-Updates").build()

    private suspend fun OkHttpClient.response(request: Request): Response = suspendCancellableCoroutine { continuation ->
        val call = newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response, onCancellation = { _, value, _ -> value.close() })
            }
        })
    }
}
