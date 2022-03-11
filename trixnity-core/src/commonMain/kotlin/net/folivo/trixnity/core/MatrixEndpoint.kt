package net.folivo.trixnity.core

import io.ktor.http.*

interface MatrixEndpoint<REQUEST, RESPONSE> {
    val method: HttpMethod
    val requestContentType: ContentType
    val responseContentType: ContentType
}

abstract class MatrixJsonEndpoint<REQUEST, RESPONSE> : MatrixEndpoint<REQUEST, RESPONSE> {
    override val requestContentType = ContentType.Application.Json
    override val responseContentType = ContentType.Application.Json
}