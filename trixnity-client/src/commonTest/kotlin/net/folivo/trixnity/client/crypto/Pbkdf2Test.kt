package net.folivo.trixnity.client.crypto

import com.soywiz.krypto.encoding.hex
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class Pbkdf2Test : ShouldSpec({
    timeout = 10_000

    should(::generatePbkdf2Sha512.name) {
        val password = "super secret. not"
        val salt = ByteArray(12) { (it + 1).toByte() }
        val iterationCount = 10_000
        val keyLength = 256
        generatePbkdf2Sha512(password, salt, iterationCount, keyLength).hex shouldBe
                "456e181b5be566aa7afc51e208024a4c8401c5dbbc7d6b4e9ad1c7a21329fb75"
    }
})