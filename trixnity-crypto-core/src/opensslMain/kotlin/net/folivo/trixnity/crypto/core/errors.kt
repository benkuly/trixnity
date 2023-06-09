import kotlinx.cinterop.*
import org.openssl.ERR_error_string
import org.openssl.ERR_get_error
import platform.posix.size_t

internal fun <T : Any> checkError(result: T?): T {
    if (result != null) return result
    error(errorMessage(0))
}

internal fun checkError(result: size_t): size_t {
    if (result > 0u) return result
    error(errorMessage(result.convert()))
}

internal fun checkError(result: Int): Int {
    if (result > 0) return result
    error(errorMessage(result))
}

@OptIn(UnsafeNumber::class)
private fun errorMessage(result: Int): String {
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
    return "OPENSSL failure: $message (result: $result)"
}