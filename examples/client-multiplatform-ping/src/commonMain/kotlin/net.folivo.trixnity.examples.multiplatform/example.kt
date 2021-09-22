package net.folivo.trixnity.examples.multiplatform

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.api.authentication.IdentifierType
import net.folivo.trixnity.client.store.InMemoryStore
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.TimelineEvent.Gap.GapBefore
import net.folivo.trixnity.core.model.MatrixId.RoomId
import net.folivo.trixnity.core.model.events.Event.MessageEvent
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.MegolmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import org.kodein.log.Logger
import org.kodein.log.LoggerFactory
import org.kodein.log.filter.entry.minimumLevel
import org.kodein.log.frontend.defaultLogFrontend

suspend fun example() = coroutineScope {
    val store = InMemoryStore("your-server")
    val matrixClient = MatrixClient.login(
        IdentifierType.User("user"),
        "password",
        initialDeviceDisplayName = "trixnity-client-${kotlin.random.Random.Default.nextInt()}",
        store,
        LoggerFactory(
            listOf(defaultLogFrontend),
            listOf(minimumLevel(Logger.Level.WARNING)),
        )
    )


    val encrytpedEventFlow = matrixClient.api.sync.events<MegolmEncryptedEventContent>()

    val startTime = Clock.System.now()

    val roomId = RoomId("!room:server")

    val answers = MutableSharedFlow<String>(extraBufferCapacity = 5)

    val job1 = launch {
        encrytpedEventFlow.collect { event ->
            require(event is MessageEvent<MegolmEncryptedEventContent>)
            if (event.roomId == roomId) {
                if (Instant.fromEpochMilliseconds(event.originTimestamp) > startTime) {
                    delay(500)
                    try {
                        val decryptedEvent = matrixClient.olm.events.decryptMegolm(event)
                        val content = decryptedEvent.content
                        if (content is RoomMessageEventContent.TextMessageEventContent && content.body.startsWith("ping")) {
                            answers.emit("pong to ${content.body}")
                        }
                    } catch (_: Exception) {

                    }
                }
            }
        }
    }
    val job2 = launch {
        answers.collect {
            delay(500)
            val pongEvent = matrixClient.olm.events.encryptMegolm(
                RoomMessageEventContent.TextMessageEventContent(it),
                roomId,
                matrixClient.api.rooms.getStateEvent(roomId)
            )
            matrixClient.api.rooms.sendRoomEvent(roomId, pongEvent)
        }
    }
    val job3 = launch {
        matrixClient.rooms.getLastTimelineEvent(roomId).filterNotNull().collect { lastEvent ->
            matrixClient.rooms.loadMembers(roomId)
            val roomName = store.rooms.byId(roomId).value?.name
            println("------------------------- $roomName")
            flow {
                var currentTimelineEvent: StateFlow<TimelineEvent?>? = lastEvent
                emit(lastEvent)
                while (currentTimelineEvent?.value != null) {
                    val currentTimelineEventValue = currentTimelineEvent.value
                    if (currentTimelineEventValue?.gap is GapBefore) {
                        matrixClient.rooms.fetchMissingEvents(currentTimelineEventValue)
                    }
                    currentTimelineEvent =
                        currentTimelineEvent.value?.let { matrixClient.rooms.getPreviousTimelineEvent(it) }
                    emit(currentTimelineEvent)
                }
            }.filterNotNull().take(10).toList().reversed().forEach { timelineEvent ->
                val event = timelineEvent.value?.event
                val content = event?.content
                val sender = event?.sender?.let { matrixClient.rooms.getUserDisplayName(roomId, it) }
                when {
                    event is MessageEvent && content is RoomMessageEventContent ->
                        println("${sender}: ${content.body}")
                    event is MessageEvent && content is MegolmEncryptedEventContent -> {
                        val decryptedEvent = timelineEvent.value?.decryptedEvent
                        val decryptedEventContent = decryptedEvent?.getOrNull()?.content
                        val decryptionException = timelineEvent.value?.decryptedEvent?.exceptionOrNull()
                        when {
                            decryptionException != null -> println("${sender}: cannot decrypt (${decryptionException.message})")
                            decryptedEvent == null -> println("${sender}: not yet decrypted")
                            decryptedEventContent is RoomMessageEventContent -> println("${sender}: ${decryptedEventContent.body}")
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

    job3.cancelAndJoin()
    job2.cancelAndJoin()
    job1.cancelAndJoin()
}