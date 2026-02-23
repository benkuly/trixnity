package de.connect2x.trixnity.idb.utils

import js.errors.toThrowable
import web.errors.AbortError
import web.errors.DOMException
import kotlin.coroutines.cancellation.CancellationException


class IDBException private constructor(
    val operation: Operation,
    override val cause: Throwable?,
) : IllegalStateException(formatMessage(operation)) {
    companion object {
        fun fromDom(operation: Operation, error: DOMException?) = when (error) {
            DOMException.AbortError -> CancellationException(
                formatMessage(operation), error.toThrowable()
            )

            else -> IDBException(operation, error?.toThrowable())
        }

        private fun formatMessage(operation: Operation) =
            "IndexedDB Error while running: ${operation.name.lowercase()}"
    }

    enum class Operation {
        GET, PUT, DELETE, CLEAR, TRANSACTION
    }
}

