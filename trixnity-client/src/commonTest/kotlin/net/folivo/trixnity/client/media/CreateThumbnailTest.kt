package net.folivo.trixnity.client.media

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.ktor.http.ContentType.Image.PNG

class CreateThumbnailTest : ShouldSpec({
    context(::createThumbnail.name) {
        should("create a thumbnail") {
            val thumbnail = createThumbnail(miniPng, 30, 20)
            assertSoftly(thumbnail) {
                file.size shouldBeGreaterThan 0
                contentType shouldBe PNG
                width shouldBe 20
                height shouldBe 20
            }
        }
    }
})