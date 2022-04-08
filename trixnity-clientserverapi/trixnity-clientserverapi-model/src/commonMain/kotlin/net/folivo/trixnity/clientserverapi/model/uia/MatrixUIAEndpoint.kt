package net.folivo.trixnity.clientserverapi.model.uia

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

interface MatrixUIAEndpoint<REQUEST, RESPONSE> : MatrixEndpoint<RequestWithUIA<REQUEST>, ResponseWithUIA<RESPONSE>> {
    fun plainRequestSerializerBuilder(mappings: EventContentSerializerMappings, json: Json): KSerializer<REQUEST>? {
        return null
    }

    fun plainResponseSerializerBuilder(mappings: EventContentSerializerMappings, json: Json): KSerializer<RESPONSE>? {
        return null
    }
}