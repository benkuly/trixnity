package net.folivo.trixnity.client.room.outbox

import io.github.oshai.kotlinlogging.KotlinLogging
import net.folivo.trixnity.core.model.events.MessageEventContent

private val log = KotlinLogging.logger { }

val FallbackOutboxMessageMediaUploaderMapping =
    OutboxMessageMediaUploaderMapping(MessageEventContent::class) { content, _ ->
        log.debug { "EventContent class ${content::class.simpleName} is not supported by any other media uploader." }
        content
    }