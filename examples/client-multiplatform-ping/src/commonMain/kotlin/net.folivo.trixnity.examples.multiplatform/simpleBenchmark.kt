package net.folivo.trixnity.examples.multiplatform

import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.api.SyncApiClient
import net.folivo.trixnity.client.api.model.authentication.IdentifierType.User
import net.folivo.trixnity.client.room.message.text
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.TimelineEvent.Gap.GapBefore
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event.MessageEvent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.MegolmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import kotlin.random.Random

suspend fun simpleBenchmark() = coroutineScope {
    val scope = CoroutineScope(Dispatchers.Default)

    val username = "trixnity"
    val password = "Tr1xn1ty!"
    val roomId = RoomId("!mZtvIlkjtKWuxTEmmw:localhost:8008")
    val baseUrl = Url("http://localhost:8008")
    val matrixClient = MatrixClient.fromStore(
        storeFactory = createStoreFactory(),
        scope = scope,
    ).getOrThrow() ?: MatrixClient.login(
        baseUrl = baseUrl,
        User(username),
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
        matrixClient.syncState.first { it == SyncApiClient.SyncState.RUNNING }
        delay(300)
        println("${Clock.System.now()} start")
        matrixClient.room.getLastTimelineEvent(roomId, this).filterNotNull().collect { lastEvent ->
            flow {
                var currentTimelineEvent: StateFlow<TimelineEvent?>? = lastEvent
                emit(lastEvent)
                while (currentTimelineEvent?.value != null) {
                    val currentTimelineEventValue = currentTimelineEvent.value
                    if (currentTimelineEventValue?.gap is GapBefore) {
                        matrixClient.room.fetchMissingEvents(currentTimelineEventValue)
                    }
                    currentTimelineEvent =
                        currentTimelineEvent.value?.let {
                            matrixClient.room.getPreviousTimelineEvent(it, this@coroutineScope)
                        }
                    emit(currentTimelineEvent)
                }
            }.filterNotNull().take(20).toList()
                .also { println("${Clock.System.now()} collectedEventsCount=${it.count()}") }
                .forEach { it.first { it?.decryptedEvent?.isSuccess == true } }
            println("${Clock.System.now()} decrypted all events")
        }
    }

    matrixClient.startSync()

    delay(300000)
    scope.cancel()

    job.cancelAndJoin()
}