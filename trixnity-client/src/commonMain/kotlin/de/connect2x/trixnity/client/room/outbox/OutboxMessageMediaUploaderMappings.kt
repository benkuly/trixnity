package de.connect2x.trixnity.client.room.outbox

import io.ktor.util.reflect.*
import de.connect2x.trixnity.core.model.events.EventContent

data class OutboxMessageMediaUploaderMappings(val mappings: List<OutboxMessageMediaUploaderMapping<*>>) {
    companion object
}

fun OutboxMessageMediaUploaderMappings.findUploaderOrFallback(content: EventContent): MessageEventContentMediaUploader =
    mappings.find { content.instanceOf(it.kClass) }?.uploader ?: FallbackOutboxMessageMediaUploaderMapping.uploader