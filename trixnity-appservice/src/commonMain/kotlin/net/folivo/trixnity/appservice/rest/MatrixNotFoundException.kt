package net.folivo.trixnity.appservice.rest

import io.ktor.http.*
import net.folivo.trixnity.client.api.model.ErrorResponse
import net.folivo.trixnity.client.api.MatrixServerException

class MatrixNotFoundException(message: String) : MatrixServerException(
    HttpStatusCode.NotFound,
    ErrorResponse.NotFound(message)
)