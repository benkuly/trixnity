package net.folivo.trixnity.core

import io.ktor.http.*

open class MatrixServerException(
    val statusCode: HttpStatusCode,
    val errorResponse: ErrorResponse
) : Exception("statusCode: $statusCode; errorResponse: $errorResponse")