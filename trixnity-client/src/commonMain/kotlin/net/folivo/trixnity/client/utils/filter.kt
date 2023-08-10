package net.folivo.trixnity.client.utils

import io.ktor.util.reflect.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.EventContent

internal inline fun <reified C : EventContent> Iterable<Event<*>>.filterContent(): Flow<Event<C>> =
    asFlow()
        .filter { it.content.instanceOf(C::class) }
        .filterIsInstance<Event<C>>()

internal inline fun <reified C : EventContent, reified E : Event<C>> Iterable<Event<*>>.filter(): Flow<E> =
    asFlow()
        .filter { it.content.instanceOf(C::class) }
        .filterIsInstance<E>()