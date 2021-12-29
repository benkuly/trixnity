package net.folivo.trixnity.appservice.rest

import io.ktor.http.*
import net.folivo.trixnity.client.api.model.ErrorResponse
import net.folivo.trixnity.client.api.MatrixServerException

class MatrixBadRequestException(message: String) : MatrixServerException(
    HttpStatusCode.BadRequest,
    ErrorResponse.Unknown(message)
)