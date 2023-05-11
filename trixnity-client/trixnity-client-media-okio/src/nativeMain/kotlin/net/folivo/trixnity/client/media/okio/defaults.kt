package net.folivo.trixnity.client.media.okio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import okio.FileSystem
import kotlin.coroutines.CoroutineContext

internal actual val defaultFileSystem: FileSystem = FileSystem.SYSTEM
internal actual val defaultContext: CoroutineContext = Dispatchers.IO