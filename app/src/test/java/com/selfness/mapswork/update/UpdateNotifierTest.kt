package com.selfness.mapswork.update

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import com.selfness.mapswork.MainActivity
import com.selfness.mapswork.core.UpdateRelease
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class UpdateNotifierTest {
    private lateinit var context: Application
    private lateinit var notifier: UpdateNotifier
    private lateinit var manager: NotificationManager
    private val release = UpdateRelease(4, "0.4.0", 26,
        "https://raw.githubusercontent.com/selfness-byte/mapswork/codex/nearby-shop-alert/downloads/mapswork-0.4.0-test.apk",
        "a".repeat(64), "업데이트")

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        manager = context.getSystemService(NotificationManager::class.java)
        notifier = UpdateNotifier(context)
    }
    @Test fun anUpdateNotificationShowsTheVersionAndOpensUpdates() {
        assertTrue(notifier.notify(release))
        val notification = shadowOf(manager).allNotifications.single()
        assertEquals("이곳 새 버전이 있어요", notification.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(notification.extras.getString(Notification.EXTRA_TEXT)!!.contains("0.4.0"))
        val intent = shadowOf(notification.contentIntent).savedIntent
        assertTrue(intent.getBooleanExtra(MainActivity.UPDATE_EXTRA, false))
        assertNull(intent.getStringExtra(MainActivity.SHOP_ID_EXTRA))
        assertEquals("mapswork://updates", intent.data.toString())
    }
    @Test fun aDeniedNotificationReturnsFalseWithoutPosting() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(notifier.notify(release))
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }
    @Test fun aBlockedUpdateChannelIsNotAcknowledged() {
        val channel = manager.getNotificationChannel(UpdateNotifier.CHANNEL_ID)
        channel.importance = NotificationManager.IMPORTANCE_NONE
        manager.createNotificationChannel(channel)
        assertFalse(notifier.notify(release))
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }
}
