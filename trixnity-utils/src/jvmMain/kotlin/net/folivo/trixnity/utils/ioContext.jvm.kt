package net.folivo.trixnity.utils

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

internal actual val ioContext: CoroutineContext = Dispatchers.IO