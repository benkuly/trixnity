package net.folivo.trixnity.appservice.rest.api

import io.ktor.http.*


open class MatrixServerException(
    val statusCode: HttpStatusCode,
    val errorResponse: net.folivo.trixnity.appservice.rest.api.ErrorResponse
) : Exception("status: $statusCode; errorCode: ${errorResponse.errorCode}; errorMessage: ${errorResponse.errorMessage}")