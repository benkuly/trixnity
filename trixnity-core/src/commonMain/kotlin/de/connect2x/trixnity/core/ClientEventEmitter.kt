package de.connect2x.trixnity.core

import de.connect2x.lognity.api.logger.Logger
import io.ktor.util.reflect.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import de.connect2x.trixnity.core.ClientEventEmitter.Priority
import de.connect2x.trixnity.core.model.events.*
import kotlin.reflect.KClass

private val log = Logger("de.connect2x.trixnity.core.ClientEventEmitter")

typealias Subscriber<T> = suspend (T) -> Unit
typealias Unsubscriber = () -> Unit

interface ClientEventEmitter<T : List<ClientEvent<*>>> {
    object Priority {
        const val FIRST = Int.MAX_VALUE

        const val STORE_EVENTS = 24_000
        const val ROOM_LIST = STORE_EVENTS - 1_000
        const val DEVICE_LISTS = ROOM_LIST - 1_000
        const val TO_DEVICE_EVENTS = DEVICE_LISTS - 1_000
        const val ONE_TIME_KEYS = TO_DEVICE_EVENTS - 1_000
        const val STORE_TIMELINE_EVENTS = TO_DEVICE_EVENTS - 1_000

        const val DEFAULT = 0
        const val BEFORE_DEFAULT = DEFAULT + 1_000
        const val AFTER_DEFAULT = DEFAULT - 1_000

        const val LAST = Int.MIN_VALUE + 1
    }

    /**
     * Subscribers have to be aware to unsubscribe when the scope of the subscriber is destroyed.
     *
     * @return A function to unsubscribe.
     */
    fun subscribe(priority: Int = Priority.DEFAULT, subscriber: Subscriber<T>): Unsubscriber

    suspend fun emit(events: T)
}

abstract class ClientEventEmitterImpl<T : List<ClientEvent<*>>> : ClientEventEmitter<T> {
    private data class PrioritySubscribers<T : List<Event<*>>>(
        val subscribers: Set<Subscriber<T>>,
        val priority: Int,
    )

    private val emitMutex = Mutex()
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
        emitMutex.withLock {
            _subscribers.value.forEach { prioritySubscribers ->
                coroutineScope {
                    log.trace { "process events in subscribers with priority ${prioritySubscribers.priority}" }
                    prioritySubscribers.subscribers.forEach { subscriber ->
                        launch { subscriber(events) }
                    }
                }
            }
        }
    }
}

fun <C : EventContent, E : ClientEvent<out C>> Flow<E>.filterContent(
    contentClass: KClass<out C>,
    eventClass: KClass<out E>? = null
): Flow<E> {
    val allowSpecialContent = eventClass != null && eventClass != ClientEvent::class
            || contentClass == UnknownEventContent::class
            || contentClass == RedactedEventContent::class
    return filter {
        it.content.instanceOf(contentClass) &&
                (allowSpecialContent ||
                        !it.content.instanceOf(UnknownEventContent::class) &&
                        !it.content.instanceOf(RedactedEventContent::class))
    }
}

/**
 * Subscribers have to be aware to unsubscribe when the scope of the subscriber is destroyed.
 *
 * @return A function to unsubscribe.
 */
fun <C : EventContent> ClientEventEmitter<*>.subscribeContent(
    contentClass: KClass<C>,
    priority: Int,
    subscriber: Subscriber<ClientEvent<C>>
) = subscribe(priority) { events ->
    events.asFlow().filterContent(contentClass).filterIsInstance<ClientEvent<C>>().collect { subscriber(it) }
}

/**
 * Subscribers have to be aware to unsubscribe when the scope of the subscriber is destroyed.
 *
 * @return A function to unsubscribe.
 */
fun <C : EventContent> ClientEventEmitter<*>.subscribeContentList(
    contentClass: KClass<C>,
    priority: Int,
    subscriber: Subscriber<List<ClientEvent<C>>>
) = subscribe(priority) { events ->
    subscriber(
        events.asFlow().filterContent(contentClass).filterIsInstance<ClientEvent<C>>().toList()
    )
}

/**
 * Subscribers have to be aware to unsubscribe when the scope of the subscriber is destroyed.
 *
 * @return A function to unsubscribe.
 */
inline fun <reified C : EventContent> ClientEventEmitter<*>.subscribeContent(
    priority: Int = Priority.DEFAULT,
    noinline subscriber: Subscriber<ClientEvent<C>>
) = subscribeContent(C::class, priority, subscriber)

/**
 * Subscribers have to be aware to unsubscribe when the scope of the subscriber is destroyed.
 *
 * @return A function to unsubscribe.
 */
inline fun <reified C : EventContent> ClientEventEmitter<*>.subscribeContentList(
    priority: Int = Priority.DEFAULT,
    noinline subscriber: Subscriber<List<ClientEvent<C>>>
) = subscribeContentList(C::class, priority, subscriber)

/**
 * Subscribers have to be aware to unsubscribe when the scope of the subscriber is destroyed.
 *
 * @return A function to unsubscribe.
 */
