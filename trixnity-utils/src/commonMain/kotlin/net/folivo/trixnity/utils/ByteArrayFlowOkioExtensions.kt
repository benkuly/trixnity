package net.folivo.trixnity.utils

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okio.*
import kotlin.coroutines.CoroutineContext

suspend fun ByteArrayFlow.writeTo(sink: BufferedSink, coroutineContext: CoroutineContext = ioContext) =
    withContext(coroutineContext) {
        sink.use {
            collect { data -> it.write(data) }
        }
    }

suspend fun BufferedSink.write(content: ByteArrayFlow, coroutineContext: CoroutineContext = ioContext) =
    content.writeTo(this, coroutineContext)

fun byteArrayFlowFromSource(coroutineContext: CoroutineContext = ioContext, sourceFactory: suspend () -> Source) =
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
        content.writeTo(sink(path).buffer(), coroutineContext)
    }

fun FileSystem.readByteArrayFlow(
    path: Path,
    coroutineContext: CoroutineContext = ioContext,
): ByteArrayFlow? =
    if (exists(path)) byteArrayFlowFromSource(coroutineContext) { source(path) }
    else null