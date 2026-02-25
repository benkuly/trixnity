package de.connect2x.trixnity.client.media.okio

import kotlinx.coroutines.Dispatchers
import okio.FileSystem
import okio.NodeJsFileSystem
import kotlin.coroutines.CoroutineContext

internal actual val defaultFileSystem: FileSystem = NodeJsFileSystem
internal actual val ioContext: CoroutineContext = Dispatchers.Default