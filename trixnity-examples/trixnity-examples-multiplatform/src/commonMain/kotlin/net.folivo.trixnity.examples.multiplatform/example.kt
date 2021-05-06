package net.folivo.trixnity.examples.multiplatform

import com.soywiz.klock.DateTime
import com.soywiz.klogger.Logger
import io.ktor.client.*
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.rest.MatrixClient
import net.folivo.trixnity.client.rest.MatrixClientProperties
import net.folivo.trixnity.client.rest.MatrixClientProperties.MatrixHomeServerProperties
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.MessageEventContent.NoticeMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.MessageEventContent.TextMessageEventContent

suspend fun example() = coroutineScope {
    Logger.defaultLevel = Logger.Level.INFO
    val log = Logger("example")
    val matrixClient =
        MatrixClient(
            HttpClient(), MatrixClientProperties(
                MatrixHomeServerProperties(
                    "matrix.imbitbu.de"
                ),
                "MDAxOGxvY2F0aW9uIGltYml0YnUuZGUKMDAxM2lkZW50aWZpZXIga2V5CjAwMTBjaWQgZ2VuID0gMQowMDIyY2lkIHVzZXJfaWQgPSBAdnZvOmltYml0YnUuZGUKMDAxNmNpZCB0eXBlID0gYWNjZXNzCjAwMjFjaWQgbm9uY2UgPSAySDU4flp2Xzp-bjtTKjhECjAwMmZzaWduYXR1cmUgl12WqHwkXlZDeWJTjloCfHF_yny7th2NpOVQGuISX4kK"
            )
        )

    // first register your event handlers
    val textMessageEventFlow = matrixClient.sync.events<TextMessageEventContent>()

    val startTime = DateTime.now()

    // you need to start the sync to receive messages
    matrixClient.sync.start()

    // wait for events in separate coroutines and print to console
    val job = launch {
        textMessageEventFlow.collect { event ->
            require(event is Event.RoomEvent)
            // we need to check, if events are not older then start time, because we use InMemorySyncBatchTokenService
            if (DateTime.fromUnix(event.originTimestamp) > startTime) {
                val body = event.content.body
                // just answer with "pong", when the received message contains "ping"
                if (body.contains("ping")) {
                    log.info { "received message containing ping" }
                    matrixClient.room.sendRoomEvent(event.roomId, NoticeMessageEventContent("pong"))
                }
            }
        }
    }

    delay(30000) // wait a minute

    // stop the client
    matrixClient.sync.stop()
    job.cancelAndJoin()
}