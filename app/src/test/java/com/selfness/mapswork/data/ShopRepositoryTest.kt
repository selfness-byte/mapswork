package com.selfness.mapswork.data

import android.app.Application
import androidx.room.Room
import com.selfness.mapswork.core.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ShopRepositoryTest {
    private lateinit var db: MapsworkDatabase
    private lateinit var settings: AppSettings
    private lateinit var repository: ShopRepository
    private val draft = ShopDraft("  커피 집  ", GeoPoint(37.5, 127.0), 200, true)

    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("mapswork-settings", 0).edit().clear().commit()
        settings = AppSettings(context).also { it.setAlertsEnabled(true) }
        db = Room.inMemoryDatabaseBuilder(context, MapsworkDatabase::class.java).build()
        repository = ShopRepository(db, settings)
    }

    @After fun cleanup() { db.close() }

    private suspend fun saveShop(): Shop {
        assertTrue(repository.save(draft, null).isEmpty())
        return repository.all().single()
    }

    @Test fun savesNameWithoutRemovingInternalSpaces() = runBlocking {
        assertEquals("커피 집", saveShop().name)
    }

    @Test fun duplicateEnterEventsProduceOneNotificationCandidate() = runBlocking {
        val shop = saveShop()
        assertEquals(1, repository.consumeTransitions(listOf(shop.geofenceRequestId, shop.geofenceRequestId), Transition.ENTER, 2_000_000).size)
        assertTrue(repository.consumeTransitions(listOf(shop.geofenceRequestId), Transition.ENTER, 4_000_000).isEmpty())
        assertEquals(2_000_000L, repository.find(shop.id)?.lastNotifiedAtMillis)
    }

    @Test fun deletedShopIsIgnoredByLateEvent() = runBlocking {
        val shop = saveShop()
        repository.delete(shop.id)
        assertTrue(repository.consumeTransitions(listOf(shop.geofenceRequestId), Transition.ENTER, 2_000_000).isEmpty())
    }

    @Test fun disabledShopIsIgnoredByLateEvent() = runBlocking {
        val shop = saveShop()
        assertTrue(repository.setEnabled(shop.id, false).isEmpty())
        assertTrue(repository.consumeTransitions(listOf(shop.geofenceRequestId), Transition.ENTER, 2_000_000).isEmpty())
    }

    @Test fun eventForPreviousPinIsIgnoredAfterMove() = runBlocking {
        val original = saveShop()
        repository.save(draft.copy(point = GeoPoint(37.6, 127.1)), original.id)
        assertTrue(repository.consumeTransitions(listOf(original.geofenceRequestId), Transition.ENTER, 2_000_000).isEmpty())
        val moved = repository.find(original.id)!!
        assertEquals(1, repository.consumeTransitions(listOf(moved.geofenceRequestId), Transition.ENTER, 2_000_000).size)
    }

    @Test fun editingDeletedShopDoesNotRecreateIt() = runBlocking {
        val shop = saveShop()
        repository.delete(shop.id)
        assertContains(repository.save(draft, shop.id), ValidationError.NOT_FOUND)
        assertTrue(repository.all().isEmpty())
    }

    @Test fun globalPauseSuppressesEventsWithoutConsumingVisit() = runBlocking {
        val shop = saveShop()
        settings.setAlertsEnabled(false)
        assertTrue(repository.consumeTransitions(listOf(shop.geofenceRequestId), Transition.ENTER, 2_000_000).isEmpty())
        assertEquals(VisitState.UNKNOWN, repository.find(shop.id)?.visitState)
    }

    @Test fun nameEditPreservesVisitAndLastNotification() = runBlocking {
        val shop = saveShop()
        repository.consumeTransitions(listOf(shop.geofenceRequestId), Transition.ENTER, 2_000_000)
        assertTrue(repository.save(draft.copy(name = "새 이름"), shop.id).isEmpty())
        val changed = repository.find(shop.id)!!
        assertEquals(VisitState.INSIDE, changed.visitState)
        assertEquals(2_000_000L, changed.lastNotifiedAtMillis)
    }

    @Test fun movedPinResetsPresenceButKeepsCooldown() = runBlocking {
        val shop = saveShop()
        repository.consumeTransitions(listOf(shop.geofenceRequestId), Transition.ENTER, 2_000_000)
        repository.save(draft.copy(point = GeoPoint(37.6, 127.1)), shop.id)
        assertEquals(VisitState.UNKNOWN, repository.find(shop.id)?.visitState)
        assertTrue(repository.consumeTransitions(listOf(repository.find(shop.id)!!.geofenceRequestId), Transition.ENTER, 2_000_100).isEmpty())
    }

    @Test fun currentLocationInsideDoesNotConsumeInitialEnter() = runBlocking {
        val shop = saveShop()
        repository.reconcilePresence(shop.point)
        assertEquals(1, repository.consumeTransitions(listOf(shop.geofenceRequestId), Transition.ENTER, 2_000_000).size)
    }

    @Test fun currentLocationOutsideRepairsMissedExit() = runBlocking {
        val shop = saveShop()
        repository.consumeTransitions(listOf(shop.geofenceRequestId), Transition.ENTER, 2_000_000)
        repository.reconcilePresence(GeoPoint(38.0, 127.0))
        assertEquals(VisitState.OUTSIDE, repository.find(shop.id)?.visitState)
        assertEquals(1, repository.consumeTransitions(listOf(shop.geofenceRequestId), Transition.ENTER, 3_800_000).size)
    }

    @Test fun databaseReopenKeepsPlacesAndVisitState() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "mapswork-reopen-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, MapsworkDatabase::class.java, name).build()
        val first = open()
        val firstRepository = ShopRepository(first, settings)
        firstRepository.save(draft, null)
        val shop = firstRepository.all().single()
        firstRepository.consumeTransitions(listOf(shop.geofenceRequestId), Transition.ENTER, 2_000_000)
        first.close()
        val second = open()
        try {
            val saved = ShopRepository(second, settings).find(shop.id)!!
            assertEquals("커피 집", saved.name)
            assertEquals(VisitState.INSIDE, saved.visitState)
            assertEquals(2_000_000L, saved.lastNotifiedAtMillis)
        } finally {
            second.close()
            context.deleteDatabase(name)
        }
    }
}
