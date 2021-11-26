package net.folivo.trixnity.examples.multiplatform

import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.api.authentication.IdentifierType
import net.folivo.trixnity.client.room.message.text
import net.folivo.trixnity.client.store.SecureStore
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.TimelineEvent.Gap.GapBefore
import net.folivo.trixnity.client.store.sqldelight.SqlDelightStoreFactory
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event.MessageEvent
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.MegolmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import org.kodein.log.Logger
import org.kodein.log.LoggerFactory
import org.kodein.log.filter.entry.minimumLevel
import org.kodein.log.frontend.defaultLogFrontend

suspend fun timelineExample() = coroutineScope {
    val username = "username"
    val password = "password"
    val roomId = RoomId("!room:example.org")
    val baseUrl = Url("https://example.org")
    val storeFactory = SqlDelightStoreFactory(createDriver(), databaseCoroutineContext())
    val secureStore = object : SecureStore {
        override val olmPickleKey = ""
    }
    val loggerFactory = LoggerFactory(
        listOf(defaultLogFrontend),
        listOf(minimumLevel(Logger.Level.INFO)),
    )
    val scope = CoroutineScope(Dispatchers.Default)
    val matrixClient = MatrixClient.fromStore(
        storeFactory = storeFactory,
        secureStore = secureStore,
        scope = scope,
        loggerFactory = loggerFactory
    ) ?: MatrixClient.login(
        baseUrl = baseUrl,
        IdentifierType.User(username),
        password,
        initialDeviceDisplayName = "trixnity-client-${kotlin.random.Random.Default.nextInt()}",
        storeFactory = storeFactory,
        secureStore = secureStore,
        scope = scope,
        loggerFactory = loggerFactory
    )

    val encrytpedEventFlow = matrixClient.api.sync.events<MegolmEncryptedEventContent>()

    val startTime = Clock.System.now()

    val job1 = launch {
        encrytpedEventFlow.collect { event ->
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
    }
    val job2 = launch {
        matrixClient.room.getLastTimelineEvent(roomId, this).filterNotNull().collect { lastEvent ->
            val roomName = matrixClient.room.getById(roomId).value?.name
            println("------------------------- $roomName")
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
            }.filterNotNull().take(10).toList().reversed().forEach { timelineEvent ->
                val event = timelineEvent.value?.event
                val content = event?.content
                val sender = event?.sender?.let { matrixClient.user.getById(it, roomId, this).value?.name }
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
    scope.cancel()

    job2.cancelAndJoin()
    job1.cancelAndJoin()
}