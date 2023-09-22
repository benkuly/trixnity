package net.folivo.trixnity.core

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.reflect.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import net.folivo.trixnity.core.EventEmitter.Priority
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.EventContent
import kotlin.reflect.KClass

private val log = KotlinLogging.logger { }

typealias Subscriber<T> = suspend (T) -> Unit
typealias Unsubscriber = () -> Unit

interface EventEmitter<T : List<Event<*>>> {
    object Priority {
        const val FIRST = Int.MAX_VALUE

        const val ONE_TIME_KEYS = 24_000
        const val DEVICE_LISTS = ONE_TIME_KEYS - 1
        const val TO_DEVICE_EVENTS = DEVICE_LISTS - 1
        const val ROOM_LIST = TO_DEVICE_EVENTS - 1

        const val DEFAULT = 0
        const val BEFORE_DEFAULT = DEFAULT + 1
        const val AFTER_DEFAULT = DEFAULT - 1

        const val LAST = Int.MIN_VALUE
    }

    /**
     * Subscribers have to be aware to unsubscribe when the scope of the subscriber is destroyed.
     *
     * @return A function to unsubscribe.
     */
    fun subscribe(priority: Int = Priority.DEFAULT, subscriber: Subscriber<T>): Unsubscriber

    suspend fun emit(events: T)
}

abstract class EventEmitterImpl<T : List<Event<*>>> : EventEmitter<T> {
    private data class PrioritySubscribers<T : List<Event<*>>>(
        val subscribers: Set<Subscriber<T>>,
        val priority: Int,
    )

    private val _subscribers: MutableStateFlow<List<PrioritySubscribers<T>>> = MutableStateFlow(listOf())

    override fun subscribe(priority: Int, subscriber: Subscriber<T>): Unsubscriber {
        _subscribers.update { oldList ->
            val existingPriority = oldList.find { it.priority == priority }
            val newList =
                if (existingPriority != null)
                    oldList - existingPriority + existingPriority.copy(subscribers = existingPriority.subscribers + subscriber)
                else
                    oldList + PrioritySubscribers(setOf(subscriber), priority)
            newList.sortedByDescending { it.priority }
        }
        return {
            _subscribers.update { oldList ->
                oldList.map {
                    it.copy(subscribers = it.subscribers - subscriber)
                }.filterNot { it.subscribers.isEmpty() }
                    .sortedByDescending { it.priority }
            }
        }
    }

    override suspend fun emit(events: T) {
        _subscribers.value.forEach { prioritySubscribers ->
            coroutineScope {
                log.trace { "process value in subscribers ${prioritySubscribers.subscribers}" }
                prioritySubscribers.subscribers.forEach { subscriber ->
                    launch { subscriber(events) }
                }
            }
        }
    }
}

/**
 * Subscribers have to be aware to unsubscribe when the scope of the subscriber is destroyed.
 *
 * @return A function to unsubscribe.
 */
fun <C : EventContent> EventEmitter<*>.subscribeContent(
    contentClass: KClass<C>,
    priority: Int,
    subscriber: Subscriber<Event<C>>
) = subscribe(priority) { events ->
    events.filter { it.content.instanceOf(contentClass) }.filterIsInstance<Event<C>>()
        .forEach { subscriber(it) }
}

/**
 * Subscribers have to be aware to unsubscribe when the scope of the subscriber is destroyed.
 *
 * @return A function to unsubscribe.
 */
fun <C : EventContent> EventEmitter<*>.subscribeContentList(
    contentClass: KClass<C>,
    priority: Int,
    subscriber: Subscriber<List<Event<C>>>
) = subscribe(priority) { events ->
    subscriber(
        events.filter { it.content.instanceOf(contentClass) }.filterIsInstance<Event<C>>()
    )
}

/**
 * Subscribers have to be aware to unsubscribe when the scope of the subscriber is destroyed.
 *
 * @return A function to unsubscribe.
 */
inline fun <reified C : EventContent> EventEmitter<*>.subscribeContent(
    priority: Int = Priority.DEFAULT,
    noinline subscriber: Subscriber<Event<C>>
) = subscribeContent(C::class, priority, subscriber)

/**
 * Subscribers have to be aware to unsubscribe when the scope of the subscriber is destroyed.
 *
 * @return A function to unsubscribe.
 */
inline fun <reified C : EventContent> EventEmitter<*>.subscribeContentList(
    priority: Int = Priority.DEFAULT,
    noinline subscriber: Subscriber<List<Event<C>>>
) = subscribeContentList(C::class, priority, subscriber)

/**
 * Subscribers have to be aware to unsubscribe when the scope of the subscriber is destroyed.
 *
 * @return A function to unsubscribe.
 */
fun <C : EventContent, E : Event<C>> EventEmitter<*>.subscribeEvent(
    contentClass: KClass<C>,
    eventClass: KClass<E>,
    priority: Int,
    subscriber: Subscriber<E>
) = subscribe(priority) { events ->
    events.filter { it.instanceOf(eventClass) }.filter { it.content.instanceOf(contentClass) }
        .forEach { @Suppress("UNCHECKED_CAST") subscriber(it as E) }
}

/**
 * Subscribers have to be aware to unsubscribe when the scope of the subscriber is destroyed.
 *
 * @return A function to unsubscribe.
 */