fun <C : EventContent, E : ClientEvent<C>> ClientEventEmitter<*>.subscribeEvent(
    contentClass: KClass<C>,
    eventClass: KClass<E>,
    priority: Int,
    subscriber: Subscriber<E>
) = subscribe(priority) { events ->
    events.asFlow().filter { it.instanceOf(eventClass) }.filterContent(contentClass, eventClass)
        .collect { @Suppress("UNCHECKED_CAST") subscriber(it as E) }
}

/**
 * Subscribers have to be aware to unsubscribe when the scope of the subscriber is destroyed.
 *
 * @return A function to unsubscribe.
 */
fun <C : EventContent, E : ClientEvent<C>> ClientEventEmitter<*>.subscribeEventList(
    contentClass: KClass<C>,
    eventClass: KClass<E>,
    priority: Int,
    subscriber: Subscriber<List<E>>
) = subscribe(priority) { events ->
    val filteredEvents =
        events.asFlow().filter { it.instanceOf(eventClass) }.filterContent(contentClass, eventClass).toList()

    @Suppress("UNCHECKED_CAST")
    val typedFilteredEvents = filteredEvents as List<E>
    subscriber(typedFilteredEvents)
}

/**
 * Subscribers have to be aware to unsubscribe when the scope of the subscriber is destroyed.
 *
 * @return A function to unsubscribe.
 */
inline fun <reified C : EventContent, reified E : ClientEvent<C>> ClientEventEmitter<*>.subscribeEvent(
    priority: Int = Priority.DEFAULT,
    noinline subscriber: Subscriber<E>
) = subscribeEvent(C::class, E::class, priority, subscriber)

/**
 * Subscribers have to be aware to unsubscribe when the scope of the subscriber is destroyed.
 *
 * @return A function to unsubscribe.
 */
inline fun <reified C : EventContent, reified E : ClientEvent<C>> ClientEventEmitter<*>.subscribeEventList(
    priority: Int = Priority.DEFAULT,
    noinline subscriber: Subscriber<List<E>>
) = subscribeEventList(C::class, E::class, priority, subscriber)

/**
 * Subscribers have to be aware to unsubscribe when the scope of the subscriber is destroyed.
 *
 * @return A function to unsubscribe.
 */
fun ClientEventEmitter<*>.subscribe(
    priority: Int = Priority.DEFAULT,
    subscriber: suspend () -> Unit
) = subscribe(priority) { subscriber() }

/**
 * Subscribers have to be aware to unsubscribe when the scope of the subscriber is destroyed.
 *
 * @return A function to unsubscribe.
 */
fun ClientEventEmitter<*>.subscribeEachEvent(priority: Int = Priority.DEFAULT, subscriber: Subscriber<ClientEvent<*>>) =
    subscribe(priority) { events -> events.forEach { subscriber(it) } }

/**
 * Subscribe events with a flow.
 *
 * If you want, that exceptions are passed to the sync loop (so sync is cancelled on an error),
 * you should use [subscribeContent] and unsubscribe.
 */
inline fun <reified C : EventContent> ClientEventEmitter<*>.subscribeContentAsFlow(priority: Int = Priority.DEFAULT): Flow<ClientEvent<C>> =
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
inline fun <reified C : EventContent> ClientEventEmitter<*>.subscribeContentListAsFlow(priority: Int = Priority.DEFAULT): Flow<List<ClientEvent<C>>> =
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
inline fun <reified C : EventContent, reified E : ClientEvent<C>> ClientEventEmitter<*>.subscribeEventAsFlow(priority: Int = Priority.DEFAULT): Flow<E> =
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
inline fun <reified C : EventContent, reified E : ClientEvent<C>> ClientEventEmitter<*>.subscribeEventListAsFlow(
    priority: Int = Priority.DEFAULT
): Flow<List<E>> =
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
fun ClientEventEmitter<*>.subscribeEachEventAsFlow(priority: Int = Priority.DEFAULT): Flow<ClientEvent<*>> =
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
fun <T : List<ClientEvent<*>>> ClientEventEmitter<T>.subscribeAsFlow(priority: Int = Priority.DEFAULT): Flow<T> =
    callbackFlow {
        val unsubscribe = subscribe(priority) { send(it) }
        awaitClose { unsubscribe() }
    }

/**
 * Subscribe with a flow.
 *
 * If you want, that exceptions are passed to the sync loop (so sync is cancelled on an error),
 * you should use [subscribeContent] and unsubscribe.
 */
fun ClientEventEmitter<*>.subscribeChangeAsFlow(priority: Int = Priority.DEFAULT): Flow<Unit> = callbackFlow {
    val unsubscribe = subscribe(priority) { send(Unit) }
    awaitClose { unsubscribe() }
}

fun Unsubscriber.unsubscribeOnCompletion(coroutineScope: CoroutineScope) =
    coroutineScope.coroutineContext.job.invokeOnCompletion { this() }