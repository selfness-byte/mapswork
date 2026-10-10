package com.selfness.mapswork.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.selfness.mapswork.MainActivity
import com.selfness.mapswork.R
import com.selfness.mapswork.core.UpdateRelease
import com.selfness.mapswork.location.DeviceStateReader

class UpdateNotifier(private val context: Context) {
    init {
        val channel = NotificationChannel(CHANNEL_ID, "앱 업데이트", NotificationManager.IMPORTANCE_LOW)
        channel.description = "이곳의 새 버전이 있으면 알려 줍니다."
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun notify(release: UpdateRelease): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled() || (Build.VERSION.SDK_INT >= 33 &&
                !DeviceStateReader.granted(context, Manifest.permission.POST_NOTIFICATIONS))) return false
        if (context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL_ID)
                ?.importance == NotificationManager.IMPORTANCE_NONE) return false
        val intent = Intent(context, MainActivity::class.java).setData(Uri.parse("mapswork://updates"))
            .putExtra(MainActivity.UPDATE_EXTRA, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID).setSmallIcon(R.drawable.ic_pin)
            .setContentTitle("이곳 새 버전이 있어요")
            .setContentText("${release.versionName} · 눌러서 확인하고 설치하세요")
            .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS).build()
        return try { manager.notify(TAG, 0, notification); true }
        catch (_: SecurityException) { false }
    }

    fun cancel() { NotificationManagerCompat.from(context).cancel(TAG, 0) }

    companion object {
        const val CHANNEL_ID = "app-updates"
        private const val TAG = "mapswork-update"
    }
}