fun <C : EventContent, E : Event<C>> EventEmitter<*>.subscribeEventList(
    contentClass: KClass<C>,
    eventClass: KClass<E>,
    priority: Int,
    subscriber: Subscriber<List<E>>
) = subscribe(priority) { events ->
    val filteredEvents = events.filter { it.instanceOf(eventClass) }.filter { it.content.instanceOf(contentClass) }

    @Suppress("UNCHECKED_CAST")
    val typedFilteredEvents = filteredEvents as List<E>
    subscriber(typedFilteredEvents)
}

/**
 * Subscribers have to be aware to unsubscribe when the scope of the subscriber is destroyed.
 *
 * @return A function to unsubscribe.
 */
inline fun <reified C : EventContent, reified E : Event<C>> EventEmitter<*>.subscribeEvent(
    priority: Int = Priority.DEFAULT,
    noinline subscriber: Subscriber<E>
) = subscribeEvent(C::class, E::class, priority, subscriber)

/**
 * Subscribers have to be aware to unsubscribe when the scope of the subscriber is destroyed.
 *
 * @return A function to unsubscribe.
 */
inline fun <reified C : EventContent, reified E : Event<C>> EventEmitter<*>.subscribeEventList(
    priority: Int = Priority.DEFAULT,
    noinline subscriber: Subscriber<List<E>>
) = subscribeEventList(C::class, E::class, priority, subscriber)

/**
 * Subscribers have to be aware to unsubscribe when the scope of the subscriber is destroyed.
 *
 * @return A function to unsubscribe.
 */
fun EventEmitter<*>.subscribe(
    priority: Int = Priority.DEFAULT,
    subscriber: suspend () -> Unit
) = subscribe(priority) { subscriber() }

/**
 * Subscribers have to be aware to unsubscribe when the scope of the subscriber is destroyed.
 *
 * @return A function to unsubscribe.
 */
fun EventEmitter<*>.subscribeEachEvent(priority: Int = Priority.DEFAULT, subscriber: Subscriber<Event<*>>) =
    subscribe(priority) { events -> events.forEach { subscriber(it) } }

/**
 * Subscribe events with a flow.
 *
 * If you want, that exceptions are passed to the sync loop (so sync is cancelled on an error),
 * you should use [subscribeContent] and unsubscribe.
 */
inline fun <reified C : EventContent> EventEmitter<*>.subscribeContentAsFlow(priority: Int = Priority.DEFAULT): Flow<Event<C>> =
    callbackFlow {
        val unsubscribe = subscribeContent(priority) { send(it) }
        awaitClose { unsubscribe() }
    }

/**
 * Subscribe events with a flow.
 *
 * If you want, that exceptions are passed to the sync loop (so sync is cancelled on an error),
 * you should use [subscribeContent] and unsubscribe.
 */
inline fun <reified C : EventContent> EventEmitter<*>.subscribeContentListAsFlow(priority: Int = Priority.DEFAULT): Flow<List<Event<C>>> =
    callbackFlow {
        val unsubscribe = subscribeContentList(priority) { send(it) }
        awaitClose { unsubscribe() }
    }

/**
 * Subscribe events with a flow.
 *
 * If you want, that exceptions are passed to the sync loop (so sync is cancelled on an error),
 * you should use [subscribeContent] and unsubscribe.
 */
inline fun <reified C : EventContent, reified E : Event<C>> EventEmitter<*>.subscribeEventAsFlow(priority: Int = Priority.DEFAULT): Flow<E> =
    callbackFlow {
        val unsubscribe = subscribeEvent<C, E>(priority) { send(it) }
        awaitClose { unsubscribe() }
    }

/**
 * Subscribe events with a flow.
 *
 * If you want, that exceptions are passed to the sync loop (so sync is cancelled on an error),
 * you should use [subscribeContent] and unsubscribe.
 */
inline fun <reified C : EventContent, reified E : Event<C>> EventEmitter<*>.subscribeEventListAsFlow(priority: Int = Priority.DEFAULT): Flow<List<E>> =
    callbackFlow {
        val unsubscribe = subscribeEventList<C, E>(priority) { send(it) }
        awaitClose { unsubscribe() }
    }

/**
 * Subscribe events with a flow.
 *
 * If you want, that exceptions are passed to the sync loop (so sync is cancelled on an error),
 * you should use [subscribeContent] and unsubscribe.
 */
fun EventEmitter<*>.subscribeEachEventAsFlow(priority: Int = Priority.DEFAULT): Flow<Event<*>> =
    callbackFlow {
        val unsubscribe = subscribeEachEvent(priority) { send(it) }
        awaitClose { unsubscribe() }
    }

/**
 * Subscribe with a flow.
 *
 * If you want, that exceptions are passed to the sync loop (so sync is cancelled on an error),
 * you should use [subscribeContent] and unsubscribe.
 */
fun <T : List<Event<*>>> EventEmitter<T>.subscribeAsFlow(priority: Int = Priority.DEFAULT): Flow<T> = callbackFlow {
    val unsubscribe = subscribe(priority) { send(it) }
    awaitClose { unsubscribe() }
}

/**
 * Subscribe with a flow.
 *
 * If you want, that exceptions are passed to the sync loop (so sync is cancelled on an error),
 * you should use [subscribeContent] and unsubscribe.
 */
fun EventEmitter<*>.subscribeChangeAsFlow(priority: Int = Priority.DEFAULT): Flow<Unit> = callbackFlow {
    val unsubscribe = subscribe(priority) { send(Unit) }
    awaitClose { unsubscribe() }
}

fun Unsubscriber.unsubscribeOnCompletion(coroutineScope: CoroutineScope) =
    coroutineScope.coroutineContext.job.invokeOnCompletion { this() }