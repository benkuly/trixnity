package de.connect2x.trixnity.client.media.okio

import kotlinx.coroutines.Dispatchers
import okio.FileSystem
import kotlin.coroutines.CoroutineContext

internal actual val defaultFileSystem: FileSystem = FileSystem.SYSTEM
internal actual val ioContext: CoroutineContext = Dispatchers.IO