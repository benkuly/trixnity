package net.folivo.trixnity.utils

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.InputStream
import kotlin.coroutines.CoroutineContext

fun byteArrayFlowFromInputStream(inputStream: InputStream, coroutineContext: CoroutineContext = ioContext) : ByteArrayFlow = flow {
    val buffer = ByteArray(BYTE_ARRAY_FLOW_CHUNK_SIZE.toInt())
    var bytesRead: Int

    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
        emit(buffer.copyOf(bytesRead))
    }
}
    .flowOn(coroutineContext)