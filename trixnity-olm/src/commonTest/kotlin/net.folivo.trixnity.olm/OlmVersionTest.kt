package net.folivo.trixnity.olm

import io.kotest.matchers.ints.shouldBeGreaterThan
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OlmVersionTest {
    @Test
    fun versionShouldBeSet() = runTest {
        getOlmVersion().major shouldBeGreaterThan 0
        getOlmVersion().minor shouldBeGreaterThan 0
        getOlmVersion().patch shouldBeGreaterThan 0
    }
}