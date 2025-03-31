package net.folivo.trixnity.core

import io.ktor.http.*

open class MatrixServerException(
    val statusCode: HttpStatusCode,
    val errorResponse: ErrorResponse,
    val retryAfter: Long? = null,
) : Exception("statusCode=$statusCode errorResponse=$errorResponse retryAfter=$retryAfter")