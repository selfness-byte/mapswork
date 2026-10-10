package com.selfness.mapswork.core

import java.security.MessageDigest
import kotlin.test.*
import org.junit.Test

class UpdateRulesTest {
    private val release = UpdateRelease(2, "0.2.0", 26,
        "https://raw.githubusercontent.com/selfness-byte/mapswork/codex/nearby-shop-alert/downloads/mapswork-0.2.0-test.apk",
        "a".repeat(64), "앱 이름 변경")
    private val installed = ApkIdentity("com.selfness.mapswork", 1, setOf("certificate"))
    private val candidate = installed.copy(versionCode = 2)

    @Test fun newerVersionIsAvailable() { assertEquals(UpdateAvailability.AVAILABLE, UpdateRules.availability(release, 1, 35)) }
    @Test fun sameVersionIsCurrent() { assertEquals(UpdateAvailability.CURRENT, UpdateRules.availability(release, 2, 35)) }
    @Test fun olderVersionNeverOffersDowngrade() { assertEquals(UpdateAvailability.CURRENT, UpdateRules.availability(release, 3, 35)) }
    @Test fun newerVersionRequiringNewerAndroidIsUnsupported() { assertEquals(UpdateAvailability.UNSUPPORTED, UpdateRules.availability(release.copy(minSdk = 36), 1, 35)) }
    @Test fun validRepositoryReleaseIsAccepted() { assertEquals(release, UpdateRules.validate(release)) }
    @Test fun foreignOrInsecureApkUrlsAreRejected() {
        listOf(
            release.apkUrl.replace("https:", "http:"),
            release.apkUrl.replace("raw.githubusercontent.com", "raw.githubusercontent.com.evil.example"),
            release.apkUrl.replace("selfness-byte/mapswork", "other/project"),
            release.apkUrl + "?token=secret", release.apkUrl + "#fragment",
            release.apkUrl.replace("raw.githubusercontent.com/", "user@raw.githubusercontent.com/"),
            release.apkUrl.replace("/downloads/", "/downloads/../"),
            release.apkUrl.replace("0.2.0", "0.3.0"),
        ).forEach { assertFailsWith<IllegalArgumentException>(it) { UpdateRules.validate(release.copy(apkUrl = it)) } }
    }
    @Test fun malformedMetadataIsRejected() {
        listOf(release.copy(versionCode = 0), release.copy(versionName = ""), release.copy(minSdk = 25),
            release.copy(sha256 = "xyz"), release.copy(notes = "x".repeat(4001)))
            .forEach { assertFailsWith<IllegalArgumentException> { UpdateRules.validate(it) } }
    }
    @Test fun samePackageNewerVersionAndSameSignerAreAccepted() { assertTrue(UpdateRules.matchesIdentity(release, installed, candidate)) }
    @Test fun anotherPackageIsRejected() { assertFalse(UpdateRules.matchesIdentity(release, installed, candidate.copy(packageName = "other.app"))) }
    @Test fun anotherSignerIsRejected() { assertFalse(UpdateRules.matchesIdentity(release, installed, candidate.copy(signerSha256 = setOf("other")))) }
    @Test fun missingSignerIsRejected() { assertFalse(UpdateRules.matchesIdentity(release, installed, candidate.copy(signerSha256 = emptySet()))) }
    @Test fun unexpectedApkVersionIsRejected() { assertFalse(UpdateRules.matchesIdentity(release, installed, candidate.copy(versionCode = 3))) }
    @Test fun alreadyInstalledCandidateIsRejected() { assertFalse(UpdateRules.matchesIdentity(release, candidate, candidate)) }
}
