package net.folivo.trixnity.client.room

import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.core.model.events.RelationType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.INFINITE
import kotlin.time.Duration.Companion.minutes


data class GetTimelineEventConfig(
    /**
     * Timeout for decrypting events.
     */
    var decryptionTimeout: Duration = INFINITE,
    /**
     * When the event does not exist locally, it is searched by fetching [TimelineEvent]s:
     * Timeout how long the [TimelineEvent] is searched.
     */
    var fetchTimeout: Duration = 1.minutes,
    /**
     * When the event does not exist locally, it is searched by fetching [TimelineEvent]s:
     * Maximum number of events fetched from the server at once.
     */
    var fetchSize: Long = 20,
    /**
     * Define, if the content of a [TimelineEvent] will be replaced on a [RelationType.Replace].
     */
    var allowReplaceContent: Boolean = true
)

fun GetTimelineEventConfig.apply(    config:GetTimelineEventsConfig) =apply {
    decryptionTimeout = config.decryptionTimeout
    fetchTimeout = config.fetchTimeout
    fetchSize = config.fetchSize
    allowReplaceContent = config.allowReplaceContent
}

data class GetTimelineEventsConfig(
    /**
     * Timeout for decrypting events.
     */
    var decryptionTimeout: Duration = INFINITE,
    /**
     * When the next event does not exist locally, it is fetched:
     * Timeout for this fetch.
     */
    var fetchTimeout: Duration = 1.minutes,
    /**
     * When the next event does not exist locally, it is fetched:
     * Maximum number of events fetched from the server at once.
     */
    var fetchSize: Long = 20,
    /**
     * Define, if the content of a [TimelineEvent] will be replaced on a [RelationType.Replace].
     */
    var allowReplaceContent: Boolean = true,
    /**
     * When set, the current [TimelineEvent] retrieving stops, when a gap is found and this size is reached (including the start event).
     */
    var minSize: Long? = null,
    /**
     * When set, the current [TimelineEvent] retrieving stops, when this varue is reached (including the start event).
     */
    var maxSize: Long? = null
)

fun GetTimelineEventsConfig.apply(config:GetTimelineEventConfig) =apply {
    decryptionTimeout = config.decryptionTimeout
    fetchTimeout = config.fetchTimeout
    fetchSize = config.fetchSize
    allowReplaceContent =config. allowReplaceContent
}