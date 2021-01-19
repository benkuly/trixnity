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
                MatrixHomeServerProperties("matrix.imbitbu.de"),
                """MDAxOGxvY2F0aW9uIGltYml0YnUuZGUKMDAxM2lkZW50aWZpZXIga2V5CjAwMTBjaWQgZ2VuID0gMQowMDIyY2lkIHVzZXJfaWQgPSBAdnZvOmltYml0YnUuZGUKMDAxNmNpZCB0eXBlID0gYWNjZXNzCjAwMjFjaWQgbm9uY2UgPSA9eGlUSFRWU28qbn5ILX5HCjAwMmZzaWduYXR1cmUgQxkQoQm2x98tuTURH4bEhxtueITicRVGugZgVbJq2E0K"""
            )
        )
    )

    suspend fun sendMessage() {
        matrixClient.room.sendRoomEvent<MessageEvent, MessageEventContent>(
            RoomId("zqCKMizNPeocxLJFNF", "imbitbu.de"),
            TextMessageEventContent("hello from platform $Platform")
        )
    }
}