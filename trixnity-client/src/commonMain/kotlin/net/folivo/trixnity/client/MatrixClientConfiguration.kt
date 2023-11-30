package net.folivo.trixnity.client

import io.ktor.client.*
import kotlinx.coroutines.CoroutineName
import net.folivo.trixnity.api.client.defaultTrixnityHttpClientFactory
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent
import net.folivo.trixnity.core.model.events.m.room.Membership
import org.koin.core.module.Module
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class MatrixClientConfiguration {
    /**
     * Set a name for this instance. This is used to set a [CoroutineName] to the [CoroutineContext].
     */
    var name: String? = null

    /**
     * Sets the own bookmark to the latest messages sent by this client.
     */
    var setOwnMessagesAsFullyRead: Boolean = false

    /**
     * Automatically join upgraded rooms.
     */
    var autoJoinUpgradedRooms: Boolean = true

    /**
     * Delete a room, when it's membership is [Membership.LEAVE].
     */
    var deleteRoomsOnLeave: Boolean = true

    /**
     * Specifies how long values are kept in the cache when not used by anyone.
     */
    var cacheExpireDurations: CacheExpireDurations = CacheExpireDurations.default(1.minutes)

    /**
     * The timeout for the normal sync loop.
     */
    var syncLoopTimeout: Duration = 30.seconds

    /**
     * Set custom delays for the sync loop.
     */
    var syncLoopDelays: SyncLoopDelays = SyncLoopDelays.default()

    /**
     * Allows you to customize, which [Room.lastRelevantEventId] is set.
     */
    var lastRelevantEventFilter: (RoomEvent<*>) -> Boolean = { it is RoomEvent.MessageEvent<*> }

    /**
     * Set custom [HttpClient].
     */
    var httpClientFactory: (config: HttpClientConfig<*>.() -> Unit) -> HttpClient = defaultTrixnityHttpClientFactory()

    /**
     * Inject and override modules into Trixnity.
     */
    var modules: List<Module> = createDefaultModules()


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