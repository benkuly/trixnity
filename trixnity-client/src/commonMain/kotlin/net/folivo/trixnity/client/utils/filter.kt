package net.folivo.trixnity.client.utils

import io.ktor.util.reflect.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.EventContent

internal inline fun <reified C : EventContent> Iterable<ClientEvent<*>>.filterContent(): Flow<ClientEvent<C>> =
    asFlow()
        .filter { it.content.instanceOf(C::class) }
        .filterIsInstance<ClientEvent<C>>()

internal inline fun <reified C : EventContent, reified E : ClientEvent<C>> Iterable<ClientEvent<*>>.filter(): Flow<E> =
    asFlow()
        .filter { it.content.instanceOf(C::class) }
        .filterIsInstance<E>()