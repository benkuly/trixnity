package net.folivo.trixnity.client.store.repository.sqldelight

import kotlin.coroutines.CoroutineContext

expect fun <T> callRunBlocking(context: CoroutineContext? = null, block: suspend () -> T): T