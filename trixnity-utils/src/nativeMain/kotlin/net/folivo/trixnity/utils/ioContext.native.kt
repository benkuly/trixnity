package net.folivo.trixnity.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlin.coroutines.CoroutineContext

internal actual val ioContext: CoroutineContext = Dispatchers.IO