package net.folivo.trixnity.client.room.message

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
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.utils.toByteArrayFlow
import kotlin.test.Test

class MessageBuilderTest : TrixnityBaseTest() {

    private val ownUserId = UserId("me", "server")
    private val encryptedRoom = RoomId("encryptedRoom", "server")
    private val unencryptedRoom = RoomId("unencryptedRoom", "server")

    private val mediaService = MediaServiceMock()
    private val roomService = RoomServiceMock().apply {
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


    @Test
    fun `build » call builder and return content`() = runTest {
        val eventContent = RoomMessageEventContent.TextBased.Text("")
        MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
            relatesTo = RelatesTo.Replace(EventId("other"), null)
            contentBuilder = {
                this.relatesTo shouldBe this@build.relatesTo
                eventContent
            }
        } shouldBe eventContent
    }

    private val replaceEventContent = RoomMessageEventContent.TextBased.Text("")

    @Test
    fun `replace » create create replace relation`() = runTest {
        MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
            replace(timelineEvent(EventId("bla")))
            contentBuilder = {
                relatesTo shouldBe RelatesTo.Replace(EventId("bla"), null)
                replaceEventContent
            }
        } shouldBe replaceEventContent
    }

    private val replyEventContent = RoomMessageEventContent.TextBased.Text("")

    @Test
    fun `reply » create reply relation`() = runTest {
        MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
            reply(timelineEvent(EventId("bla")))
            contentBuilder = {
                relatesTo shouldBe RelatesTo.Reply(RelatesTo.ReplyTo(EventId("bla")))
                replyEventContent
            }
        } shouldBe replyEventContent
    }

    @Test
    fun `reply » create thread aware reply`() = runTest {
        MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
            reply(timelineEvent(EventId("bla"), RelatesTo.Thread(EventId("root"))))
            contentBuilder = {
                relatesTo shouldBe RelatesTo.Thread(EventId("root"), RelatesTo.ReplyTo(EventId("bla")), true)
                replyEventContent
            }
        } shouldBe replyEventContent
    }


    private val threadEventContent = RoomMessageEventContent.TextBased.Text("")

    @Test
    fun `thread » create thread relation`() = runTest {
        MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
            thread(timelineEvent(EventId("bla"), RelatesTo.Thread(EventId("root"))))
            contentBuilder = {
                relatesTo shouldBe RelatesTo.Thread(EventId("root"), RelatesTo.ReplyTo(EventId("bla")), true)
                threadEventContent
            }
        } shouldBe threadEventContent
    }

    @Test
    fun `thread » create thread relation from root`() = runTest {
        MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
            thread(timelineEvent(EventId("bla")))
            contentBuilder = {
                relatesTo shouldBe RelatesTo.Thread(EventId("bla"), RelatesTo.ReplyTo(EventId("bla")), true)
                threadEventContent
            }
        } shouldBe threadEventContent
    }

    @Test
    fun `thread » create thread relation as reply`() = runTest {
        MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
            thread(timelineEvent(EventId("bla"), RelatesTo.Thread(EventId("root"))), true)
            contentBuilder = {
                relatesTo shouldBe RelatesTo.Thread(EventId("root"), RelatesTo.ReplyTo(EventId("bla")), false)
                threadEventContent
            }
        } shouldBe threadEventContent
    }

    @Test
    fun `thread » create thread relation from root as reply`() = runTest {
        MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
            thread(timelineEvent(EventId("bla")), true)
            contentBuilder = {
                relatesTo shouldBe RelatesTo.Thread(EventId("bla"), RelatesTo.ReplyTo(EventId("bla")), false)
                threadEventContent
            }
        } shouldBe threadEventContent
    }


    @Test
    fun `react » react`() = runTest {
        MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
            react(EventId("bla"), "👍")
        } shouldBe ReactionEventContent(RelatesTo.Annotation(EventId("bla"), "👍"))
    }


    private val mentionsEventContent = RoomMessageEventContent.TextBased.Text("")

    @Test
    fun `mentions » add mention`() = runTest {
        MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
            mentions(UserId("bla"), room = true)
            contentBuilder = {
                mentions shouldBe Mentions(setOf(UserId("bla")), true)
                newContentMentions shouldBe Mentions()
                mentionsEventContent
            }
        } shouldBe mentionsEventContent
    }

    @Test
    fun `mentions » replace old mention`() = runTest {
        roomService.returnGetTimelineEvent = flowOf(
            timelineEvent(EventId("bla"), mentions = Mentions(setOf(UserId("1"), UserId("2"))))
        )
        MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
            mentions(UserId("1"), UserId("3"), room = true)
            replace(EventId("bla"))
            contentBuilder = {
                mentions shouldBe Mentions(setOf(UserId("3")), true)
                newContentMentions shouldBe Mentions(setOf(UserId("1"), UserId("3")), true)
                mentionsEventContent
            }
        } shouldBe mentionsEventContent
    }

    @Test
    fun `mentions » add mention on reply`() = runTest {
        roomService.returnGetTimelineEvent = flowOf(
            timelineEvent(EventId("bla"), mentions = Mentions(setOf(UserId("1"))))
        )
        MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
            mentions(UserId("2"), room = true)
            reply(EventId("bla"), null)
            contentBuilder = {
                mentions shouldBe Mentions(setOf(UserId("sender", "server"), UserId("1"), UserId("2")), true)
                newContentMentions shouldBe Mentions()
                mentionsEventContent
            }
        } shouldBe mentionsEventContent
    }

    @Test
    fun `mentions » remove own mention`() = runTest {
        roomService.returnGetTimelineEvent = flowOf(
            timelineEvent(EventId("bla"), mentions = Mentions(setOf(UserId("1"))))
        )
        MessageBuilder(encryptedRoom, roomService, mediaService, UserId("sender", "server")).build {
            mentions(UserId("2"), room = true)
            reply(EventId("bla"), null)
            contentBuilder = {
                mentions shouldBe Mentions(setOf(UserId("1"), UserId("2")), true)
                newContentMentions shouldBe Mentions()
                mentionsEventContent
            }
        } shouldBe mentionsEventContent
    }

    @Test
    fun `text » create text`() = runTest {
        MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
            text(body = "body", format = "format", formattedBody = "formatted_body")
        } shouldBe RoomMessageEventContent.TextBased.Text("body", "format", "formatted_body", mentions = Mentions())
    }

    @Test
    fun `text » create fallback text on replace`() = runTest {
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

    @Test
    fun `text » create fallback text on reply`() = runTest {
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

    @Test
    fun `text » not nest fallback text on reply`() = runTest {
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

    @Test
    fun `text » not nest fallback text on rich reply`() = runTest {
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

    @Test
    fun `text » create fallback text on thread`() = runTest {
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

    @Test
    fun `text » create fallback text on reply to image`() = runTest {
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

    @Test
    fun `text » notice » create notice`() = runTest {
        MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
            notice(body = "body", format = "format", formattedBody = "formatted_body")
        } shouldBe RoomMessageEventContent.TextBased.Notice(
            "body",
            "format",
            "formatted_body",
            mentions = Mentions()
        )
    }

    @Test
    fun `emote » create emote`() = runTest {
        MessageBuilder(encryptedRoom, roomService, mediaService, ownUserId).build {
            emote(body = "body", format = "format", formattedBody = "formatted_body")
        } shouldBe RoomMessageEventContent.TextBased.Emote(
            "body",
            "format",
            "formatted_body",
            mentions = Mentions()
        )
    }


    @Test
    fun `image » create image and thumbnail`() = runTest {
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

    @Test
    fun `image » create encrypted image and thumbnail`() = runTest {
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


    @Test
    fun `file » create file and thumbnail`() = runTest {
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

    @Test
    fun `file » create encrypted file and thumbnail`() = runTest {
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

    @Test
    fun `video » create video and thumbnail`() = runTest {
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

    @Test
    fun `video » create encrypted video and thumbnail`() = runTest {
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

    @Test
    fun `audio » create audio`() = runTest {
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

    @Test
    fun `audio » create encrypted audio`() = runTest {
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


    private fun timelineEvent(eventId: EventId, relatesTo: RelatesTo? = null, mentions: Mentions? = null) =
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
}