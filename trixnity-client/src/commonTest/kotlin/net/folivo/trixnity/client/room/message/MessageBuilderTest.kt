package net.folivo.trixnity.client.room.message

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.ContentType.Audio.OGG
import io.ktor.http.ContentType.Image.PNG
import io.ktor.http.ContentType.Video.MP4
import io.ktor.utils.io.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import net.folivo.trixnity.client.mocks.MediaServiceMock
import net.folivo.trixnity.client.mocks.RoomServiceMock
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.RelatesTo
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.*
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import net.folivo.trixnity.core.toByteFlow

class MessageBuilderTest : ShouldSpec({
    timeout = 60_000
    val encryptedRoom = RoomId("encryptedRoom", "server")
    val unencryptedRoom = RoomId("unencryptedRoom", "server")
    val roomService = RoomServiceMock().apply {
        rooms.value = mapOf(
            encryptedRoom to MutableStateFlow(Room(encryptedRoom, encryptionAlgorithm = EncryptionAlgorithm.Megolm)),
            unencryptedRoom to MutableStateFlow(Room(unencryptedRoom)),
        )
        returnGetTimelineEvent = flowOf(
            TimelineEvent(
                event = Event.MessageEvent(
                    TextMessageEventContent("dino\nunicorn"),
                    EventId("dino"),
                    UserId("sender", "server"),
                    RoomId("room", "server"),
                    1234
                ),
                gap = null,
                nextEventId = null,
                previousEventId = null,
            )
        )
    }
    val mediaService = MediaServiceMock()


    fun timelineEvent(eventId: EventId, relatesTo: RelatesTo? = null) =
        TimelineEvent(
            event = Event.MessageEvent(
                content = TextMessageEventContent("hi", relatesTo = relatesTo),
                id = eventId,
                sender = UserId("sender", "server"),
                roomId = RoomId("room", "server"),
                originTimestamp = 24,
            ),
            previousEventId = null,
            nextEventId = null,
            gap = null
        )

    context(MessageBuilder::build.name) {
        should("call builder and return content") {
            val eventContent = TextMessageEventContent("")
            MessageBuilder(encryptedRoom, roomService, mediaService).build {
                relatesTo = RelatesTo.Replace(EventId("other"), null)
                contentBuilder = {
                    it shouldBe relatesTo
                    eventContent
                }
            } shouldBe eventContent
        }
    }

    context("replace") {
        val eventContent = TextMessageEventContent("")
        should("create create replace relation") {
            MessageBuilder(encryptedRoom, roomService, mediaService).build {
                replace(timelineEvent(EventId("bla")))
                contentBuilder = {
                    it shouldBe RelatesTo.Replace(EventId("bla"), null)
                    eventContent
                }
            } shouldBe eventContent
        }
    }

    context("reply") {
        val eventContent = TextMessageEventContent("")
        should("create create reply relation") {
            MessageBuilder(encryptedRoom, roomService, mediaService).build {
                reply(timelineEvent(EventId("bla")))
                contentBuilder = {
                    it shouldBe RelatesTo.Reply(RelatesTo.ReplyTo(EventId("bla")))
                    eventContent
                }
            } shouldBe eventContent
        }
        should("create thread aware reply") {
            MessageBuilder(encryptedRoom, roomService, mediaService).build {
                reply(timelineEvent(EventId("bla"), RelatesTo.Thread(EventId("root"))))
                contentBuilder = {
                    it shouldBe RelatesTo.Thread(EventId("root"), RelatesTo.ReplyTo(EventId("bla")), true)
                    eventContent
                }
            } shouldBe eventContent
        }
    }

    context("thread") {
        val eventContent = TextMessageEventContent("")
        should("create thread relation") {
            MessageBuilder(encryptedRoom, roomService, mediaService).build {
                thread(timelineEvent(EventId("bla"), RelatesTo.Thread(EventId("root"))))
                contentBuilder = {
                    it shouldBe RelatesTo.Thread(EventId("root"), RelatesTo.ReplyTo(EventId("bla")), true)
                    eventContent
                }
            } shouldBe eventContent
        }
        should("create thread relation from root") {
            MessageBuilder(encryptedRoom, roomService, mediaService).build {
                thread(timelineEvent(EventId("bla")))
                contentBuilder = {
                    it shouldBe RelatesTo.Thread(EventId("bla"), RelatesTo.ReplyTo(EventId("bla")), true)
                    eventContent
                }
            } shouldBe eventContent
        }
        should("create thread relation as reply") {
            MessageBuilder(encryptedRoom, roomService, mediaService).build {
                thread(timelineEvent(EventId("bla"), RelatesTo.Thread(EventId("root"))), true)
                contentBuilder = {
                    it shouldBe RelatesTo.Thread(EventId("root"), RelatesTo.ReplyTo(EventId("bla")), false)
                    eventContent
                }
            } shouldBe eventContent
        }
        should("create thread relation from root as reply") {
            MessageBuilder(encryptedRoom, roomService, mediaService).build {
                thread(timelineEvent(EventId("bla")), true)
                contentBuilder = {
                    it shouldBe RelatesTo.Thread(EventId("bla"), RelatesTo.ReplyTo(EventId("bla")), false)
                    eventContent
                }
            } shouldBe eventContent
        }
    }

    context(MessageBuilder::text.name) {
        should("create text") {
            MessageBuilder(encryptedRoom, roomService, mediaService).build {
                text("body", "format", "formatted_body")
            } shouldBe TextMessageEventContent("body", "format", "formatted_body")
        }
        should("create fallback text on replace") {
            MessageBuilder(encryptedRoom, roomService, mediaService).build {
                replace(timelineEvent(EventId("bla")))
                text("body", "format", "formatted_body")
            } shouldBe TextMessageEventContent(
                "* body",
                "format",
                "* formatted_body",
                RelatesTo.Replace(EventId("bla"), TextMessageEventContent("body", "format", "formatted_body"))
            )
        }
        should("create fallback text on reply") {
            MessageBuilder(encryptedRoom, roomService, mediaService).build {
                reply(timelineEvent(EventId("bla")))
                text("body", "format", "formatted_body")
            } shouldBe TextMessageEventContent(
                "> <@sender:server> dino\n> unicorn\nbody",
                "format",
                """
                    <mx-reply>
                    <blockquote>
                    <a href="https://matrix.to/#/!room:server/dino">In reply to</a>
                    <a href="https://matrix.to/#/@sender:server">@sender:server</a>
                    <br />
                    dino<br />unicorn
                    </blockquote>
                    </mx-reply>
                    formatted_body
                """.trimIndent(),
                RelatesTo.Reply(RelatesTo.ReplyTo(EventId("bla")))
            )
        }
        should("create fallback text on thread") {
            MessageBuilder(encryptedRoom, roomService, mediaService).build {
                thread(timelineEvent(EventId("bla")))
                text("body", "format", "formatted_body")
            } shouldBe TextMessageEventContent(
                "> <@sender:server> dino\n> unicorn\nbody",
                "format",
                """
                    <mx-reply>
                    <blockquote>
                    <a href="https://matrix.to/#/!room:server/dino">In reply to</a>
                    <a href="https://matrix.to/#/@sender:server">@sender:server</a>
                    <br />
                    dino<br />unicorn
                    </blockquote>
                    </mx-reply>
                    formatted_body
                """.trimIndent(),
                RelatesTo.Thread(EventId("bla"), RelatesTo.ReplyTo(EventId("bla")), true)
            )
        }
        should("create fallback text on reply to image") {
            val roomService = RoomServiceMock().apply {
                rooms.value = mapOf(
                    encryptedRoom to MutableStateFlow(
                        Room(
                            encryptedRoom,
                            encryptionAlgorithm = EncryptionAlgorithm.Megolm
                        )
                    ),
                    unencryptedRoom to MutableStateFlow(Room(unencryptedRoom)),
                )
                returnGetTimelineEvent = flowOf(
                    TimelineEvent(
                        event = Event.MessageEvent(
                            ImageMessageEventContent(body = "", url = "http://localhost/media/123456"), // <- image!
                            EventId("dino"),
                            UserId("sender", "server"),
                            RoomId("room", "server"),
                            1234
                        ),
                        gap = null,
                        nextEventId = null,
                        previousEventId = null,
                    )
                )
            }
            MessageBuilder(encryptedRoom, roomService, mediaService).build {
                reply(timelineEvent(EventId("bla")))
                text("body", "format", "formatted_body")
            } shouldBe TextMessageEventContent(
                "> <@sender:server> sent an image.\nbody",
                "format",
                """
                    <mx-reply>
                    <blockquote>
                    <a href="https://matrix.to/#/!room:server/dino">In reply to</a>
                    <a href="https://matrix.to/#/@sender:server">@sender:server</a>
                    <br />
                    sent an image.
                    </blockquote>
                    </mx-reply>
                    formatted_body
                """.trimIndent(),
                RelatesTo.Reply(RelatesTo.ReplyTo(EventId("bla")))
            )
        }
    }
    context(MessageBuilder::notice.name) {
        should("create notice") {
            MessageBuilder(encryptedRoom, roomService, mediaService).build {
                notice("body", "format", "formatted_body")
            } shouldBe NoticeMessageEventContent("body", "format", "formatted_body")
        }
    }
    context(MessageBuilder::emote.name) {
        should("create emote") {
            MessageBuilder(encryptedRoom, roomService, mediaService).build {
                emote("body", "format", "formatted_body")
            } shouldBe EmoteMessageEventContent("body", "format", "formatted_body")
        }
    }
    context(MessageBuilder::image.name) {
        should("create image and thumbnail") {
            val thumbnailInfo = ThumbnailInfo()
            mediaService.returnPrepareUploadThumbnail = Pair("thumbnailCacheUrl", thumbnailInfo)
            mediaService.returnPrepareUploadMedia = "mediaCacheUrl"
            MessageBuilder(unencryptedRoom, roomService, mediaService).build {
                image("body", "fake_image".toByteArray().toByteFlow(), PNG, 10, 1024, 1024)
            } shouldBe ImageMessageEventContent(
                "body", ImageInfo(
                    1024, 1024, "image/png", 10, "thumbnailCacheUrl", null,
                    thumbnailInfo,
                ), "mediaCacheUrl"
            )
        }
        should("create encrypted image and thumbnail") {
            val thumbnailInfo = ThumbnailInfo()
            val encryptedFile = EncryptedFile("", EncryptedFile.JWK(""), "", mapOf())
            val encryptedThumbnail = EncryptedFile("", EncryptedFile.JWK(""), "", mapOf())
            mediaService.returnPrepareUploadEncryptedMedia = encryptedFile
            mediaService.returnPrepareUploadEncryptedThumbnail = encryptedThumbnail to thumbnailInfo
            MessageBuilder(encryptedRoom, roomService, mediaService).build {
                image("body", "fake_image".toByteArray().toByteFlow(), PNG, 10, 1024, 1024)
            } shouldBe ImageMessageEventContent(
                "body", ImageInfo(
                    1024, 1024, "image/png", 10, null, encryptedThumbnail,
                    thumbnailInfo,
                ), null, encryptedFile
            )
        }
    }
    context(MessageBuilder::file.name) {
        should("create file and thumbnail") {
            val thumbnailInfo = ThumbnailInfo()
            mediaService.returnPrepareUploadThumbnail = Pair("thumbnailCacheUrl", thumbnailInfo)
            mediaService.returnPrepareUploadMedia = "mediaCacheUrl"
            MessageBuilder(unencryptedRoom, roomService, mediaService).build {
                file("body", "fake_file".toByteArray().toByteFlow(), PNG, 9, "filename")
            } shouldBe FileMessageEventContent(
                "body", "filename", FileInfo(
                    "image/png", 9, "thumbnailCacheUrl", null, thumbnailInfo,
                ), "mediaCacheUrl"
            )
        }
        should("create encrypted file and thumbnail") {
            val thumbnailInfo = ThumbnailInfo()
            val encryptedFile = EncryptedFile("", EncryptedFile.JWK(""), "", mapOf())
            val encryptedThumbnail = EncryptedFile("", EncryptedFile.JWK(""), "", mapOf())
            mediaService.returnPrepareUploadEncryptedMedia = encryptedFile
            mediaService.returnPrepareUploadEncryptedThumbnail = encryptedThumbnail to thumbnailInfo
            MessageBuilder(encryptedRoom, roomService, mediaService).build {
                file("body", "fake_file".toByteArray().toByteFlow(), PNG, 9, "filename")
            } shouldBe FileMessageEventContent(
                "body", "filename", FileInfo(
                    "image/png", 9, null, encryptedThumbnail, thumbnailInfo,
                ), null, encryptedFile
            )
        }
    }
    context(MessageBuilder::video.name) {
        should("create video and thumbnail") {
            val thumbnailInfo = ThumbnailInfo()
            mediaService.returnPrepareUploadThumbnail = Pair("thumbnailCacheUrl", thumbnailInfo)
            mediaService.returnPrepareUploadMedia = "mediaCacheUrl"
            MessageBuilder(unencryptedRoom, roomService, mediaService).build {
                video("body", "fake_video".toByteArray().toByteFlow(), MP4, 10, 1024, 1024, 1024)
            } shouldBe VideoMessageEventContent(
                "body", VideoInfo(
                    1024, 1024, 1024, "video/mp4", 10, "thumbnailCacheUrl", null,
                    thumbnailInfo,
                ), "mediaCacheUrl"
            )
        }
        should("create encrypted video and thumbnail") {
            val thumbnailInfo = ThumbnailInfo()
            val encryptedFile = EncryptedFile("", EncryptedFile.JWK(""), "", mapOf())
            val encryptedThumbnail = EncryptedFile("", EncryptedFile.JWK(""), "", mapOf())
            mediaService.returnPrepareUploadEncryptedMedia = encryptedFile
            mediaService.returnPrepareUploadEncryptedThumbnail = encryptedThumbnail to thumbnailInfo
            MessageBuilder(encryptedRoom, roomService, mediaService).build {
                video("body", "fake_video".toByteArray().toByteFlow(), MP4, 10, 1024, 1024, 1024)
            } shouldBe VideoMessageEventContent(
                "body", VideoInfo(
                    1024, 1024, 1024, "video/mp4", 10, null, encryptedThumbnail,
                    thumbnailInfo,
                ), null, encryptedFile
            )
        }
    }
    context(MessageBuilder::audio.name) {
        should("create audio") {
            mediaService.returnPrepareUploadMedia = "mediaCacheUrl"
            MessageBuilder(unencryptedRoom, roomService, mediaService).build {
                audio("body", "fake_audio".toByteArray().toByteFlow(), OGG, 10, 1024)
            } shouldBe AudioMessageEventContent(
                "body", AudioInfo(1024, "audio/ogg", 10), "mediaCacheUrl"
            )
        }
        should("create encrypted audio") {
            val encryptedFile = EncryptedFile("", EncryptedFile.JWK(""), "", mapOf())
            mediaService.returnPrepareUploadEncryptedMedia = encryptedFile
            MessageBuilder(encryptedRoom, roomService, mediaService).build {
                audio("body", "fake_audio".toByteArray().toByteFlow(), OGG, 10, 1024)
            } shouldBe AudioMessageEventContent(
                "body", AudioInfo(1024, "audio/ogg", 10), null, encryptedFile
            )
        }
    }
})