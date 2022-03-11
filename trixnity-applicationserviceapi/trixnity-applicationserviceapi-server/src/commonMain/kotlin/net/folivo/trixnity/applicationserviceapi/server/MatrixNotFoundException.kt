package net.folivo.trixnity.applicationserviceapi.server

import io.ktor.http.*
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixServerException

class MatrixNotFoundException(message: String) : MatrixServerException(
    HttpStatusCode.NotFound,
    ErrorResponse.NotFound(message)
)