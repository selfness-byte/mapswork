package com.selfness.mapswork.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.selfness.mapswork.MainActivity
import com.selfness.mapswork.MapsworkApplication
import com.selfness.mapswork.core.GeoPoint
import com.selfness.mapswork.core.ShopDraft
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import android.os.Looper
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = MapsworkApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivitySmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @After fun cleanup() {
        val container = (compose.activity.application as MapsworkApplication).container
        container.scope.cancel()
        container.database.close()
    }

    @Test fun initialLaunchShowsNameAndRegistrationAction() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.onNodeWithText("이곳").assertIsDisplayed()
        compose.onNodeWithContentDescription("상점 추가").assertIsDisplayed().assertHasClickAction()
    }

    @Test fun settingsCanBeOpenedFromTheList() {
        compose.onNodeWithContentDescription("설정").performClick()
        compose.onNodeWithText("근처 알림 사용").assertIsDisplayed()
        compose.onNodeWithText("정확한 위치").performScrollTo().assertIsDisplayed()
    }

    @Test fun settingsExposeManualUpdateCheckAndCurrentVersion() {
        compose.onNodeWithContentDescription("설정").performClick()
        compose.onNodeWithText("업데이트").assertIsDisplayed()
        compose.onNodeWithText("현재 버전 0.3.0").assertIsDisplayed()
        compose.onNodeWithText("업데이트 확인").assertIsDisplayed().assertHasClickAction()
    }

    @Test fun shopEditorProvidesNameSearchAndExplicitGoogleMapConnectionState() {
        compose.onNodeWithContentDescription("상점 추가").performClick()
        compose.onNodeWithText("상점 이름 검색").assertIsDisplayed()
        compose.onNodeWithText("내 위치").assertIsDisplayed()
        compose.onNodeWithText("Google 지도 연결 대기").assertIsDisplayed()
    }

    @Test fun automaticUpdateChecksCanBeTurnedOffInSettings() {
        compose.onNodeWithContentDescription("설정").performClick()
        compose.onNodeWithContentDescription("새 버전 자동 확인").assertIsOn().performClick().assertIsOff()
        val container = (compose.activity.application as MapsworkApplication).container
        kotlin.test.assertFalse(container.settings.automaticUpdates.value)
    }

    @Test fun updateNotificationIntentOpensSettingsAfterRecreation() {
        compose.activity.intent.putExtra(MainActivity.UPDATE_EXTRA, true)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("업데이트").assertIsDisplayed()
        compose.onNodeWithContentDescription("새 버전 자동 확인").assertIsDisplayed()
    }

    @Test fun savedPlaceAppearsInTheVisibleList() {
        val container = (compose.activity.application as MapsworkApplication).container
        runBlocking { container.repository.save(ShopDraft("테스트 카페", GeoPoint(37.5, 127.0), enabled = false), null) }
        compose.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.onAllNodesWithText("테스트 카페").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("테스트 카페").assertIsDisplayed()
    }
}
