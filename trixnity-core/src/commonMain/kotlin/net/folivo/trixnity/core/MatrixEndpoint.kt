package net.folivo.trixnity.core

import io.ktor.http.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialInfo
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

interface MatrixEndpoint<REQUEST, RESPONSE> {
    val requestContentType: ContentType?
        get() = ContentType.Application.Json
    val responseContentType: ContentType?
        get() = ContentType.Application.Json

    fun requestSerializerBuilder(
        mappings: EventContentSerializerMappings,
        json: Json,
        value: REQUEST?
    ): KSerializer<REQUEST>? = null

    fun responseSerializerBuilder(
        mappings: EventContentSerializerMappings,
        json: Json,
        value: RESPONSE?
    ): KSerializer<RESPONSE>? = null
}

@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPEALIAS)
annotation class HttpMethod(val type: HttpMethodType)

enum class HttpMethodType {
    GET,
    POST,
    PUT,
    PATCH,
    DELETE,
    HEAD,
    OPTIONS,
}

@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPEALIAS)
annotation class WithoutAuth(val optional: Boolean = false)

@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPEALIAS)
annotation class ForceJson
