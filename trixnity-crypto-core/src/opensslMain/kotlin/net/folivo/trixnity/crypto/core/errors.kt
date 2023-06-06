import kotlinx.cinterop.*
import net.folivo.trixnity.crypto.core.cinterop.openssl.*
import platform.posix.*

internal fun <T : Any> checkError(result: T?): T {
    if (result != null) return result
    fail(0)
}

internal fun checkError(result: size_t): size_t {
    if (result > 0u) return result
    fail(result.convert())
}

internal fun checkError(result: Int): Int {
    if (result > 0) return result
    fail(result)
}

@OptIn(UnsafeNumber::class)
private fun fail(result: Int): Nothing {
    val message = buildString {
        var code = ERR_get_error()
        if (code.toInt() != 0) do {
            val message = memScoped {
                val buffer = allocArray<ByteVar>(256)
                ERR_error_string(code, buffer)?.toKString()
            }
            append(message)
            code = ERR_get_error()
            if (code.toInt() != 0) append(", ")
        } while (code.toInt() != 0)
    }
    error("OPENSSL failure: $message (result: $result)")
}