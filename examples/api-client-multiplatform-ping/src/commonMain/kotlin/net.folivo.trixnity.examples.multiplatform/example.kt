package net.folivo.trixnity.examples.multiplatform

import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.media.ThumbnailResizingMethod
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.ImageInfo
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.ImageMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent

suspend fun example() = coroutineScope {
    val matrixRestClient =
        MatrixClientServerApiClient(
            baseUrl = Url("http://host"),
        ).apply { accessToken.value = "token" }
    val roomId = RoomId("!room:server")

    val startTime = Clock.System.now()

    matrixRestClient.sync.subscribe<TextMessageEventContent> { event ->
        require(event is Event.MessageEvent)
        if (event.roomId == roomId) {
            if (Instant.fromEpochMilliseconds(event.originTimestamp) > startTime) {
                val body = event.content.body
                when {
                    body.startsWith("ping") -> {
                        matrixRestClient.rooms.sendMessageEvent(
                            roomId, TextMessageEventContent(body = "pong")
                        )
                    }
                    body.startsWith("me") -> {
                        val senderAvatar =
                            matrixRestClient.rooms.getStateEvent<MemberEventContent>(
                                roomId,
                                event.sender.full
                            ).getOrThrow().avatarUrl
                        if (senderAvatar != null) {
                            val senderAvatarDownload = matrixRestClient.media.downloadThumbnail(
                                senderAvatar,
                                64u,
                                64u,
                                ThumbnailResizingMethod.CROP
                            ).getOrThrow()
                            val contentLength = senderAvatarDownload.contentLength
                            requireNotNull(contentLength)
                            val uploadedUrl = matrixRestClient.media.upload(
                                senderAvatarDownload.content,
                                contentLength,
                                senderAvatarDownload.contentType ?: ContentType.Application.OctetStream
                            ).getOrThrow().contentUri
                            matrixRestClient.rooms.sendMessageEvent(
                                roomId, ImageMessageEventContent(
                                    body = "avatar image of ${event.sender}",
                                    info = ImageInfo(),
                                    url = uploadedUrl
                                )
                            )
                        }

                    }
                }
            }
        }
    }

    val scope = CoroutineScope(Dispatchers.Default)
    matrixRestClient.sync.start(scope = scope)

    delay(30000)

    matrixRestClient.sync.stop()
    scope.cancel()
}