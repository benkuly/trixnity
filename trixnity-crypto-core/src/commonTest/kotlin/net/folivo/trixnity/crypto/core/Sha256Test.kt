package net.folivo.trixnity.crypto.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.utils.io.core.*
import kotlinx.coroutines.flow.collect
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.utils.encodeUnpaddedBase64
import net.folivo.trixnity.utils.toByteArrayFlow
import kotlin.test.Test

class Sha256Test : TrixnityBaseTest() {

    companion object {
        private const val FOO = "foo"
        private const val BAR = "bar"

        private const val DIGEST_FOO = "2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae"
        private const val DIGEST_BAR = "fcde2b2edba56bf408601fb721fe9b5c338d10ee429ea04fae5511b68fbf8fb9"
        private const val DIGEST_EMPTY = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    }

    @Test
    fun `create single digest`() {
        val sha256 = Sha256()

        sha256.update(FOO.toByteArray())

        sha256.digest().toHexString() shouldBe DIGEST_FOO
    }

    @Test
    fun `create empty digest`() {
        val sha256 = Sha256()

        sha256.digest().toHexString() shouldBe DIGEST_EMPTY
    }

    @Test
    fun `allow empty update`() {
        val sha256 = Sha256()

        sha256.update(ByteArray(0))
        sha256.digest().toHexString() shouldBe DIGEST_EMPTY
    }

    @Test
    fun `reset after digest`() {
        val sha256 = Sha256()

        sha256.update(FOO.toByteArray())
        sha256.digest().toHexString() shouldBe DIGEST_FOO
        sha256.digest().toHexString() shouldBe DIGEST_EMPTY
    }

    @Test
    fun `keep state between updates`() {
        val sha256 = Sha256()

        for (byte in FOO.toByteArray()) {
            sha256.update(byteArrayOf(byte))
        }
    }

    @Test
    fun `allow multiple digests`() {
        val sha256 = Sha256()

        sha256.update(FOO.toByteArray())
        sha256.digest().toHexString() shouldBe DIGEST_FOO

        sha256.update(BAR.toByteArray())
        sha256.digest().toHexString() shouldBe DIGEST_BAR
    }

    @Test
    fun `allow double close`() {
        val sha256 = Sha256()
        sha256.close()
        sha256.close()
    }

    @Test
    fun `disallow update after close`() {
        val sha256 = Sha256()
        sha256.close()

        shouldThrow<IllegalStateException> {
            sha256.update(FOO.toByteArray())
        }.message shouldBe "SHA-256 is closed"
    }

    @Test
    fun `disallow digest after close`() {
        val sha256 = Sha256()
        sha256.close()

        shouldThrow<IllegalStateException> {
            sha256.digest()
        }.message shouldBe "SHA-256 is closed"
    }

    @Test
    fun `hash is null before collection`() = runTest {
        val sha256ByteFlow = FOO.toByteArray().toByteArrayFlow().sha256()
        sha256ByteFlow.hash.value shouldBe null
    }

    @Test
    fun `hash is set after collection`() = runTest {
        val sha256ByteFlow = FOO.toByteArray().toByteArrayFlow().sha256()
        sha256ByteFlow.collect()
        sha256ByteFlow.hash.value shouldBe DIGEST_FOO.hexToByteArray().encodeUnpaddedBase64()
    }
}