package net.folivo.trixnity.appservice.rest

import io.ktor.http.*
import net.folivo.trixnity.client.rest.api.ErrorResponse
import net.folivo.trixnity.client.rest.api.MatrixServerException

class MatrixNotFoundException(message: String) : MatrixServerException(
    HttpStatusCode.NotFound,
    ErrorResponse("NET.FOLIVO.MATRIX_NOT_FOUND", message)
)