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
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.Mentions
import net.folivo.trixnity.core.model.events.m.ReactionEventContent
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.*
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import net.folivo.trixnity.utils.toByteArrayFlow

class MessageBuilderTest : ShouldSpec({
    timeout = 60_000
    val ownUserId = UserId("me", "server")
    val encryptedRoom = RoomId("encryptedRoom", "server")
    val unencryptedRoom = RoomId("unencryptedRoom", "server")
    lateinit var roomService: RoomServiceMock

    beforeTest {
        roomService = RoomServiceMock().apply {
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
                    event = MessageEvent(
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
    }
    val mediaService = MediaServiceMock()


    fun timelineEvent(eventId: EventId, relatesTo: RelatesTo? = null, mentions: Mentions? = null) =
        TimelineEvent(
            event = MessageEvent(
                content = TextMessageEventContent("hi", relatesTo = relatesTo, mentions = mentions),
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
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                relatesTo = RelatesTo.Replace(EventId("other"), null)
                contentBuilder = { relatesTo, _, _ ->
                    relatesTo shouldBe this.relatesTo
                    eventContent
                }
            } shouldBe eventContent
        }
    }

    context("replace") {
        val eventContent = TextMessageEventContent("")
        should("create create replace relation") {
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                replace(timelineEvent(EventId("bla")))
                contentBuilder = { relatesTo, _, _ ->
                    relatesTo shouldBe RelatesTo.Replace(EventId("bla"), null)
                    eventContent
                }
            } shouldBe eventContent
        }
    }

    context("reply") {
        val eventContent = TextMessageEventContent("")
        should("create create reply relation") {
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                reply(timelineEvent(EventId("bla")))
                contentBuilder = { relatesTo, _, _ ->
                    relatesTo shouldBe RelatesTo.Reply(RelatesTo.ReplyTo(EventId("bla")))
                    eventContent
                }
            } shouldBe eventContent
        }
        should("create thread aware reply") {
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                reply(timelineEvent(EventId("bla"), RelatesTo.Thread(EventId("root"))))
                contentBuilder = { relatesTo, _, _ ->
                    relatesTo shouldBe RelatesTo.Thread(EventId("root"), RelatesTo.ReplyTo(EventId("bla")), true)
                    eventContent
                }
            } shouldBe eventContent
        }
    }

    context("thread") {
        val eventContent = TextMessageEventContent("")
        should("create thread relation") {
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                thread(timelineEvent(EventId("bla"), RelatesTo.Thread(EventId("root"))))
                contentBuilder = { relatesTo, _, _ ->
                    relatesTo shouldBe RelatesTo.Thread(EventId("root"), RelatesTo.ReplyTo(EventId("bla")), true)
                    eventContent
                }
            } shouldBe eventContent
        }
        should("create thread relation from root") {
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                thread(timelineEvent(EventId("bla")))
                contentBuilder = { relatesTo, _, _ ->
                    relatesTo shouldBe RelatesTo.Thread(EventId("bla"), RelatesTo.ReplyTo(EventId("bla")), true)
                    eventContent
                }
            } shouldBe eventContent
        }
        should("create thread relation as reply") {
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                thread(timelineEvent(EventId("bla"), RelatesTo.Thread(EventId("root"))), true)
                contentBuilder = { relatesTo, _, _ ->
                    relatesTo shouldBe RelatesTo.Thread(EventId("root"), RelatesTo.ReplyTo(EventId("bla")), false)
                    eventContent
                }
            } shouldBe eventContent
        }
        should("create thread relation from root as reply") {
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                thread(timelineEvent(EventId("bla")), true)
                contentBuilder = { relatesTo, _, _ ->
                    relatesTo shouldBe RelatesTo.Thread(EventId("bla"), RelatesTo.ReplyTo(EventId("bla")), false)
                    eventContent
                }
            } shouldBe eventContent
        }
    }
    context("react") {
        should("react") {
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                react(EventId("bla"), "👍")
            } shouldBe ReactionEventContent(RelatesTo.Annotation(EventId("bla"), "👍"))
        }
    }
    context(MessageBuilder::mentions.name) {
        val eventContent = TextMessageEventContent("")
        should("add mention") {
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                mentions(UserId("bla"), room = true)
                contentBuilder = { _, mentions, newContentMentions ->
                    mentions shouldBe Mentions(setOf(UserId("bla")), true)
                    newContentMentions shouldBe Mentions()
                    eventContent
                }
            } shouldBe eventContent
        }
        should("replace old mention") {
            roomService.returnGetTimelineEvent = flowOf(
                timelineEvent(EventId("bla"), mentions = Mentions(setOf(UserId("1"), UserId("2"))))
            )
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                mentions(UserId("1"), UserId("3"), room = true)
                replace(EventId("bla"))
                contentBuilder = { _, mentions, newContentMentions ->
                    mentions shouldBe Mentions(setOf(UserId("3")), true)
                    newContentMentions shouldBe Mentions(setOf(UserId("1"), UserId("3")), true)
                    eventContent
                }
            } shouldBe eventContent
        }
        should("add mention on reply") {
            roomService.returnGetTimelineEvent = flowOf(
                timelineEvent(EventId("bla"), mentions = Mentions(setOf(UserId("1"))))
            )
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                mentions(UserId("2"), room = true)
                reply(EventId("bla"), null)
                contentBuilder = { _, mentions, newContentMentions ->
                    mentions shouldBe Mentions(setOf(UserId("sender", "server"), UserId("1"), UserId("2")), true)
                    newContentMentions shouldBe Mentions()
                    eventContent
                }
            } shouldBe eventContent
        }
        should("remove own mention") {
            roomService.returnGetTimelineEvent = flowOf(
                timelineEvent(EventId("bla"), mentions = Mentions(setOf(UserId("1"))))
            )
            MessageBuilder(encryptedRoom, roomService, mediaService, UserId("sender", "server")).build {
                mentions(UserId("2"), room = true)
                reply(EventId("bla"), null)
                contentBuilder = { _, mentions, newContentMentions ->
                    mentions shouldBe Mentions(setOf(UserId("1"), UserId("2")), true)
                    newContentMentions shouldBe Mentions()
                    eventContent
                }
            } shouldBe eventContent
        }
    }
    context(MessageBuilder::text.name) {
        should("create text") {
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                text("body", "format", "formatted_body")
            } shouldBe TextMessageEventContent("body", "format", "formatted_body", mentions = Mentions())
        }
        should("create fallback text on replace") {
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                replace(timelineEvent(EventId("bla")))
                text("body", "format", "formatted_body")
            } shouldBe TextMessageEventContent(
                "* body",
                "format",
                "* formatted_body",
                RelatesTo.Replace(
                    EventId("bla"),
                    TextMessageEventContent("body", "format", "formatted_body", mentions = Mentions()),
                ),
                mentions = Mentions()
            )
        }
        should("create fallback text on reply") {
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                reply(timelineEvent(EventId("bla")))
                text("body", "format", "formatted_body")
            } shouldBe TextMessageEventContent(
                "> <@sender:server> dino\n> unicorn\n\nbody",
                "org.matrix.custom.html",
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
                RelatesTo.Reply(RelatesTo.ReplyTo(EventId("bla"))),
                mentions = Mentions(users = setOf(UserId("sender", "server")))
            )
        }
        should("create fallback text on thread") {
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                thread(timelineEvent(EventId("bla")))
                text("body", "format", "formatted_body")
            } shouldBe TextMessageEventContent(
                "> <@sender:server> dino\n> unicorn\n\nbody",
                "org.matrix.custom.html",
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
                RelatesTo.Thread(EventId("bla"), RelatesTo.ReplyTo(EventId("bla")), true),
                mentions = Mentions()
            )
        }
        should("create fallback text on reply to image") {
            roomService.returnGetTimelineEvent = flowOf(
                TimelineEvent(
                    event = MessageEvent(
                        ImageMessageEventContent(
                            body = "image.png",
                            url = "http://localhost/media/123456",
                            mentions = Mentions()
                        ), // <- image!
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
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                reply(timelineEvent(EventId("bla")))
                text("body", "format", "formatted_body")
            } shouldBe TextMessageEventContent(
                "> <@sender:server> image.png\n\nbody",
                "org.matrix.custom.html",
                """
                    <mx-reply>
                    <blockquote>
                    <a href="https://matrix.to/#/!room:server/dino">In reply to</a>
                    <a href="https://matrix.to/#/@sender:server">@sender:server</a>
                    <br />
                    image.png
                    </blockquote>
                    </mx-reply>
                    formatted_body
                """.trimIndent(),
                RelatesTo.Reply(RelatesTo.ReplyTo(EventId("bla"))),
                mentions = Mentions(users = setOf(UserId("sender", "server")))
            )
        }
    }
    context(MessageBuilder::notice.name) {
        should("create notice") {
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                notice("body", "format", "formatted_body")
            } shouldBe NoticeMessageEventContent("body", "format", "formatted_body", mentions = Mentions())
        }
    }
    context(MessageBuilder::emote.name) {
        should("create emote") {
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                emote("body", "format", "formatted_body")
            } shouldBe EmoteMessageEventContent("body", "format", "formatted_body", mentions = Mentions())
        }
    }
    context(MessageBuilder::image.name) {
        should("create image and thumbnail") {
            val thumbnailInfo = ThumbnailInfo()
            mediaService.returnPrepareUploadThumbnail = Pair("thumbnailCacheUrl", thumbnailInfo)
            mediaService.returnPrepareUploadMedia = "mediaCacheUrl"
            MessageBuilder(unencryptedRoom, roomService, mediaService, ownUserId).build {
                image("body", "fake_image".toByteArray().toByteArrayFlow(), PNG, 10, 1024, 1024)
            } shouldBe ImageMessageEventContent(
                "body", ImageInfo(
                    1024, 1024, "image/png", 10, "thumbnailCacheUrl", null,
                    thumbnailInfo,
                ), "mediaCacheUrl",
                mentions = Mentions()
            )
        }
        should("create encrypted image and thumbnail") {
            val thumbnailInfo = ThumbnailInfo()
            val encryptedFile = EncryptedFile("", EncryptedFile.JWK(""), "", mapOf())
            val encryptedThumbnail = EncryptedFile("", EncryptedFile.JWK(""), "", mapOf())
            mediaService.returnPrepareUploadEncryptedMedia = encryptedFile
            mediaService.returnPrepareUploadEncryptedThumbnail = encryptedThumbnail to thumbnailInfo
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                image("body", "fake_image".toByteArray().toByteArrayFlow(), PNG, 10, 1024, 1024)
            } shouldBe ImageMessageEventContent(
                "body", ImageInfo(
                    1024, 1024, "image/png", 10, null, encryptedThumbnail,
                    thumbnailInfo,
                ), null, encryptedFile,
                mentions = Mentions()
            )
        }
    }
    context(MessageBuilder::file.name) {
        should("create file and thumbnail") {
            val thumbnailInfo = ThumbnailInfo()
            mediaService.returnPrepareUploadThumbnail = Pair("thumbnailCacheUrl", thumbnailInfo)
            mediaService.returnPrepareUploadMedia = "mediaCacheUrl"
            MessageBuilder(unencryptedRoom, roomService, mediaService, ownUserId).build {
                file("body", "fake_file".toByteArray().toByteArrayFlow(), PNG, 9, "filename")
            } shouldBe FileMessageEventContent(
                "body", "filename", FileInfo(
                    "image/png", 9, "thumbnailCacheUrl", null, thumbnailInfo,
                ), "mediaCacheUrl",
                mentions = Mentions()
            )
        }
        should("create encrypted file and thumbnail") {
            val thumbnailInfo = ThumbnailInfo()
            val encryptedFile = EncryptedFile("", EncryptedFile.JWK(""), "", mapOf())
            val encryptedThumbnail = EncryptedFile("", EncryptedFile.JWK(""), "", mapOf())
            mediaService.returnPrepareUploadEncryptedMedia = encryptedFile
            mediaService.returnPrepareUploadEncryptedThumbnail = encryptedThumbnail to thumbnailInfo
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                file("body", "fake_file".toByteArray().toByteArrayFlow(), PNG, 9, "filename")
            } shouldBe FileMessageEventContent(
                "body", "filename", FileInfo(
                    "image/png", 9, null, encryptedThumbnail, thumbnailInfo,
                ), null, encryptedFile,
                mentions = Mentions()
            )
        }
    }
    context(MessageBuilder::video.name) {
        should("create video and thumbnail") {
            val thumbnailInfo = ThumbnailInfo()
            mediaService.returnPrepareUploadThumbnail = Pair("thumbnailCacheUrl", thumbnailInfo)
            mediaService.returnPrepareUploadMedia = "mediaCacheUrl"
            MessageBuilder(unencryptedRoom, roomService, mediaService, ownUserId).build {
                video("body", "fake_video".toByteArray().toByteArrayFlow(), MP4, 10, 1024, 1024, 1024)
            } shouldBe VideoMessageEventContent(
                "body", VideoInfo(
                    1024, 1024, 1024, "video/mp4", 10, "thumbnailCacheUrl", null,
                    thumbnailInfo,
                ), "mediaCacheUrl",
                mentions = Mentions()
            )
        }
        should("create encrypted video and thumbnail") {
            val thumbnailInfo = ThumbnailInfo()
            val encryptedFile = EncryptedFile("", EncryptedFile.JWK(""), "", mapOf())
            val encryptedThumbnail = EncryptedFile("", EncryptedFile.JWK(""), "", mapOf())
            mediaService.returnPrepareUploadEncryptedMedia = encryptedFile
            mediaService.returnPrepareUploadEncryptedThumbnail = encryptedThumbnail to thumbnailInfo
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                video("body", "fake_video".toByteArray().toByteArrayFlow(), MP4, 10, 1024, 1024, 1024)
            } shouldBe VideoMessageEventContent(
                "body", VideoInfo(
                    1024, 1024, 1024, "video/mp4", 10, null, encryptedThumbnail,
                    thumbnailInfo,
                ), null, encryptedFile,
                mentions = Mentions()
            )
        }
    }
    context(MessageBuilder::audio.name) {
        should("create audio") {
            mediaService.returnPrepareUploadMedia = "mediaCacheUrl"
            MessageBuilder(unencryptedRoom, roomService, mediaService, ownUserId).build {
                audio("body", "fake_audio".toByteArray().toByteArrayFlow(), OGG, 10, 1024)
            } shouldBe AudioMessageEventContent(
                "body", AudioInfo(1024, "audio/ogg", 10), "mediaCacheUrl",
                mentions = Mentions()
            )
        }
        should("create encrypted audio") {
            val encryptedFile = EncryptedFile("", EncryptedFile.JWK(""), "", mapOf())
            mediaService.returnPrepareUploadEncryptedMedia = encryptedFile
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                audio("body", "fake_audio".toByteArray().toByteArrayFlow(), OGG, 10, 1024)
            } shouldBe AudioMessageEventContent(
                "body", AudioInfo(1024, "audio/ogg", 10), null, encryptedFile,
                mentions = Mentions()
            )
        }
    }
})