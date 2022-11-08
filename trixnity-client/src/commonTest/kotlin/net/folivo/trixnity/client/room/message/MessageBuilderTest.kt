package net.folivo.trixnity.client.room.message

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.ContentType.Audio.OGG
import io.ktor.http.ContentType.Image.PNG
import io.ktor.http.ContentType.Video.MP4
import io.ktor.utils.io.core.*
import net.folivo.trixnity.client.mocks.MediaServiceMock
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.events.RelatesTo
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.*
import net.folivo.trixnity.core.toByteFlow

class MessageBuilderTest : ShouldSpec({
    timeout = 60_000
    val mediaService = MediaServiceMock()

    context(MessageBuilder::build.name) {
        should("call builder and return content") {
            val eventContent = TextMessageEventContent("")
            MessageBuilder(true, mediaService).build {
                relatesTo = RelatesTo.Replace(EventId("other"), null)
                contentBuilder = {
                    it shouldBe relatesTo
                    eventContent
                }
            } shouldBe eventContent
        }
    }

    context(MessageBuilder::replace.name) {
        val eventContent = TextMessageEventContent("")
        should("create create replace relation") {
            MessageBuilder(true, mediaService).build {
                replace(EventId("bla"))
                contentBuilder = {
                    it shouldBe RelatesTo.Replace(EventId("bla"), null)
                    eventContent
                }
            } shouldBe eventContent
        }
    }

    context(MessageBuilder::reply.name) {
        val eventContent = TextMessageEventContent("")
        should("create create reply relation") {
            MessageBuilder(true, mediaService).build {
                reply(EventId("bla"))
                contentBuilder = {
                    it shouldBe RelatesTo.Reply(RelatesTo.ReplyTo(EventId("bla")))
                    eventContent
                }
            } shouldBe eventContent
        }
    }

    context(MessageBuilder::text.name) {
        should("create text") {
            MessageBuilder(true, mediaService).build {
                text("body", "format", "formatted_body")
            } shouldBe TextMessageEventContent("body", "format", "formatted_body")
        }
        should("create fallback text on replace") {
            MessageBuilder(true, mediaService).build {
                replace(EventId("bla"))
                text("body", "format", "formatted_body")
            } shouldBe TextMessageEventContent(
                "*body",
                "format",
                "*formatted_body",
                RelatesTo.Replace(EventId("bla"), TextMessageEventContent("body", "format", "formatted_body"))
            )
        }
    }
    context(MessageBuilder::notice.name) {
        should("create notice") {
            MessageBuilder(true, mediaService).build {
                notice("body", "format", "formatted_body")
            } shouldBe NoticeMessageEventContent("body", "format", "formatted_body")
        }
    }
    context(MessageBuilder::emote.name) {
        should("create emote") {
            MessageBuilder(true, mediaService).build {
                emote("body", "format", "formatted_body")
            } shouldBe EmoteMessageEventContent("body", "format", "formatted_body")
        }
    }
    context(MessageBuilder::image.name) {
        should("create image and thumbnail") {
            val thumbnailInfo = ThumbnailInfo()
            mediaService.returnPrepareUploadThumbnail = Pair("thumbnailCacheUrl", thumbnailInfo)
            mediaService.returnPrepareUploadMedia = "mediaCacheUrl"
            MessageBuilder(false, mediaService).build {
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
            MessageBuilder(true, mediaService).build {
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
            MessageBuilder(false, mediaService).build {
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
            MessageBuilder(true, mediaService).build {
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
            MessageBuilder(false, mediaService).build {
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
            MessageBuilder(true, mediaService).build {
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
            MessageBuilder(false, mediaService).build {
                audio("body", "fake_audio".toByteArray().toByteFlow(), OGG, 10, 1024)
            } shouldBe AudioMessageEventContent(
                "body", AudioInfo(1024, "audio/ogg", 10), "mediaCacheUrl"
            )
        }
        should("create encrypted audio") {
            val encryptedFile = EncryptedFile("", EncryptedFile.JWK(""), "", mapOf())
            mediaService.returnPrepareUploadEncryptedMedia = encryptedFile
            MessageBuilder(true, mediaService).build {
                audio("body", "fake_audio".toByteArray().toByteFlow(), OGG, 10, 1024)
            } shouldBe AudioMessageEventContent(
                "body", AudioInfo(1024, "audio/ogg", 10), null, encryptedFile
            )
        }
    }
})