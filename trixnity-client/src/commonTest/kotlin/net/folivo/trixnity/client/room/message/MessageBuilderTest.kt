package net.folivo.trixnity.client.room.message

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.ContentType.Audio.OGG
import io.ktor.http.ContentType.Image.PNG
import io.ktor.http.ContentType.Video.MP4
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import net.folivo.trixnity.client.media.IMediaService
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.*

class MessageBuilderTest : ShouldSpec({
    val mediaService = mockk<IMediaService>()

    afterTest { clearAllMocks() }

    context(MessageBuilder::build.name) {
        should("call builder and return content") {
            val mockContent = mockk<MessageEventContent>()
            MessageBuilder(true, mediaService).build {
                content = mockContent
            } shouldBe mockContent
        }
    }

    context(MessageBuilder::text.name) {
        should("create text") {
            MessageBuilder(true, mediaService).build {
                text("body", "format", "formatted_body")
            } shouldBe TextMessageEventContent("body", "format", "formatted_body")
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
            val thumbnailInfo = mockk<ThumbnailInfo>()
            coEvery { mediaService.prepareUploadThumbnail("fake_image".toByteArray(), PNG) }
                .returns(Pair("thumbnailCacheUrl", thumbnailInfo))
            coEvery { mediaService.prepareUploadMedia("fake_image".toByteArray(), PNG) }
                .returns("mediaCacheUrl")
            MessageBuilder(false, mediaService).build {
                image("body", "fake_image".toByteArray(), PNG, 1024, 1024)
            } shouldBe ImageMessageEventContent(
                "body", ImageInfo(
                    1024, 1024, "image/png", 10, "thumbnailCacheUrl", null,
                    thumbnailInfo,
                ), "mediaCacheUrl"
            )
        }
        should("create encrypted image and thumbnail") {
            val thumbnailInfo = mockk<ThumbnailInfo>()
            val encryptedFile = mockk<EncryptedFile>()
            val encryptedThumbnail = mockk<EncryptedFile>()
            coEvery { mediaService.prepareUploadEncryptedMedia("fake_image".toByteArray()) }
                .returns(encryptedFile)
            coEvery { mediaService.prepareUploadEncryptedThumbnail("fake_image".toByteArray(), PNG) }
                .returns(encryptedThumbnail to thumbnailInfo)
            MessageBuilder(true, mediaService).build {
                image("body", "fake_image".toByteArray(), PNG, 1024, 1024)
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
            val thumbnailInfo = mockk<ThumbnailInfo>()
            coEvery { mediaService.prepareUploadThumbnail("fake_file".toByteArray(), PNG) }
                .returns(Pair("thumbnailCacheUrl", thumbnailInfo))
            coEvery { mediaService.prepareUploadMedia("fake_file".toByteArray(), PNG) }
                .returns("mediaCacheUrl")
            MessageBuilder(false, mediaService).build {
                file("body", "fake_file".toByteArray(), PNG, "filename")
            } shouldBe FileMessageEventContent(
                "body", "filename", FileInfo(
                    "image/png", 9, "thumbnailCacheUrl", null, thumbnailInfo,
                ), "mediaCacheUrl"
            )
        }
        should("create encrypted file and thumbnail") {
            val thumbnailInfo = mockk<ThumbnailInfo>()
            val encryptedFile = mockk<EncryptedFile>()
            val encryptedThumbnail = mockk<EncryptedFile>()
            coEvery { mediaService.prepareUploadEncryptedMedia("fake_file".toByteArray()) }
                .returns(encryptedFile)
            coEvery { mediaService.prepareUploadEncryptedThumbnail("fake_file".toByteArray(), PNG) }
                .returns(encryptedThumbnail to thumbnailInfo)
            MessageBuilder(true, mediaService).build {
                file("body", "fake_file".toByteArray(), PNG, "filename")
            } shouldBe FileMessageEventContent(
                "body", "filename", FileInfo(
                    "image/png", 9, null, encryptedThumbnail, thumbnailInfo,
                ), null, encryptedFile
            )
        }
    }
    context(MessageBuilder::video.name) {
        should("create video and thumbnail") {
            val thumbnailInfo = mockk<ThumbnailInfo>()
            coEvery { mediaService.prepareUploadThumbnail("fake_video".toByteArray(), MP4) }
                .returns(Pair("thumbnailCacheUrl", thumbnailInfo))
            coEvery { mediaService.prepareUploadMedia("fake_video".toByteArray(), MP4) }
                .returns("mediaCacheUrl")
            MessageBuilder(false, mediaService).build {
                video("body", "fake_video".toByteArray(), MP4, 1024, 1024, 1024)
            } shouldBe VideoMessageEventContent(
                "body", VideoInfo(
                    1024, 1024, 1024, "video/mp4", 10, "thumbnailCacheUrl", null,
                    thumbnailInfo,
                ), "mediaCacheUrl"
            )
        }
        should("create encrypted video and thumbnail") {
            val thumbnailInfo = mockk<ThumbnailInfo>()
            val encryptedFile = mockk<EncryptedFile>()
            val encryptedThumbnail = mockk<EncryptedFile>()
            coEvery { mediaService.prepareUploadEncryptedMedia("fake_video".toByteArray()) }
                .returns(encryptedFile)
            coEvery { mediaService.prepareUploadEncryptedThumbnail("fake_video".toByteArray(), MP4) }
                .returns(encryptedThumbnail to thumbnailInfo)
            MessageBuilder(true, mediaService).build {
                video("body", "fake_video".toByteArray(), MP4, 1024, 1024, 1024)
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
            coEvery { mediaService.prepareUploadMedia("fake_audio".toByteArray(), OGG) }
                .returns("mediaCacheUrl")
            MessageBuilder(false, mediaService).build {
                audio("body", "fake_audio".toByteArray(), OGG, 1024)
            } shouldBe AudioMessageEventContent(
                "body", AudioInfo(1024, "audio/ogg", 10), "mediaCacheUrl"
            )
        }
        should("create encrypted audio") {
            val encryptedFile = mockk<EncryptedFile>()
            coEvery { mediaService.prepareUploadEncryptedMedia("fake_audio".toByteArray()) }
                .returns(encryptedFile)
            MessageBuilder(true, mediaService).build {
                audio("body", "fake_audio".toByteArray(), OGG, 1024)
            } shouldBe AudioMessageEventContent(
                "body", AudioInfo(1024, "audio/ogg", 10), null, encryptedFile
            )
        }
    }
})