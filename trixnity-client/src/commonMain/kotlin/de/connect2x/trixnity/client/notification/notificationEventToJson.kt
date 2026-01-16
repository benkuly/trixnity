package de.connect2x.trixnity.client.notification

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.StrippedStateEvent

private val log = KotlinLogging.logger("de.connect2x.trixnity.client.notification.notificationEventToJson")

@OptIn(ExperimentalSerializationApi::class)
internal fun notificationEventToJson(event: ClientEvent<*>, json: Json) =
    try {
        when (event) {
            is RoomEvent -> json.serializersModule.getContextual(RoomEvent::class)?.let {
                json.encodeToJsonElement(it, event)
            }?.jsonObject

            is StrippedStateEvent -> json.serializersModule.getContextual(StrippedStateEvent::class)?.let {
                json.encodeToJsonElement(it, event)
            }?.jsonObject

            else -> throw IllegalStateException("event did have unexpected type ${event::class.simpleName}")
        }
    } catch (exception: Exception) {
        log.warn(exception) { "could not serialize event" }
        null
    }