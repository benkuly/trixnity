package net.folivo.trixnity.examples

import kotlinx.coroutines.runBlocking

actual fun runBlockingTest(block: suspend () -> Unit) = runBlocking { block() }