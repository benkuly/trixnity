package net.folivo.trixnity.libolm

import io.kotest.matchers.ints.shouldBeGreaterThan
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class OlmVersionTest {
    @Test
    fun versionShouldBeSet() = runTest {
        getOlmVersion().major shouldBeGreaterThan 0
        getOlmVersion().minor shouldBeGreaterThan 0
        getOlmVersion().patch shouldBeGreaterThan 0
    }
}