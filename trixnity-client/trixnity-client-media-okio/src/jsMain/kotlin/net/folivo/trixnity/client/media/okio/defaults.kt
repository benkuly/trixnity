package net.folivo.trixnity.client.media.okio

import kotlinx.coroutines.Dispatchers
import okio.FileSystem
import okio.NodeJsFileSystem
import kotlin.coroutines.CoroutineContext

internal actual val defaultFileSystem: FileSystem = NodeJsFileSystem
internal actual val defaultContext: CoroutineContext = Dispatchers.Default