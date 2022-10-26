package net.folivo.trixnity.client.media.okio

import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import net.folivo.trixnity.core.ByteFlow
import okio.*
import kotlin.coroutines.CoroutineContext

suspend fun ByteFlow.writeToSink(sink: BufferedSink, coroutineContext: CoroutineContext = defaultContext): Unit =
    withContext(coroutineContext) {
        collect { sink.writeByte(it.toInt()) }
    }

suspend fun BufferedSource.readByteFlow(coroutineContext: CoroutineContext = defaultContext): ByteFlow =
    flow {
        while (exhausted().not()) {
            emit(readByte())
        }
    }.flowOn(coroutineContext)

suspend fun FileSystem.writeByteFlow(
    path: Path,
    content: ByteFlow,
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
): ByteFlow? =
    if (exists(path))
        flow {
            source(path).buffer().use { source ->
                emitAll(source.readByteFlow(coroutineContext))
            }
        }.flowOn(coroutineContext)
    else null