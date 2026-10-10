package com.selfness.mapswork.location

import com.selfness.mapswork.core.Transition
import kotlinx.coroutines.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.*

class GeofenceEventQueueTest {
    @Test fun delayedEnterCannotBeOvertakenByExit() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val enterFinished = CompletableDeferred<Unit>()
        val exitFinished = CompletableDeferred<Unit>()
        val processed = mutableListOf<Transition>()
        try {
            val queue = GeofenceEventQueue(scope, process = { _, transition, _ ->
                if (transition == Transition.ENTER) { entered.complete(Unit); release.await() }
                processed += transition
            }, onFailure = { error("Unexpected event failure") })
            queue.submit(listOf("shop:1"), Transition.ENTER, 1) { enterFinished.complete(Unit) }
            withTimeout(2_000) { entered.await() }
            queue.submit(listOf("shop:1"), Transition.EXIT, 2) { exitFinished.complete(Unit) }
            assertNull(withTimeoutOrNull(100) { exitFinished.await() }, "EXIT overtook the suspended ENTER")
            release.complete(Unit)
            withTimeout(2_000) { enterFinished.await(); exitFinished.await() }
            assertEquals(listOf(Transition.ENTER, Transition.EXIT), processed)
        } finally { release.complete(Unit); scope.cancel() }
    }

    @Test fun failedEventFinishesAndDoesNotPreventFollowingEvent() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val failures = AtomicInteger()
        val processed = AtomicInteger()
        val finished = CompletableDeferred<Unit>()
        val firstFinished = CompletableDeferred<Unit>()
        try {
            val queue = GeofenceEventQueue(scope, process = { _, transition, _ ->
                if (transition == Transition.ENTER) throw IllegalStateException("DB unavailable")
                processed.incrementAndGet()
            }, onFailure = { failures.incrementAndGet() })
            queue.submit(listOf("shop:1"), Transition.ENTER, 1) { firstFinished.complete(Unit) }
            queue.submit(listOf("shop:1"), Transition.EXIT, 2) { finished.complete(Unit) }
            withTimeout(2_000) { firstFinished.await(); finished.await() }
            assertEquals(1, failures.get())
            assertEquals(1, processed.get())
        } finally { scope.cancel() }
    }

    @Test fun queuedEventDeadlineIncludesTimeWaitingForEarlierEvent() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val clock = AtomicLong()
        val failures = AtomicInteger()
        val processed = AtomicInteger()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        try {
            val queue = GeofenceEventQueue(scope, process = { _, transition, _ ->
                if (transition == Transition.ENTER) { entered.complete(Unit); release.await() }
                processed.incrementAndGet()
            }, onFailure = { failures.incrementAndGet() }, timeoutMillis = 500,
                elapsedMillis = clock::get)
            queue.submit(listOf("shop:1"), Transition.ENTER, 1) {}
            withTimeout(2_000) { entered.await() }
            queue.submit(listOf("shop:1"), Transition.EXIT, 2) { finished.complete(Unit) }
            clock.set(1_000)
            release.complete(Unit)
            withTimeout(2_000) { finished.await() }
            assertEquals(1, processed.get())
            assertEquals(1, failures.get())
        } finally { release.complete(Unit); scope.cancel() }
    }
}
