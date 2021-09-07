package net.folivo.trixnity.client.api

import kotlin.reflect.KClass

internal fun unsupportedEventType(eventType: KClass<*>): String =
    "Event type ${eventType.simpleName} is not supported. If it is a custom type, you should register it in MatrixRestClient. " +
            "If not, ensure, that you use the generic fields (e. g. sendStateEvent<MemberEventContent>(...)) " +
            "so that we can determine the right event type."