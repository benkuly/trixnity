package net.folivo.trixnity.core

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.EventContent
import kotlin.reflect.KClass

typealias EventSubscriber<T> = suspend (Event<T>) -> Unit

abstract class EventEmitter {
    private val eventSubscribers =
        MutableStateFlow<Map<KClass<out EventContent>, Set<EventSubscriber<out EventContent>>>>(mapOf())

    protected suspend fun emitEvent(event: Event<*>) = coroutineScope {
        eventSubscribers.value
            .filterKeys {
                it.isInstance(event.content)
            }
            .forEach { (_, subscribers) ->
                subscribers.forEach { launch { it.invoke(event) } }
            }
    }

    fun <T : EventContent> subscribe(clazz: KClass<T>, subscriber: EventSubscriber<T>) {
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

    inline fun <reified T : EventContent> subscribe(noinline subscriber: EventSubscriber<T>) {
        subscribe(T::class, subscriber)
    }

    fun <T : EventContent> unsubscribe(clazz: KClass<T>, subscriber: EventSubscriber<T>) {
        @Suppress("UNCHECKED_CAST")
        subscriber as EventSubscriber<out EventContent>
        eventSubscribers.update {
            val existingSubscribers = it[clazz]
            if (existingSubscribers == null) it
            else it + (clazz to (existingSubscribers - subscriber))
        }
    }

    inline fun <reified T : EventContent> unsubscribe(noinline subscriber: EventSubscriber<T>) {
        unsubscribe(T::class, subscriber)
    }


    fun subscribeAllEvents(subscriber: EventSubscriber<EventContent>) {
        subscribe(subscriber)
    }

    fun unsubscribeAllEvents(subscriber: EventSubscriber<EventContent>) {
        unsubscribe(subscriber)
    }
}