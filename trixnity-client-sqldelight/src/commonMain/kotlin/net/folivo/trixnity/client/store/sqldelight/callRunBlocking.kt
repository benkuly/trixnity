package net.folivo.trixnity.client.store.sqldelight

import kotlin.coroutines.CoroutineContext

expect fun <T> callRunBlocking(context: CoroutineContext? = null, block: suspend () -> T): T