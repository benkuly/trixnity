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
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.m.Mentions
import net.folivo.trixnity.core.model.events.m.ReactionEventContent
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.room.*
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
                encryptedRoom to MutableStateFlow(Room(encryptedRoom, encrypted = true)),
                unencryptedRoom to MutableStateFlow(Room(unencryptedRoom)),
            )
            returnGetTimelineEvent = flowOf(
                TimelineEvent(
                    event = MessageEvent(
                        RoomMessageEventContent.TextBased.Text(
                            """
                            dino
                            unicorn
                        """.trimIndent()
                        ),
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
                content = RoomMessageEventContent.TextBased.Text("hi", relatesTo = relatesTo, mentions = mentions),
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
            val eventContent = RoomMessageEventContent.TextBased.Text("")
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                relatesTo = RelatesTo.Replace(EventId("other"), null)
                contentBuilder = {
                    this.relatesTo shouldBe this@build.relatesTo
                    eventContent
                }
            } shouldBe eventContent
        }
    }

    context("replace") {
        val eventContent = RoomMessageEventContent.TextBased.Text("")
        should("create create replace relation") {
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                replace(timelineEvent(EventId("bla")))
                contentBuilder = {
                    relatesTo shouldBe RelatesTo.Replace(EventId("bla"), null)
                    eventContent
                }
            } shouldBe eventContent
        }
    }

    context("reply") {
        val eventContent = RoomMessageEventContent.TextBased.Text("")
        should("create reply relation") {
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                reply(timelineEvent(EventId("bla")))
                contentBuilder = {
                    relatesTo shouldBe RelatesTo.Reply(RelatesTo.ReplyTo(EventId("bla")))
                    eventContent
                }
            } shouldBe eventContent
        }
        should("create thread aware reply") {
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                reply(timelineEvent(EventId("bla"), RelatesTo.Thread(EventId("root"))))
                contentBuilder = {
                    relatesTo shouldBe RelatesTo.Thread(EventId("root"), RelatesTo.ReplyTo(EventId("bla")), true)
                    eventContent
                }
            } shouldBe eventContent
        }
    }

    context("thread") {
        val eventContent = RoomMessageEventContent.TextBased.Text("")
        should("create thread relation") {
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                thread(timelineEvent(EventId("bla"), RelatesTo.Thread(EventId("root"))))
                contentBuilder = {
                    relatesTo shouldBe RelatesTo.Thread(EventId("root"), RelatesTo.ReplyTo(EventId("bla")), true)
                    eventContent
                }
            } shouldBe eventContent
        }
        should("create thread relation from root") {
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                thread(timelineEvent(EventId("bla")))
                contentBuilder = {
                    relatesTo shouldBe RelatesTo.Thread(EventId("bla"), RelatesTo.ReplyTo(EventId("bla")), true)
                    eventContent
                }
            } shouldBe eventContent
        }
        should("create thread relation as reply") {
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                thread(timelineEvent(EventId("bla"), RelatesTo.Thread(EventId("root"))), true)
                contentBuilder = {
                    relatesTo shouldBe RelatesTo.Thread(EventId("root"), RelatesTo.ReplyTo(EventId("bla")), false)
                    eventContent
                }
            } shouldBe eventContent
        }
        should("create thread relation from root as reply") {
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                thread(timelineEvent(EventId("bla")), true)
                contentBuilder = {
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
        val eventContent = RoomMessageEventContent.TextBased.Text("")
        should("add mention") {
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                mentions(UserId("bla"), room = true)
                contentBuilder = {
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
                contentBuilder = {
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
                contentBuilder = {
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
                contentBuilder = {
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
                text(body = "body", format = "format", formattedBody = "formatted_body")
            } shouldBe RoomMessageEventContent.TextBased.Text("body", "format", "formatted_body", mentions = Mentions())
        }
        should("create fallback text on replace") {
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                replace(timelineEvent(EventId("bla")))
                text(body = "body", format = "format", formattedBody = "formatted_body")
            } shouldBe RoomMessageEventContent.TextBased.Text(
                "* body",
                "format",
                "* formatted_body",
                RelatesTo.Replace(
                    EventId("bla"),
                    RoomMessageEventContent.TextBased.Text("body", "format", "formatted_body", mentions = Mentions()),
                ),
                mentions = Mentions()
            )
        }
        should("create fallback text on reply") {
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                reply(timelineEvent(EventId("bla")))
                text(body = "body", format = "format", formattedBody = "formatted_body")
            } shouldBe RoomMessageEventContent.TextBased.Text(
                """
                    > <@sender:server> dino
                    > unicorn
                    
                    body
                """.trimIndent(),
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
        should("not nest fallback text on reply") {
            roomService.returnGetTimelineEvent = flowOf(
                TimelineEvent(
                    event = MessageEvent(
                        RoomMessageEventContent.TextBased.Text(
                            """
                                > <@other:server> bla
                                > blub
                    
                                dino
                                unicorn
                            """.trimIndent(),
                            relatesTo = RelatesTo.Reply(RelatesTo.ReplyTo(EventId("otherEvent")))
                        ),
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
                text(body = "body", format = "format", formattedBody = "formatted_body")
            } shouldBe RoomMessageEventContent.TextBased.Text(
                """
                    > <@sender:server> dino
                    > unicorn
                    
                    body
                """.trimIndent(),
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
        should("not nest fallback text on rich reply") {
            roomService.returnGetTimelineEvent = flowOf(
                TimelineEvent(
                    event = MessageEvent(
                        RoomMessageEventContent.TextBased.Text(
                            """
                                > <@other:server> bla
                                > blub
                    
                                dino
                                unicorn
                            """.trimIndent(),
                            "org.matrix.custom.html",
                            """
                                <mx-reply>
                                <blockquote>
                                <a href="https://matrix.to/#/!room:server/dino">In reply to</a>
                                <a href="https://matrix.to/#/@other:server">@other:server</a>
                                <br />
                                bla_formatted<br />blub_formatted
                                </blockquote>
                                </mx-reply>
                                dino_formatted<br />unicorn_formatted
                            """.trimIndent(),
                            relatesTo = RelatesTo.Reply(RelatesTo.ReplyTo(EventId("otherEvent")))
                        ),
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
                text(body = "body", format = "format", formattedBody = "formatted_body")
            } shouldBe RoomMessageEventContent.TextBased.Text(
                """
                    > <@sender:server> dino
                    > unicorn
                    
                    body
                """.trimIndent(),
                "org.matrix.custom.html",
                """
                    <mx-reply>
                    <blockquote>
                    <a href="https://matrix.to/#/!room:server/dino">In reply to</a>
                    <a href="https://matrix.to/#/@sender:server">@sender:server</a>
                    <br />
                    dino_formatted<br />unicorn_formatted
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
                text(body = "body", format = "format", formattedBody = "formatted_body")
            } shouldBe RoomMessageEventContent.TextBased.Text(
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
                        RoomMessageEventContent.FileBased.Image(
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
                text(body = "body", format = "format", formattedBody = "formatted_body")
            } shouldBe RoomMessageEventContent.TextBased.Text(
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
                notice(body = "body", format = "format", formattedBody = "formatted_body")
            } shouldBe RoomMessageEventContent.TextBased.Notice(
                "body",
                "format",
                "formatted_body",
                mentions = Mentions()
            )
        }
    }
    context(MessageBuilder::emote.name) {
        should("create emote") {
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                emote(body = "body", format = "format", formattedBody = "formatted_body")
            } shouldBe RoomMessageEventContent.TextBased.Emote(
                "body",
                "format",
                "formatted_body",
                mentions = Mentions()
            )
        }
    }
    context(MessageBuilder::image.name) {
        should("create image and thumbnail") {
            val thumbnailInfo = ThumbnailInfo()
            mediaService.returnPrepareUploadMedia.add("mediaCacheUrl")
            mediaService.returnPrepareUploadMedia.add("thumbnailCacheUrl")
            MessageBuilder(unencryptedRoom, roomService, mediaService, ownUserId).build {
                image(
                    body = "body",
                    image = "fake_image".toByteArray().toByteArrayFlow(),
                    format = null,
                    formattedBody = null,
                    fileName = null,
                    type = PNG,
                    size = 10,
                    height = 1024,
                    width = 1024,
                    thumbnail = "fake_thumbnail".toByteArray().toByteArrayFlow(),
                    thumbnailInfo = thumbnailInfo
                )
            } shouldBe RoomMessageEventContent.FileBased.Image(
                "body", info = ImageInfo(
                    1024, 1024, "image/png", 10, "thumbnailCacheUrl", null,
                    thumbnailInfo,
                ), url = "mediaCacheUrl",
                mentions = Mentions()
            )
        }
        should("create encrypted image and thumbnail") {
            val thumbnailInfo = ThumbnailInfo()
            val encryptedFile = EncryptedFile("", EncryptedFile.JWK(""), "", mapOf())
            val encryptedThumbnail = EncryptedFile("", EncryptedFile.JWK(""), "", mapOf())
            mediaService.returnPrepareUploadEncryptedMedia.add(encryptedFile)
            mediaService.returnPrepareUploadEncryptedMedia.add(encryptedThumbnail)
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                image(
                    body = "body",
                    image = "fake_image".toByteArray().toByteArrayFlow(),
                    format = null,
                    formattedBody = null,
                    fileName = null,
                    type = PNG,
                    size = 10,
                    height = 1024,
                    width = 1024,
                    thumbnail = "fake_thumbnaul".toByteArray().toByteArrayFlow(),
                    thumbnailInfo = thumbnailInfo
                )
            } shouldBe RoomMessageEventContent.FileBased.Image(
                "body", info = ImageInfo(
                    1024, 1024, "image/png", 10, null, encryptedThumbnail,
                    thumbnailInfo,
                ), url = null, file = encryptedFile,
                mentions = Mentions()
            )
        }
    }
    context(MessageBuilder::file.name) {
        should("create file and thumbnail") {
            val thumbnailInfo = ThumbnailInfo()
            mediaService.returnPrepareUploadMedia.add("mediaCacheUrl")
            mediaService.returnPrepareUploadMedia.add("thumbnailCacheUrl")
            MessageBuilder(unencryptedRoom, roomService, mediaService, ownUserId).build {
                file(
                    body = "body",
                    file = "fake_file".toByteArray().toByteArrayFlow(),
                    format = null,
                    formattedBody = null,
                    fileName = "filename",
                    type = PNG,
                    size = 9,
                    thumbnail = "fake_thumbnaul".toByteArray().toByteArrayFlow(),
                    thumbnailInfo = thumbnailInfo
                )
            } shouldBe RoomMessageEventContent.FileBased.File(
                "body", fileName = "filename", info = FileInfo(
                    "image/png", 9, "thumbnailCacheUrl", null, thumbnailInfo,
                ), url = "mediaCacheUrl",
                mentions = Mentions()
            )
        }
        should("create encrypted file and thumbnail") {
            val thumbnailInfo = ThumbnailInfo()
            val encryptedFile = EncryptedFile("", EncryptedFile.JWK(""), "", mapOf())
            val encryptedThumbnail = EncryptedFile("", EncryptedFile.JWK(""), "", mapOf())
            mediaService.returnPrepareUploadEncryptedMedia.add(encryptedFile)
            mediaService.returnPrepareUploadEncryptedMedia.add(encryptedThumbnail)
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                file(
                    body = "body",
                    file = "fake_file".toByteArray().toByteArrayFlow(),
                    format = null,
                    formattedBody = null,
                    fileName = "filename",
                    type = PNG,
                    size = 9,
                    thumbnail = "fake_thumbnaul".toByteArray().toByteArrayFlow(),
                    thumbnailInfo = thumbnailInfo
                )
            } shouldBe RoomMessageEventContent.FileBased.File(
                "body", fileName = "filename", info = FileInfo(
                    "image/png", 9, null, encryptedThumbnail, thumbnailInfo,
                ), url = null, file = encryptedFile,
                mentions = Mentions()
            )
        }
    }
    context(MessageBuilder::video.name) {
        should("create video and thumbnail") {
            val thumbnailInfo = ThumbnailInfo()
            mediaService.returnPrepareUploadMedia.add("mediaCacheUrl")
            mediaService.returnPrepareUploadMedia.add("thumbnailCacheUrl")
            MessageBuilder(unencryptedRoom, roomService, mediaService, ownUserId).build {
                video(
                    body = "body",
                    video = "fake_video".toByteArray().toByteArrayFlow(),
                    format = null,
                    formattedBody = null,
                    fileName = null,
                    type = MP4,
                    size = 10,
                    height = 1024,
                    width = 1024,
                    duration = 1024,
                    thumbnail = "fake_thumbnaul".toByteArray().toByteArrayFlow(),
                    thumbnailInfo = thumbnailInfo
                )
            } shouldBe RoomMessageEventContent.FileBased.Video(
                "body", info = VideoInfo(
                    1024, 1024, 1024, "video/mp4", 10, "thumbnailCacheUrl", null,
                    thumbnailInfo,
                ), url = "mediaCacheUrl",
                mentions = Mentions()
            )
        }
        should("create encrypted video and thumbnail") {
            val thumbnailInfo = ThumbnailInfo()
            val encryptedFile = EncryptedFile("", EncryptedFile.JWK(""), "", mapOf())
            val encryptedThumbnail = EncryptedFile("", EncryptedFile.JWK(""), "", mapOf())
            mediaService.returnPrepareUploadEncryptedMedia.add(encryptedFile)
            mediaService.returnPrepareUploadEncryptedMedia.add(encryptedThumbnail)
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                video(
                    body = "body",
                    video = "fake_video".toByteArray().toByteArrayFlow(),
                    format = null,
                    formattedBody = null,
                    fileName = null,
                    type = MP4,
                    size = 10,
                    height = 1024,
                    width = 1024,
                    duration = 1024,
                    thumbnail = "fake_thumbnaul".toByteArray().toByteArrayFlow(),
                    thumbnailInfo = thumbnailInfo
                )
            } shouldBe RoomMessageEventContent.FileBased.Video(
                "body", info = VideoInfo(
                    1024, 1024, 1024, "video/mp4", 10, null, encryptedThumbnail,
                    thumbnailInfo,
                ), url = null, file = encryptedFile,
                mentions = Mentions()
            )
        }
    }
    context(MessageBuilder::audio.name) {
        should("create audio") {
            mediaService.returnPrepareUploadMedia.add("mediaCacheUrl")
            MessageBuilder(unencryptedRoom, roomService, mediaService, ownUserId).build {
                audio(
                    body = "body",
                    audio = "fake_audio".toByteArray().toByteArrayFlow(),
                    format = null,
                    formattedBody = null,
                    fileName = null,
                    type = OGG,
                    size = 10,
                    duration = 1024
                )
            } shouldBe RoomMessageEventContent.FileBased.Audio(
                "body", info = AudioInfo(1024, "audio/ogg", 10), url = "mediaCacheUrl",
                mentions = Mentions()
            )
        }
        should("create encrypted audio") {
            val encryptedFile = EncryptedFile("", EncryptedFile.JWK(""), "", mapOf())
            mediaService.returnPrepareUploadEncryptedMedia.add(encryptedFile)
            MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
                audio(
                    body = "body",
                    audio = "fake_audio".toByteArray().toByteArrayFlow(),
                    format = null,
                    formattedBody = null,
                    fileName = null,
                    type = OGG,
                    size = 10,
                    duration = 1024
                )
            } shouldBe RoomMessageEventContent.FileBased.Audio(
                "body", info = AudioInfo(1024, "audio/ogg", 10), url = null, file = encryptedFile,
                mentions = Mentions()
            )
        }
    }
})