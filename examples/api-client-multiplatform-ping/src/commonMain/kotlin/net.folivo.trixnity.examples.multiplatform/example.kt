package net.folivo.trixnity.examples.multiplatform

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.core.model.MatrixId.RoomId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.MessageEventContent.TextMessageEventContent

suspend fun example() = coroutineScope {
    val matrixRestClient =
        MatrixApiClient(
            hostname = "server",
            accessToken = MutableStateFlow("token")
        )

    val textMessageEventFlow = matrixRestClient.sync.events<TextMessageEventContent>()

    val startTime = Clock.System.now()

    val job = launch {
        textMessageEventFlow.collect { event ->
            require(event is Event.RoomEvent)
            if (event.roomId == RoomId("!someRoom:server")) {
                if (Instant.fromEpochMilliseconds(event.originTimestamp) > startTime) {
                    val body = event.content.body
                }
            }
        }
    }

    matrixRestClient.sync.start()

    delay(30000)

    matrixRestClient.sync.stop()
    job.cancelAndJoin()
}