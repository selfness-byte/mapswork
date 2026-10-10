package com.selfness.mapswork.core

import java.net.URI

data class UpdateRelease(
    val versionCode: Long,
    val versionName: String,
    val minSdk: Int,
    val apkUrl: String,
    val sha256: String,
    val notes: String,
)

data class ApkIdentity(val packageName: String, val versionCode: Long, val signerSha256: Set<String>)
enum class UpdateAvailability { CURRENT, AVAILABLE, UNSUPPORTED }

object UpdateRules {
    const val MAX_MANIFEST_BYTES = 32 * 1024
    const val MAX_APK_BYTES = 80L * 1024 * 1024
    const val MANIFEST_URL = "https://raw.githubusercontent.com/selfness-byte/mapswork/codex/nearby-shop-alert/downloads/update.json"
    private val versionPattern = Regex("[0-9]+\\.[0-9]+\\.[0-9]+")
    private val pathPattern = Regex("/selfness-byte/mapswork/(codex/nearby-shop-alert|[a-f0-9]{40})/downloads/mapswork-[0-9]+\\.[0-9]+\\.[0-9]+-test\\.apk")

    fun manifestUrl(firebaseHostingSite: String): String {
        val site = firebaseHostingSite.trim()
        if (site.isEmpty()) return MANIFEST_URL
        require(Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?").matches(site))
        return "https://$site.web.app/updates/update.json"
    }

    fun validate(release: UpdateRelease): UpdateRelease {
        require(release.versionCode > 0 && versionPattern.matches(release.versionName))
        require(release.minSdk >= 26 && release.notes.length <= 4_000)
        require(Regex("[a-fA-F0-9]{64}").matches(release.sha256))
        val uri = URI(release.apkUrl)
        require(uri.scheme == "https" && uri.host == "raw.githubusercontent.com")
        require(uri.port == -1 && uri.userInfo == null && uri.query == null && uri.fragment == null)
        require(pathPattern.matches(uri.rawPath) && uri.rawPath.endsWith("/mapswork-${release.versionName}-test.apk"))
        return release
    }

    fun availability(release: UpdateRelease, installedCode: Long, sdk: Int): UpdateAvailability {
        validate(release)
        return when {
            release.versionCode <= installedCode -> UpdateAvailability.CURRENT
            release.minSdk > sdk -> UpdateAvailability.UNSUPPORTED
            else -> UpdateAvailability.AVAILABLE
        }
    }

    fun matchesIdentity(release: UpdateRelease, installed: ApkIdentity, artifact: ApkIdentity): Boolean =
        installed.packageName == "com.selfness.mapswork" && artifact.packageName == installed.packageName &&
            artifact.versionCode == release.versionCode && artifact.versionCode > installed.versionCode &&
            installed.signerSha256.isNotEmpty() && artifact.signerSha256 == installed.signerSha256
}

object AutomaticUpdateRules {
    const val INTERVAL_MILLIS = 24L * 60 * 60 * 1000
    fun isDue(nowMillis: Long, lastCheckedAtMillis: Long): Boolean =
        lastCheckedAtMillis <= 0 || nowMillis < lastCheckedAtMillis ||
            nowMillis - lastCheckedAtMillis >= INTERVAL_MILLIS
}
