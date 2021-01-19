package net.folivo.trixnity.examples

import net.folivo.trixnity.client.rest.MatrixClient
import net.folivo.trixnity.client.rest.MatrixClientProperties
import net.folivo.trixnity.client.rest.MatrixClientProperties.MatrixHomeServerProperties
import net.folivo.trixnity.client.rest.makeHttpClient
import net.folivo.trixnity.core.model.MatrixId.RoomId
import net.folivo.trixnity.core.model.events.m.room.MessageEvent
import net.folivo.trixnity.core.model.events.m.room.MessageEvent.MessageEventContent
import net.folivo.trixnity.core.model.events.m.room.MessageEvent.MessageEventContent.TextMessageEventContent

class SendMessage {
    private val matrixClient = MatrixClient(
        makeHttpClient(
            MatrixClientProperties(
                MatrixHomeServerProperties("matrix.org"),
                """CHANGEME"""
            )
        )
    )

    suspend fun sendMessage() {
        matrixClient.room.sendRoomEvent<MessageEvent, MessageEventContent>(
            RoomId("CHANGEME", "matrix.org"),
            TextMessageEventContent("hello from platform $Platform")
        )
    }
}