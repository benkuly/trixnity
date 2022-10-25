package net.folivo.trixnity.client.media.okio

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import net.folivo.trixnity.core.ByteFlow
import okio.*
import kotlin.coroutines.CoroutineContext

suspend fun ByteFlow.writeToSink(sink: BufferedSink): Unit =
    collect { sink.writeByte(it.toInt()) }

suspend fun BufferedSource.readByteFlow(): ByteFlow =
    flow {
        while (exhausted().not()) {
            emit(readByte())
        }
    }

suspend fun FileSystem.writeByteFlow(
    path: Path,
    content: ByteFlow,
    coroutineContext: CoroutineContext = defaultContext,
): Unit =
    withContext(coroutineContext) {
        sink(path).buffer().use { sink ->
            content.writeToSink(sink)
        }
    }

suspend fun FileSystem.readByteFlow(
    path: Path,
    coroutineContext: CoroutineContext = defaultContext,
): ByteFlow? =
    withContext(coroutineContext) {
        if (exists(path))
            flow {
                source(path).buffer().use { source ->
                    source.readByteFlow()
                }
            }
        else null
    }