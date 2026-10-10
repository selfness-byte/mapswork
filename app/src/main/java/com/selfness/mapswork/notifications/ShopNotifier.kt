package com.selfness.mapswork.notifications

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.selfness.mapswork.MainActivity
import com.selfness.mapswork.R
import com.selfness.mapswork.core.Shop
import com.selfness.mapswork.location.DeviceStateReader

class ShopNotifier(private val context: Context) {
    init {
        val channel = NotificationChannel(CHANNEL_ID, "저장한 상점 근처 알림", NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "앱에 등록한 상점 근처에 들어왔을 때 알려 줍니다."
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun notify(shop: Shop): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled() || (Build.VERSION.SDK_INT >= 33 &&
            !DeviceStateReader.granted(context, Manifest.permission.POST_NOTIFICATIONS))) return false
        if (context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL_ID)
                ?.importance == NotificationManager.IMPORTANCE_NONE) return false
        val intent = Intent(context, MainActivity::class.java)
            .setData(Uri.parse("mapswork://shop/${Uri.encode(shop.id)}"))
            .putExtra(MainActivity.SHOP_ID_EXTRA, shop.id)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pin)
            .setContentTitle("저장한 상점 근처에 도착했어요")
            .setContentText("${shop.name} · 설정 반경 ${shop.radiusMeters}m 안에 있어요")
            .setContentIntent(pending).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        return try {
            manager.notify(shop.id, 0, notification)
            true
        } catch (_: SecurityException) { false }
    }

    fun cancel(id: String) { NotificationManagerCompat.from(context).cancel(id, 0) }

    companion object { const val CHANNEL_ID = "nearby-shops" }
}
