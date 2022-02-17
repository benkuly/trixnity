package net.folivo.trixnity.clientserverapi.client

import io.ktor.http.*
import net.folivo.trixnity.clientserverapi.model.ErrorResponse

open class MatrixServerException(
    val statusCode: HttpStatusCode,
    val errorResponse: ErrorResponse
) : Exception("statusCode: $statusCode; errorResponse: $errorResponse")