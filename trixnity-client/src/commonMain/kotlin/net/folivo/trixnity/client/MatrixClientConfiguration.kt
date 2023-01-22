package net.folivo.trixnity.client

import io.ktor.client.*
import net.folivo.trixnity.core.model.events.Event
import org.koin.core.module.Module
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class MatrixClientConfiguration {
    var setOwnMessagesAsFullyRead: Boolean = false
    var enableAsyncTransactions: Boolean = true
    var cacheExpireDurations: CacheExpireDurations = CacheExpireDurations.default(1.minutes)
    var lastRelevantEventFilter: (Event.RoomEvent<*>) -> Boolean = { it is Event.MessageEvent<*> }
    var httpClientFactory: (HttpClientConfig<*>.() -> Unit) -> HttpClient = { HttpClient(it) }
    var modules: List<Module> = createDefaultModules()

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
                )
        }
    }
}