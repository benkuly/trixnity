package net.folivo.trixnity.client.rest.api

import io.ktor.http.*


open class MatrixServerException(
    val statusCode: HttpStatusCode,
    val errorResponse: ErrorResponse
) : Exception("status: $statusCode; errorCode: ${errorResponse.errorCode}; errorMessage: ${errorResponse.errorMessage}")