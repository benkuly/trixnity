package net.folivo.trixnity.appservice

import io.ktor.http.*
import net.folivo.trixnity.clientserverapi.client.MatrixServerException

class MatrixBadRequestException(message: String) : MatrixServerException(
    HttpStatusCode.BadRequest,
    net.folivo.trixnity.clientserverapi.model.ErrorResponse.Unknown(message)
)