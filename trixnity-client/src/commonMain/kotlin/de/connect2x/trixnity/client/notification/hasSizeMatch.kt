package de.connect2x.trixnity.client.notification

import de.connect2x.lognity.api.logger.Logger

private val log = Logger("de.connect2x.trixnity.client.notification.hasSizeMatch")
private val roomSizePattern = Regex("\\s*(==|<|>|<=|>=)\\s*([0-9]+)")
internal fun hasSizeMatch(value: String, size: Long): Boolean {
    value.toLongOrNull()?.let { count ->
        return size == count
    }
    val result = roomSizePattern.find(value)
    val bound = result?.groupValues?.getOrNull(2)?.toLongOrNull() ?: return false
    if (bound < 0) return false
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