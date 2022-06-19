package net.folivo.trixnity.client.crypto

import com.soywiz.krypto.encoding.hex
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class Pbkdf2Test : ShouldSpec({
    timeout = 10_000

    should(::generatePbkdf2Sha512.name) {
        val password = "super secret. not"
        val salt = ByteArray(12) { (it + 1).toByte() }
        val iterationCount = 500000
        val keyLength = 256
        generatePbkdf2Sha512(password, salt, iterationCount, keyLength).hex shouldBe
                "7094d5836ad28e6609c9f41dea292bde0b5ad4d6ffad52b1375aeeda691786a6"
    }
})