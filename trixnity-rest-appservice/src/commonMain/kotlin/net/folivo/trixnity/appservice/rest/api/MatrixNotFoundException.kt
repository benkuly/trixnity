package net.folivo.trixnity.appservice.rest.api

import io.ktor.http.*

class MatrixNotFoundException(message: String) : MatrixServerException(
    HttpStatusCode.NotFound,
    ErrorResponse("NET.FOLIVO.MATRIX_NOT_FOUND", message)
)