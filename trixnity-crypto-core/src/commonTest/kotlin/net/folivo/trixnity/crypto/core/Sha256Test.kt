package net.folivo.trixnity.crypto.core

import io.kotest.matchers.shouldBe
import io.ktor.utils.io.core.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.utils.toByteArrayFlow
import kotlin.test.Test

class Sha256Test : TrixnityBaseTest() {

    @Test
    fun sha256() = runTest {
        val hashByteFlow = "foo".toByteArray().toByteArrayFlow().sha256()
        hashByteFlow.collect()
        hashByteFlow.hash.value shouldBe "LCa0a2j/xo/5m0U8HTBBNBNCLXBkg7+g+YpeiGJm564"
    }
}