package net.folivo.trixnity.utils

import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okio.*
import kotlin.coroutines.CoroutineContext

suspend fun BufferedSink.write(content: ByteArrayFlow, coroutineContext: CoroutineContext = ioContext) =
    withContext(coroutineContext) {
        content.collect { write(it) }
    }

suspend fun BufferedSource.readByteArrayFlow(coroutineContext: CoroutineContext = ioContext): ByteArrayFlow =
    flow {
        while (exhausted().not()) {
            emit(readByteArray(BYTE_ARRAY_FLOW_CHUNK_SIZE.coerceAtMost(buffer.size)))
        }
    }.flowOn(coroutineContext)

suspend fun FileSystem.write(
    path: Path,
    content: ByteArrayFlow,
    coroutineContext: CoroutineContext = ioContext,
): Unit =
    withContext(coroutineContext) {
        sink(path).buffer().use { it.write(content) }
    }

suspend fun FileSystem.readByteArrayFlow(
    path: Path,
    coroutineContext: CoroutineContext = ioContext,
): ByteArrayFlow? =
    if (exists(path))
        flow {
            source(path).buffer().use { source -> // use only works wrapped into the flow
                emitAll(source.readByteArrayFlow(coroutineContext))
            }
        }.flowOn(coroutineContext)
    else null