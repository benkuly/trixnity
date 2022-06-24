package net.folivo.trixnity.core

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mu.KotlinLogging
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.EventContent
import kotlin.reflect.KClass

typealias EventSubscriber<T> = suspend (Event<T>) -> Unit

private val log = KotlinLogging.logger { }

interface IEventEmitter {
    fun <T : EventContent> subscribe(clazz: KClass<T>, subscriber: EventSubscriber<T>)
    fun <T : EventContent> unsubscribe(clazz: KClass<T>, subscriber: EventSubscriber<T>)
    fun subscribeAllEvents(subscriber: EventSubscriber<EventContent>)
    fun unsubscribeAllEvents(subscriber: EventSubscriber<EventContent>)
}

abstract class EventEmitter : IEventEmitter {
    private val eventSubscribers =
        MutableStateFlow<Map<KClass<out EventContent>, Set<EventSubscriber<out EventContent>>>>(mapOf())

    protected suspend fun emitEvent(event: Event<*>) = coroutineScope {
        eventSubscribers.value
            .filterKeys {
                it.isInstance(event.content)
            }
            .forEach { (_, subscribers) ->
                subscribers.forEach {
                    launch {
                        log.trace { "called subscriber: $it" }
                        it.invoke(event)
                    }
                }
            }
    }

    override fun <T : EventContent> subscribe(clazz: KClass<T>, subscriber: EventSubscriber<T>) {
        @Suppress("UNCHECKED_CAST")
        subscriber as EventSubscriber<out EventContent>
        eventSubscribers.update {
            val existingSubscribers = it[clazz]
            val newSubscribers =
                if (existingSubscribers == null) setOf(subscriber)
                else existingSubscribers + subscriber
            it + (clazz to newSubscribers)
        }
    }


    override fun <T : EventContent> unsubscribe(clazz: KClass<T>, subscriber: EventSubscriber<T>) {
        @Suppress("UNCHECKED_CAST")
        subscriber as EventSubscriber<out EventContent>
        eventSubscribers.update {
            val existingSubscribers = it[clazz]
            if (existingSubscribers == null) it
            else it + (clazz to (existingSubscribers - subscriber))
        }
    }

    override fun subscribeAllEvents(subscriber: EventSubscriber<EventContent>) {
        subscribe(subscriber)
    }

    override fun unsubscribeAllEvents(subscriber: EventSubscriber<EventContent>) {
        unsubscribe(subscriber)
    }
}

/**
 * Subscribers have to be aware to unsubscribe() when the scope of the subscriber is destroyed.
 */
inline fun <reified T : EventContent> IEventEmitter.subscribe(noinline subscriber: EventSubscriber<T>) {
    subscribe(T::class, subscriber)
}

inline fun <reified T : EventContent> IEventEmitter.unsubscribe(noinline subscriber: EventSubscriber<T>) {
    unsubscribe(T::class, subscriber)
}