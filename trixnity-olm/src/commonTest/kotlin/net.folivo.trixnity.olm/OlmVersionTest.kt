package net.folivo.trixnity.olm

import io.kotest.matchers.ints.shouldBeGreaterThan
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import kotlin.test.Test

class OlmVersionTest : TrixnityBaseTest() {
    @Test
    fun versionShouldBeSet() = runTest {
        getOlmVersion().major shouldBeGreaterThan 0
        getOlmVersion().minor shouldBeGreaterThan 0
        getOlmVersion().patch shouldBeGreaterThan 0
    }
}