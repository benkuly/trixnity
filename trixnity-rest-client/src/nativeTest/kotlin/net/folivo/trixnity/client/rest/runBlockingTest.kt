package net.folivo.trixnity.client.rest

import kotlinx.coroutines.runBlocking

actual fun runBlockingTest(block: suspend () -> Unit) = runBlocking { block() }