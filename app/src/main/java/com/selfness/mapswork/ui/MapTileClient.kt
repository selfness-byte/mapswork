package com.selfness.mapswork.ui

import android.content.Context
import com.selfness.mapswork.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.*
import org.maplibre.android.module.http.HttpRequestUtil
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object MapTileClient {
    val failures = MutableStateFlow(0L)
    private var configured = false

    @Synchronized fun configure(context: Context) {
        if (configured) return
        val client = OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "map-http-cache"), 50L * 1024 * 1024))
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mapswork/${BuildConfig.VERSION_NAME} (+https://github.com/selfness-byte/mapswork)")
                    .build()
                try {
                    chain.proceed(request).also { if (!it.isSuccessful && it.code != 304) failures.update { value -> value + 1 } }
                } catch (error: IOException) {
                    failures.update { it + 1 }
                    throw error
                }
            }.build()
        HttpRequestUtil.setOkHttpClient(client)
        HttpRequestUtil.setLogEnabled(false)
        configured = true
    }
}
