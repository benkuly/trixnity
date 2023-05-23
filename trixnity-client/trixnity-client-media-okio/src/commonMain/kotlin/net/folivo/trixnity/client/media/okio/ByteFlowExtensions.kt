package net.folivo.trixnity.client.media.okio

import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import net.folivo.trixnity.utils.BYTE_ARRAY_FLOW_CHUNK_SIZE
import net.folivo.trixnity.utils.ByteArrayFlow
import okio.*
import kotlin.coroutines.CoroutineContext

suspend fun ByteArrayFlow.writeToSink(sink: BufferedSink, coroutineContext: CoroutineContext = defaultContext): Unit =
    withContext(coroutineContext) {
        collect { sink.write(it) }
    }

suspend fun BufferedSource.readByteFlow(coroutineContext: CoroutineContext = defaultContext): ByteArrayFlow =
    flow {
        while (exhausted().not()) {
            emit(readByteArray(BYTE_ARRAY_FLOW_CHUNK_SIZE.coerceAtMost(buffer.size)))
        }
    }.flowOn(coroutineContext)

suspend fun FileSystem.writeByteFlow(
    path: Path,
    content: ByteArrayFlow,
    coroutineContext: CoroutineContext = defaultContext,
): Unit =
    withContext(coroutineContext) {
        sink(path).buffer().use { sink ->
            content.writeToSink(sink, coroutineContext)
        }
    }

suspend fun FileSystem.readByteFlow(
    path: Path,
    coroutineContext: CoroutineContext = defaultContext,
): ByteArrayFlow? =
    if (exists(path))
        flow {
            source(path).buffer().use { source ->
                emitAll(source.readByteFlow(coroutineContext))
            }
        }.flowOn(coroutineContext)
    else null