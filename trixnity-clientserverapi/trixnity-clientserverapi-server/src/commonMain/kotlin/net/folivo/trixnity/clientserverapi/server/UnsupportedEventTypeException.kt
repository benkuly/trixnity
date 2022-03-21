package net.folivo.trixnity.clientserverapi.server

import io.ktor.http.*
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixServerException

class UnsupportedEventTypeException(eventType: String) : MatrixServerException(
    HttpStatusCode.BadRequest, ErrorResponse.Unrecognized(
        "Event type $eventType is not supported. If it is a custom type, you should register it in MatrixRestClient. " +
                "If not, ensure, that you use the generic fields (e. g. sendStateEvent<MemberEventContent>(...)) " +
                "so that we can determine the right event type."
    )
)