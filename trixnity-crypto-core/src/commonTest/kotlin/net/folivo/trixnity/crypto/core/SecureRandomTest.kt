package net.folivo.trixnity.crypto.core

import io.kotest.matchers.shouldNotBe
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecureRandomTest : TrixnityBaseTest() {
    @Test
    fun shouldCreateDifferentInts() {
        SecureRandom.nextInt() shouldNotBe SecureRandom.nextInt()
    }

    @Test
    fun shouldNotFillEmptyByteArray() {
        assertTrue(SecureRandom.nextBytes(0).isEmpty())
        assertTrue(SecureRandom.nextBytes(ByteArray(0)).isEmpty())
    }

    @Test
    fun shouldFillBytes() {
        val bytes = ByteArray(10) { it.toByte() }
        bytes.copyOf().also { copy ->
            assertContentEquals(bytes, copy)
            val inPlace = SecureRandom.nextBytes(copy)
            assertContentEquals(inPlace, copy)
            assertFalse(bytes.contentEquals(inPlace))
            assertFalse(bytes.contentEquals(copy))
        }
        bytes.copyOf().also { copy ->
            val inPlace = SecureRandom.nextBytes(copy, 5, 5)
            assertContentEquals(inPlace, copy)
            assertContentEquals(bytes, inPlace)
            assertContentEquals(bytes, copy)
        }
        bytes.copyOf().also { copy ->
            val inPlace = SecureRandom.nextBytes(copy, 0, 5)
            assertContentEquals(inPlace, copy)
            assertFalse(bytes.contentEquals(inPlace))
            assertFalse(bytes.contentEquals(copy))
        }
    }
}