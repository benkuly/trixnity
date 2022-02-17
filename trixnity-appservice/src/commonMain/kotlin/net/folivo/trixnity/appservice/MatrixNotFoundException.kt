package net.folivo.trixnity.appservice

import io.ktor.http.*
import net.folivo.trixnity.clientserverapi.client.MatrixServerException

class MatrixNotFoundException(message: String) : MatrixServerException(
    HttpStatusCode.NotFound,
    net.folivo.trixnity.clientserverapi.model.ErrorResponse.NotFound(message)
)