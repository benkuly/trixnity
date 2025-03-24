package net.folivo.trixnity.clientserverapi.client

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest

fun String.trimToFlatJson() = this.trimIndent().lines().joinToString("") { it.replace(": ", ":").trim() }

fun runTestWithCoroutineScope(
    testBody: suspend kotlinx.coroutines.test.TestScope.(CoroutineScope) -> Unit
): TestResult = runTest {
    val coroutineScope = CoroutineScope(coroutineContext + Job())
    try {
        testBody(coroutineScope)
    } finally {
        coroutineScope.cancel()
    }
}