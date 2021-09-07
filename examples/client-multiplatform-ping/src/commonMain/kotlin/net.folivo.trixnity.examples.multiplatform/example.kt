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
import net.folivo.trixnity.client.store.getPrevious
import net.folivo.trixnity.core.model.MatrixId.RoomId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.MegolmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.MessageEventContent
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
            require(event is Event.RoomEvent<MegolmEncryptedEventContent>)
            if (event.roomId == roomId) {
                if (Instant.fromEpochMilliseconds(event.originTimestamp) > startTime) {
                    delay(500)
                    try {
                        val decryptedEvent = matrixClient.olm.events.decryptMegolm(event)
                        val content = decryptedEvent.content
                        if (content is MessageEventContent.TextMessageEventContent && content.body.startsWith("ping")) {
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
                MessageEventContent.TextMessageEventContent(it),
                roomId,
                matrixClient.api.rooms.getStateEvent(roomId)
            )
            matrixClient.api.rooms.sendRoomEvent(roomId, pongEvent)
        }
    }
    val job3 = launch {
        store.rooms.all()
            .mapNotNull { rooms -> rooms.find { it.roomId == roomId } }
            .stateIn(this)
            .collect { room ->
                println("-----------------------------------")
                println(room)
                val lastEventId = room.lastEventId
                if (lastEventId != null) {
                    var currentTimelineEvent = store.rooms.timeline.byId(lastEventId, room.roomId).value
                    flow {
                        emit(currentTimelineEvent)
                        while (currentTimelineEvent != null) {
                            currentTimelineEvent =
                                currentTimelineEvent?.let { store.rooms.timeline.getPrevious(it)?.value }
                            emit(currentTimelineEvent)
                        }
                    }.filterNotNull().toList().reversed().forEach {
                        val event = it.event
                        val content = event.content
                        if (event is Event.RoomEvent && content is MegolmEncryptedEventContent) {
                            try {
                                val decryptedEvent =
                                    matrixClient.olm.events.decryptMegolm(event as Event.RoomEvent<MegolmEncryptedEventContent>)
                                val content = decryptedEvent.content
                                if (content is MessageEventContent.TextMessageEventContent) {
                                    println("${event.sender}: ${content.body}")
                                }
                            } catch (e: Exception) {
                                println("${event.sender}: cannot be decrypted (${e.message})")
                            }
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