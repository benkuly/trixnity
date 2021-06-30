package net.folivo.trixnity.examples.multiplatform

import com.soywiz.klock.DateTime
import io.ktor.client.*
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.rest.MatrixClient
import net.folivo.trixnity.client.rest.MatrixClientProperties
import net.folivo.trixnity.client.rest.MatrixClientProperties.MatrixHomeServerProperties
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.MessageEventContent.TextMessageEventContent

suspend fun example() = coroutineScope {
    val matrixClient =
        MatrixClient(
            HttpClient(), MatrixClientProperties(
                MatrixHomeServerProperties(
                    "matrix.imbitbu.de"
                ),
                "someToken"
            )
        )

    val textMessageEventFlow = matrixClient.sync.events<TextMessageEventContent>()

    val startTime = DateTime.now()

    val job = launch {
        textMessageEventFlow.collect { event ->
            require(event is Event.RoomEvent)
            if (event.roomId == MatrixId.RoomId("!zqCKMizNPeocxLJFNF:imbitbu.de")) {
                if (DateTime.fromUnix(event.originTimestamp) > startTime) {
                    val body = event.content.body
                }
            }
        }
    }

    matrixClient.sync.start()

    delay(30000)

    matrixClient.sync.stop()
    job.cancelAndJoin()
}