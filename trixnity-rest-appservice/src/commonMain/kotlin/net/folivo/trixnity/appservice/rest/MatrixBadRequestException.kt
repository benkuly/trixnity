package net.folivo.trixnity.appservice.rest

import io.ktor.http.*
import net.folivo.trixnity.client.rest.api.ErrorResponse
import net.folivo.trixnity.client.rest.api.MatrixServerException

class MatrixBadRequestException(message: String) : MatrixServerException(
    HttpStatusCode.BadRequest,
    ErrorResponse("NET.FOLIVO.MATRIX_BAD_REQUEST", message)
)