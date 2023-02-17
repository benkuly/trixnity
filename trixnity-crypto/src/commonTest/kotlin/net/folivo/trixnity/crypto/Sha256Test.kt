package net.folivo.trixnity.crypto

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.utils.io.core.*
import kotlinx.coroutines.flow.collect
import net.folivo.trixnity.core.toByteArrayFlow

class Sha256Test : ShouldSpec({
    timeout = 2_000

    should("sha256") {
        val hashByteFlow = "foo".toByteArray().toByteArrayFlow().sha256()
        hashByteFlow.collect()
        hashByteFlow.hash.value shouldBe "LCa0a2j/xo/5m0U8HTBBNBNCLXBkg7+g+YpeiGJm564"
    }
})