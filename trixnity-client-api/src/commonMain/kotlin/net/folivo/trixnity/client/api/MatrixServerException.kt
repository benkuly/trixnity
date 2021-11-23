package net.folivo.trixnity.client.api

import io.ktor.http.*

open class MatrixServerException(
    val statusCode: HttpStatusCode,
    val errorResponse: ErrorResponse
) : Exception("statusCode: $statusCode; errorResponse: $errorResponse")