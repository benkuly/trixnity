package net.folivo.trixnity.examples.client.ping

import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.room.message.text
import net.folivo.trixnity.client.room.toFlowList
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

suspend fun timelineExample() = coroutineScope {
    val scope = CoroutineScope(Dispatchers.Default)

    val username = "username"
    val password = "password"
    val roomId = RoomId("!room:example.org")
    val baseUrl = Url("https://example.org")
    val matrixClient = MatrixClient.fromStore(
        storeFactory = createStoreFactory(scope),
        scope = scope,
    ).getOrThrow() ?: MatrixClient.login(
        baseUrl = baseUrl,
        IdentifierType.User(username),
        password,
        initialDeviceDisplayName = "trixnity-client-${Random.Default.nextInt()}",
        storeFactory = createStoreFactory(scope),
        scope = scope,
    ).getOrThrow()

    val job1 = launch {
        matrixClient.room.getTimelineEventsFromNowOn().collect { timelineEvent ->
            if (timelineEvent.roomId == roomId) {
                try {
                    val content = timelineEvent.content?.getOrNull()
                    if (content is TextMessageEventContent && content.body.startsWith("ping")) {
                        matrixClient.room.sendMessage(roomId) {
                            text("pong to ${content.body}")
                        }
                    }
                } catch (_: Exception) {

                }
            }
        }
    }

    val job2 = launch {
        matrixClient.room.getLastTimelineEvents(roomId)
            .toFlowList(MutableStateFlow(10))
            .debounce(100.milliseconds)
            .collectLatest { timelineEvents ->
                timelineEvents.reversed().forEach { timelineEvent ->
                    val event = timelineEvent.value?.event
                    val content = timelineEvent.value?.content?.getOrNull()
                    val sender = event?.sender?.let { matrixClient.user.getById(it, roomId)?.name }
                    when (content) {
                        is RoomMessageEventContent -> println("${sender}: ${content.body}")
                        null -> println("${sender}: not yet decrypted")
                        else -> {
                        }
                    }
                }
                println("### END OF TIMELINE ###")
            }
    }

    matrixClient.startSync()

    delay(300000)
    scope.cancel()

    job1.cancelAndJoin()
    job2.cancelAndJoin()
}