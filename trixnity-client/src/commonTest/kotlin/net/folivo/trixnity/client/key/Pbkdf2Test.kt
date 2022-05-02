package net.folivo.trixnity.client.key

import com.soywiz.krypto.encoding.hex
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class Pbkdf2Test : ShouldSpec({
    timeout = 30_000

    context(::generatePbkdf2Sha512.name) {
        should("create pbkdf2") {
            val password = "password"
            val salt = ByteArray(12) { (it + 1).toByte() }
            val iterationCount = 4096
            val keyLength = 256
            generatePbkdf2Sha512(password, salt, iterationCount, keyLength).hex shouldBe
                    "77fec6ca97e3c15022b1c51a37cf05739e6a3c90b8c85518427ac5be6f8fa06d"
        }
    }
})