package net.folivo.trixnity.utils

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okio.*
import kotlin.coroutines.CoroutineContext

suspend fun BufferedSink.write(content: ByteArrayFlow, coroutineContext: CoroutineContext = ioContext) =
    withContext(coroutineContext) {
        content.collect { write(it) }
    }

fun byteArrayFlow(coroutineContext: CoroutineContext = ioContext, sourceFactory: suspend () -> Source) =
    flow {
        val source = sourceFactory().buffer()
        source.use {
            while (source.exhausted().not()) {
                emit(source.readByteArray(BYTE_ARRAY_FLOW_CHUNK_SIZE.coerceAtMost(source.buffer.size)))
            }
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

fun FileSystem.readByteArrayFlow(
    path: Path,
    coroutineContext: CoroutineContext = ioContext,
): ByteArrayFlow? =
    if (exists(path)) byteArrayFlow(coroutineContext) { source(path) }
    else null