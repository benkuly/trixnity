package net.folivo.trixnity.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.EventContent
import kotlin.reflect.KClass

abstract class EventEmitter { // TODO test it
    private val eventHandler: MutableMap<KClass<out EventContent>, MutableSharedFlow<Event<*>>> =
        mutableMapOf()

    protected suspend fun emitEvent(event: Event<*>) {
        eventHandler
            .filterKeys {
                it.isInstance(event.content)
            }
            .forEach {
                it.value.emit(event)
            }
    }

    fun <T : EventContent> events(clazz: KClass<T>): SharedFlow<Event<T>> {
        @Suppress("UNCHECKED_CAST")
        return (eventHandler.getOrPut(clazz) { MutableSharedFlow() } as MutableSharedFlow<Event<T>>)
            .asSharedFlow()
    }

    inline fun <reified T : EventContent> events(): SharedFlow<Event<T>> {
        return events(T::class)
    }

    fun allEvents(): SharedFlow<Event<*>> {
        return events<EventContent>()
    }
}