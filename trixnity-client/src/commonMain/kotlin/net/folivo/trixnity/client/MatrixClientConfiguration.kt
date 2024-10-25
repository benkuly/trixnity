package net.folivo.trixnity.client

import io.ktor.client.*
import kotlinx.coroutines.CoroutineName
import net.folivo.trixnity.api.client.defaultTrixnityHttpClientFactory
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.clientserverapi.model.users.Filters
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent
import net.folivo.trixnity.core.model.events.m.room.Membership
import org.koin.core.module.Module
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

data class MatrixClientConfiguration(
    /**
     * Set a name for this instance. This is used to set a [CoroutineName] to the [CoroutineContext].
     */
    var name: String? = null,

    /**
     * Allow to save [TimelineEvent]s unencrypted.
     */
    var storeTimelineEventContentUnencrypted: Boolean = true,

    /**
     * Sets the own bookmark to the latest messages sent by this client.
     */
    var setOwnMessagesAsFullyRead: Boolean = false,

    /**
     * Automatically join upgraded rooms.
     */
    var autoJoinUpgradedRooms: Boolean = true,

    /**
     * Delete a room, when it's membership is [Membership.LEAVE].
     */
    var deleteRoomsOnLeave: Boolean = true,

    /**
     * Set the delay, after which a sent outbox message is deleted. The delay is checked each time a sync is received.
     */
    var deleteSentOutboxMessageDelay: Duration = 10.seconds,

    /**
     * Specifies how long values are kept in the cache when not used by anyone.
     */
    var cacheExpireDurations: CacheExpireDurations = CacheExpireDurations.default(1.minutes),

    /**
     * The timeout for the normal sync loop.
     */
    var syncLoopTimeout: Duration = 30.seconds,

    /**
     * Set custom delays for the sync loop.
     */
    var syncLoopDelays: SyncLoopDelays = SyncLoopDelays.default(),

    /**
     * Allows you to customize, which [Room.lastRelevantEventId] is set.
     */
    var lastRelevantEventFilter: (RoomEvent<*>) -> Boolean = { it is RoomEvent.MessageEvent<*> },

    /**
     * Set filter for the normal sync.
     */
    var syncFilter: Filters = Filters(),

    /**
     * Set filter for the single sync (background sync).
     */
    var syncOnceFilter: Filters = Filters(presence = Filters.EventFilter(limit = 0)),

    /**
     * Set custom [HttpClient].
     */
    var httpClientFactory: (config: HttpClientConfig<*>.() -> Unit) -> HttpClient = defaultTrixnityHttpClientFactory(),

    /**
     * Inject and override modules into Trixnity. You should always apply [createDefaultTrixnityModules] first.
     *
     * For example:
     * ```kotlin
     * modules = createDefaultTrixnityModules() + createCustomModule()
     * ```
     */
    @Deprecated("replace with modulesFactory")
    var modules: List<Module>? = null,

    /**
     * Inject and override modules into Trixnity. You should always apply [createDefaultTrixnityModules] first.
     *
     * Be aware to always create new modules because a module saves your class instances and therefore is reused, which we don't want!
     *
     * For example:
     * ```kotlin
     * modulesFactory = { createDefaultTrixnityModules() + createCustomModule() }
     * ```
     */
    var modulesFactory: () -> List<Module> = { createDefaultTrixnityModules() },
) {
    data class SyncLoopDelays(
        val syncLoopDelay: Duration,
        val syncLoopErrorDelay: Duration
    ) {
        companion object {
            fun default() = SyncLoopDelays(
                syncLoopDelay = 2.seconds,
                syncLoopErrorDelay = 5.seconds
            )
        }
    }

    data class CacheExpireDurations(
        val globalAccountDate: Duration,
        val deviceKeys: Duration,
        val crossSigningKeys: Duration,
        val keyVerificationState: Duration,
        val mediaCacheMapping: Duration,
        val olmSession: Duration,
        val inboundMegolmSession: Duration,
        val inboundMegolmMessageIndex: Duration,
        val outboundMegolmSession: Duration,
        val roomAccountData: Duration,
        val roomState: Duration,
        val timelineEvent: Duration,
        val timelineEventRelation: Duration,
        val roomUser: Duration,
        val roomUserReceipts: Duration,
        val secretKeyRequest: Duration,
        val roomKeyRequest: Duration,
        val roomOutboxMessage: Duration,
        val room: Duration,
    ) {
        companion object {
            fun default(duration: Duration) =
                CacheExpireDurations(
                    globalAccountDate = duration,
                    deviceKeys = duration,
                    crossSigningKeys = duration,
                    keyVerificationState = duration,
                    mediaCacheMapping = duration,
                    olmSession = duration,
                    inboundMegolmSession = duration,
                    inboundMegolmMessageIndex = duration,
                    outboundMegolmSession = duration,
                    roomAccountData = duration,
                    roomState = duration,
                    timelineEvent = duration,
                    timelineEventRelation = duration,
                    roomUser = duration,
                    roomUserReceipts = duration,
                    secretKeyRequest = duration,
                    roomKeyRequest = duration,
                    roomOutboxMessage = duration / 2,
                    room = duration * 10,
                )
        }
    }
}