package net.folivo.trixnity.client.store

import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.RelatesTo
import net.folivo.trixnity.core.model.events.replace

val TimelineEvent.isEncrypted: Boolean
    get() = event.isEncrypted

val TimelineEvent.isFirst: Boolean
    get() = previousEventId == null && gap == null

val TimelineEvent.isLast: Boolean
    get() = nextEventId == null

val TimelineEvent.isReplaced: Boolean
    get() =
        if (event is Event.MessageEvent) event.unsigned?.aggregations?.replace != null
        else false

val TimelineEvent.isReplacing: Boolean
    get() = relatesTo is RelatesTo.Replace

val TimelineEvent.relatesTo: RelatesTo?
    get() =
        if (event is Event.MessageEvent) {
            val content = event.content
            if (content is MessageEventContent) {
                content.relatesTo
            } else null
        } else null