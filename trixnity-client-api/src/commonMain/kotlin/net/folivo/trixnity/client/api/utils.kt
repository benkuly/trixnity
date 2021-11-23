package net.folivo.trixnity.client.api

import io.ktor.http.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.MatrixId
import kotlin.reflect.KClass

internal fun unsupportedEventType(eventType: KClass<*>): String =
    "Event type ${eventType.simpleName} is not supported. If it is a custom type, you should register it in MatrixRestClient. " +
            "If not, ensure, that you use the generic fields (e. g. sendStateEvent<MemberEventContent>(...)) " +
            "so that we can determine the right event type."

fun MatrixId.e(): String { // TODO remove when https://youtrack.jetbrains.com/issue/KTOR-1658 is fixed
    return full.encodeURLQueryComponent(true)
}

fun EventId.e(): String { // TODO remove when https://youtrack.jetbrains.com/issue/KTOR-1658 is fixed
    return full.encodeURLQueryComponent(true)
}