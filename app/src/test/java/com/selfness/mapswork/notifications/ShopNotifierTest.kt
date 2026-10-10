package com.selfness.mapswork.notifications

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import com.selfness.mapswork.MainActivity
import com.selfness.mapswork.core.GeoPoint
import com.selfness.mapswork.core.Shop
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ShopNotifierTest {
    private lateinit var context: Application
    private lateinit var notifier: ShopNotifier
    private lateinit var manager: NotificationManager
    private val shop = Shop("cafe", "커피 집", GeoPoint(37.5, 127.0))

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        manager = context.getSystemService(NotificationManager::class.java)
        notifier = ShopNotifier(context)
    }

    @Test fun notificationHasReadableShopAndRadius() {
        assertTrue(notifier.notify(shop))
        val notification = shadowOf(manager).allNotifications.single()
        assertEquals("저장한 상점 근처에 도착했어요", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("커피 집 · 설정 반경 200m 안에 있어요", notification.extras.getString(Notification.EXTRA_TEXT))
    }

    @Test fun notificationOpensTheCorrectShop() {
        notifier.notify(shop)
        val pending = shadowOf(manager).allNotifications.single().contentIntent
        val intent = shadowOf(pending).savedIntent
        assertEquals(shop.id, intent.getStringExtra(MainActivity.SHOP_ID_EXTRA))
        assertEquals("mapswork://shop/cafe", intent.data.toString())
    }

    @Test fun sameHashShopIdsHaveIndependentNotificationsAndIntents() {
        val first = shop.copy(id = "FB")
        val second = shop.copy(id = "Ea")
        assertEquals(first.id.hashCode(), second.id.hashCode())
        notifier.notify(first)
        notifier.notify(second)
        val intents = shadowOf(manager).allNotifications.map { shadowOf(it.contentIntent).savedIntent }
        assertEquals(setOf("FB", "Ea"), intents.map { it.getStringExtra(MainActivity.SHOP_ID_EXTRA) }.toSet())
    }

    @Test fun deniedNotificationsAreNotPosted() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(notifier.notify(shop))
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    @Test fun cancelRemovesOnlyTheSpecifiedShopNotification() {
        notifier.notify(shop)
        notifier.notify(shop.copy(id = "another"))
        notifier.cancel(shop.id)
        assertEquals(1, shadowOf(manager).allNotifications.size)
    }
}
