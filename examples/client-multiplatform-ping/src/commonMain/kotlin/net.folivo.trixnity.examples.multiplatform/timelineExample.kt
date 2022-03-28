package net.folivo.trixnity.examples.multiplatform

import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.room.message.text
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event.MessageEvent
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.MegolmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
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
                    val decryptedEvent = matrixClient.olm.events.decryptMegolm(event)
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
        matrixClient.room.getLastTimelineEvent(roomId, this).filterNotNull().collect { lastEvent ->
            val roomName = matrixClient.room.getById(roomId).value?.name
            println("------------------------- $roomName")
            flow {
                var currentTimelineEvent: StateFlow<TimelineEvent?>? = lastEvent
                emit(lastEvent)
                while (currentTimelineEvent?.value != null) {
                    val currentTimelineEventValue = currentTimelineEvent.value
                    if (currentTimelineEventValue?.gap is TimelineEvent.Gap.GapBefore) {
                        matrixClient.room.fetchMissingEvents(currentTimelineEventValue)
                    }
                    currentTimelineEvent =
                        currentTimelineEvent.value?.let {
                            matrixClient.room.getPreviousTimelineEvent(it, this@coroutineScope)
                        }
                    emit(currentTimelineEvent)
                }
            }.filterNotNull().take(10).toList().reversed().forEach { timelineEvent ->
                val event = timelineEvent.value?.event
                val content = event?.content
                val sender = event?.sender?.let { matrixClient.user.getById(it, roomId, this).value?.name }
                when {
                    content is RoomMessageEventContent ->
                        println("${sender}: ${content.body}")
                    content is MegolmEncryptedEventContent -> {
                        val decryptedEvent = timelineEvent.value?.content
                        val decryptedEventContent = decryptedEvent?.getOrNull()?.content
                        val decryptionException = decryptedEvent?.exceptionOrNull()
                        when {
                            decryptedEventContent is RoomMessageEventContent -> println("${sender}: ${decryptedEventContent.body}")
                            decryptedEvent == null -> println("${sender}: not yet decrypted")
                            decryptionException != null -> println("${sender}: cannot decrypt (${decryptionException.message})")
                        }
                    }
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