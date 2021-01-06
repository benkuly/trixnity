package net.folivo.trixnity.client.rest

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise

actual fun runBlockingTest(block: suspend () -> Unit): dynamic = GlobalScope.promise { block() }