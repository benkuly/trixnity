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
import net.folivo.trixnity.client.store.SecureStore
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.TimelineEvent.Gap.GapBefore
import net.folivo.trixnity.client.store.sqldelight.SqlDelightStoreFactory
import net.folivo.trixnity.core.model.MatrixId.RoomId
import net.folivo.trixnity.core.model.events.Event.MessageEvent
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.MegolmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import org.kodein.log.Logger
import org.kodein.log.LoggerFactory
import org.kodein.log.filter.entry.minimumLevel
import org.kodein.log.frontend.defaultLogFrontend

suspend fun example() = coroutineScope {
    val username = "username"
    val password = "password"
    val roomId = RoomId("!room:example.org")
    val hostname = "example.org"
    val port = 443
    val secure = true
    val storeFactory = SqlDelightStoreFactory(createDriver(), databaseCoroutineContext())
    val secureStore = object : SecureStore {
        override val olmPickleKey = ""
    }
    val loggerFactory = LoggerFactory(
        listOf(defaultLogFrontend),
        listOf(minimumLevel(Logger.Level.INFO)),
    )
    val matrixClient = MatrixClient.fromStore(
        hostname = hostname,
        port = port,
        secure = secure,
        storeFactory = storeFactory,
        secureStore = secureStore,
        loggerFactory = loggerFactory
    ) ?: MatrixClient.login(
        hostname = hostname,
        port = port,
        secure = secure,
        IdentifierType.User(username),
        password,
        initialDeviceDisplayName = "trixnity-client-${kotlin.random.Random.Default.nextInt()}",
        storeFactory = storeFactory,
        secureStore = secureStore,
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
                            matrixClient.room.sendMessage(TextMessageEventContent("pong to ${content.body}"), roomId)
                        }
                    } catch (_: Exception) {

                    }
                }
            }
        }
    }
    val job2 = launch {
        matrixClient.room.getLastTimelineEvent(roomId, this).filterNotNull().collect { lastEvent ->
            matrixClient.room.loadMembers(roomId)
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

    job2.cancelAndJoin()
    job1.cancelAndJoin()
}