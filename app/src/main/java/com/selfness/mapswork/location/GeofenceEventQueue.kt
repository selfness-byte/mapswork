package com.selfness.mapswork.location

import com.selfness.mapswork.core.Transition
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

class GeofenceEventQueue(
    scope: CoroutineScope,
    private val process: suspend (List<String>, Transition, Long) -> Unit,
    private val onFailure: () -> Unit,
    private val timeoutMillis: Long = 8_000,
    private val elapsedMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private data class Entry(val ids: List<String>, val transition: Transition,
        val receivedAtMillis: Long, val deadline: Long, val finish: () -> Unit)
    private val events = Channel<Entry>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (entry in events) {
                try {
                    val remaining = entry.deadline - elapsedMillis()
                    if (remaining <= 0) reportFailure()
                    else withTimeout(remaining) { process(entry.ids, entry.transition, entry.receivedAtMillis) }
                } catch (_: TimeoutCancellationException) { reportFailure() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { reportFailure() }
                finally { runCatching(entry.finish) }
            }
        }.invokeOnCompletion {
            events.close()
            while (true) {
                val abandoned = events.tryReceive().getOrNull() ?: break
                runCatching(abandoned.finish)
            }
        }
    }

    // Called synchronously by onReceive: enqueue order is broadcast arrival order,
    // even when the consumer suspends before reaching its Room transaction.
    fun submit(ids: List<String>, transition: Transition, receivedAtMillis: Long, finish: () -> Unit) {
        val entry = Entry(ids.toList(), transition, receivedAtMillis, elapsedMillis() + timeoutMillis, finish)
        if (events.trySend(entry).isFailure) {
            runCatching(finish)
            reportFailure()
        }
    }

    private fun reportFailure() { runCatching(onFailure) }
}
