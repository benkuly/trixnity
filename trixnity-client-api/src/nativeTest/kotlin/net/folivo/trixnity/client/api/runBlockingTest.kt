package net.folivo.trixnity.client.api

import kotlinx.coroutines.runBlocking

actual fun runBlockingTest(block: suspend () -> Unit) = runBlocking { block() }