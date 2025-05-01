package net.folivo.trixnity.crypto.core

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import kotlin.test.Test

class HmacSha256Test : TrixnityBaseTest() {

    @Test
    fun `create mac`() = runTest {
        hmacSha256(
            "this is a key".encodeToByteArray(),
            "this should be maced".encodeToByteArray()
        ) shouldBe "f5ab5a64c568f2393ed7a1c5bc84ae82d2ecb847b968bab2057fb99190e52d75".chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}