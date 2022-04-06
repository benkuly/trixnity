package net.folivo.trixnity.examples.multiplatform

import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.room.message.text
import net.folivo.trixnity.client.room.toFlowList
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event.MessageEvent
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.MegolmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.subscribe
import kotlin.random.Random

suspend fun timelineExample() = coroutineScope {
    val scope = CoroutineScope(Dispatchers.Default)

    val username = "username"
    val password = "password"
    val roomId = RoomId("!room:example.org")
    val baseUrl = Url("https://example.org")
    val matrixClient = MatrixClient.fromStore(
        storeFactory = createStoreFactory(),
        scope = scope,
    ).getOrThrow() ?: MatrixClient.login(
        baseUrl = baseUrl,
        IdentifierType.User(username),
        password,
        initialDeviceDisplayName = "trixnity-client-${Random.Default.nextInt()}",
        storeFactory = createStoreFactory(),
        scope = scope,
    ).getOrThrow()

    val startTime = Clock.System.now()

    matrixClient.api.sync.subscribe<MegolmEncryptedEventContent> { event ->
        require(event is MessageEvent<MegolmEncryptedEventContent>)
        if (event.roomId == roomId) {
            if (Instant.fromEpochMilliseconds(event.originTimestamp) > startTime) {
                delay(500)
                try {
                    val decryptedEvent = matrixClient.olm.event.decryptMegolm(event)
                    val content = decryptedEvent.content
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

    val job = launch {
        matrixClient.room.getLastTimelineEvents(roomId, this)
            .toFlowList(MutableStateFlow(10))
            .collectLatest { timelineEvents ->
                timelineEvents.forEach { timelineEvent ->
                    val event = timelineEvent.value?.event
                    val content = timelineEvent.value?.content?.getOrNull()
                    val sender = event?.sender?.let { matrixClient.user.getById(it, roomId, this).value?.name }
                    when {
                        content is RoomMessageEventContent -> println("${sender}: ${content.body}")
                        content == null -> println("${sender}: not yet decrypted")
                        event is MessageEvent -> println("${sender}: $event")
                        event is StateEvent -> println("${sender}: $event")
                        else -> {
                        }
                    }
                }
            }
    }

    matrixClient.startSync()

    delay(300000)
    scope.cancel()

    job.cancelAndJoin()
}