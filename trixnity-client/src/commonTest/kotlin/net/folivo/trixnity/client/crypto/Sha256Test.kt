package net.folivo.trixnity.client.crypto

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.utils.io.core.*

class Sha256Test : ShouldSpec({
    timeout = 2_000

    should(::sha256.name) {
        sha256("foo".toByteArray()) shouldBe "LCa0a2j/xo/5m0U8HTBBNBNCLXBkg7+g+YpeiGJm564"
    }
})