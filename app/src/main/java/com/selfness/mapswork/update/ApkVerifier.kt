package com.selfness.mapswork.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.selfness.mapswork.core.*
import java.io.File
import java.security.MessageDigest

class ApkVerifier(private val context: Context) {
    @Suppress("DEPRECATION")
    fun verify(file: File, release: UpdateRelease) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw UpdateProblem("설치할 수 없는 APK 파일이에요. 다시 받아 주세요.")
        if (!UpdateRules.matchesIdentity(release, identity(installed), identity(archive))) {
            throw UpdateProblem("이 앱의 서명·버전과 맞지 않는 파일이에요. 설치하지 않았어요.")
        }
        if ((archive.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE) > Build.VERSION.SDK_INT) {
            throw UpdateProblem("이 업데이트는 더 최신 Android가 필요해요.")
        }
    }

    @Suppress("DEPRECATION")
    private fun identity(info: PackageInfo): ApkIdentity {
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        val digests = signatures.orEmpty().map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
        return ApkIdentity(info.packageName, PackageInfoCompat.getLongVersionCode(info), digests)
    }
}
