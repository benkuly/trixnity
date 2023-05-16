package net.folivo.trixnity.crypto.core

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class HmacSha256Test : ShouldSpec({
    timeout = 30_000

    should("create mac") {
        hmacSha256(
            "this is a key".encodeToByteArray(),
            "this should be maced".encodeToByteArray()
        ) shouldBe "f5ab5a64c568f2393ed7a1c5bc84ae82d2ecb847b968bab2057fb99190e52d75".chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
})