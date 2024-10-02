package net.folivo.trixnity.utils

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.InputStream
import kotlin.coroutines.CoroutineContext

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