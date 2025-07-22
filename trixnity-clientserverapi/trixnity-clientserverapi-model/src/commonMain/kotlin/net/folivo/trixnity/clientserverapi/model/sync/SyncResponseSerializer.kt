package net.folivo.trixnity.clientserverapi.model.sync

import kotlinx.serialization.json.*

// TODO maybe this could be solved completely with contextual serializers
@Deprecated("Replaced by Sync.Response.serializer()", replaceWith = ReplaceWith("Sync.Response.serializer()"))
object SyncResponseSerializer : JsonTransformingSerializer<Sync.Response>(Sync.Response.serializer())