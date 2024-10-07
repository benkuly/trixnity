package net.folivo.trixnity.utils

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.CoroutineContext

suspend fun ByteArrayFlow.writeTo(outputStream: OutputStream, coroutineContext: CoroutineContext = ioContext) =
    withContext(coroutineContext) {
        outputStream.use {
            collect { data ->
                it.write(data)
            }
        }
    }

suspend fun OutputStream.write(content: ByteArrayFlow, coroutineContext: CoroutineContext = ioContext) =
    content.writeTo(this, coroutineContext)

fun byteArrayFlowFromInputStream(
    coroutineContext: CoroutineContext = ioContext,
    inputStreamFactory: suspend () -> InputStream
): ByteArrayFlow = flow {
    val inputStream = inputStreamFactory()
    val buffer = ByteArray(BYTE_ARRAY_FLOW_CHUNK_SIZE.toInt())
    var bytesRead: Int

    inputStream.use {
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            emit(buffer.copyOf(bytesRead))
        }
    }
}.flowOn(coroutineContext)