package net.folivo.trixnity.client.notification

import io.github.oshai.kotlinlogging.KotlinLogging

private val log = KotlinLogging.logger("net.folivo.trixnity.client.notification.hasSizeMatch")
private val roomSizePattern = Regex("\\s*(==|<|>|<=|>=)\\s*([0-9]+)")
internal fun hasSizeMatch(value: String, size: Long): Boolean {
    value.toLongOrNull()?.let { count ->
        return size == count
    }
    val result = roomSizePattern.find(value)
    val bound = result?.groupValues?.getOrNull(2)?.toLongOrNull() ?: return false
    val operator = result.groupValues.getOrNull(1) ?: return false
    log.trace { "room size ($size) $operator bound ($bound)" }
    return when (operator) {
        "==" -> size == bound
        "<" -> size < bound
        ">" -> size > bound
        "<=" -> size <= bound
        ">=" -> size >= bound
        else -> false
    }
}