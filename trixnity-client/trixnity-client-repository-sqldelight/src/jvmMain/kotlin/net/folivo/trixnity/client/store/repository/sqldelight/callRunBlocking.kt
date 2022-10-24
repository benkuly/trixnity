package net.folivo.trixnity.client.store.repository.sqldelight

import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext

actual fun <T> callRunBlocking(context: CoroutineContext?, block: suspend () -> T): T =
    context?.let { runBlocking(it) { block() } } ?: runBlocking { block() }