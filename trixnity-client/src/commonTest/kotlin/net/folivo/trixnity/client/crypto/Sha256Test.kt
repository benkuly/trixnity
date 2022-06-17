package net.folivo.trixnity.client.crypto

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.utils.io.core.*

class Sha256Test : ShouldSpec({
    timeout = 2_000

    should(::sha256.name) {
        sha256("foo".toByteArray()) shouldBe "2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae"
    }
})