package net.folivo.trixnity.examples.multiplatform

import com.soywiz.klock.DateTime
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.rest.MatrixRestClient
import net.folivo.trixnity.client.rest.MatrixRestClientProperties
import net.folivo.trixnity.client.rest.MatrixRestClientProperties.MatrixHomeServerProperties
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.MessageEventContent.TextMessageEventContent

suspend fun example() = coroutineScope {
    val matrixRestClient =
        MatrixRestClient(
            MatrixRestClientProperties(
                MatrixHomeServerProperties(
                    "matrix.imbitbu.de"
                ),
                "someToken"
            )
        )

    val textMessageEventFlow = matrixRestClient.sync.events<TextMessageEventContent>()

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

    matrixRestClient.sync.start()

    delay(30000)

    matrixRestClient.sync.stop()
    job.cancelAndJoin()
}