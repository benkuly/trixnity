package net.folivo.trixnity.clientserverapi.model.sync

import kotlinx.serialization.json.*

// TODO maybe this could be solved completely with contextual serializers
object SyncResponseSerializer : JsonTransformingSerializer<Sync.Response>(Sync.Response.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        require(element is JsonObject)
        val rooms = element["rooms"] ?: return element
        require(rooms is JsonObject)
        val newRooms = JsonObject(buildMap {
            putAll(rooms)
            putAndConvertEventMap("join", rooms["join"], setOf("timeline", "state", "ephemeral", "account_data"))
            putAndConvertEventMap("leave", rooms["leave"], setOf("timeline", "state", "account_data"))
            putAndConvertEventMap("invite", rooms["invite"], setOf("invite_state"))
        })
        return JsonObject(buildMap {
            putAll(element)
            put("rooms", newRooms)
        })
    }

    private fun MutableMap<String, JsonElement>.putAndConvertEventMap(
        key: String,
        source: JsonElement?,
        eventContainerKeys: Set<String>
    ) {
        if (source != null) {
            require(source is JsonObject)
            put(key, convertEventMap(source, eventContainerKeys))
        }
    }

    private fun convertEventMap(source: JsonObject, eventContainerKeys: Set<String>): JsonObject {
        return JsonObject(buildMap {
            source.forEach {
                val roomId = it.key
                val room = it.value
                require(room is JsonObject)
                put(roomId, JsonObject(buildMap {
                    putAll(room)
                    eventContainerKeys.forEach { eventContainerKey ->
                        putAndConvertEventMapContent(eventContainerKey, room[eventContainerKey], roomId)
                    }
                }))
            }
        })
    }

    private fun MutableMap<String, JsonElement>.putAndConvertEventMapContent(
        key: String,
        source: JsonElement?,
        roomId: String,
    ) {
        if (source != null) {
            require(source is JsonObject)
            val events = source["events"]
            if (events != null) {
                require(events is JsonArray)
                put(key, JsonObject(buildMap {
                    putAll(source)
                    put("events", addRoomIdToEvents(events, roomId))
                }))
            }
        }
    }

    private fun addRoomIdToEvents(source: JsonArray, roomId: String): JsonArray {
        return JsonArray(source.map { addRoomIdToEvent(it.jsonObject, roomId) })
    }

    private fun addRoomIdToEvent(event: JsonObject, roomId: String): JsonObject {
        return JsonObject(buildMap {
            putAll(event)
            put("room_id", JsonPrimitive(roomId))
            val unsigned = event["unsigned"]?.jsonObject
            if (unsigned != null) {
                val aggregations = unsigned["m.relations"]?.jsonObject
                val newAggregations =
                    if (aggregations != null) {
                        val thread = aggregations["m.thread"]?.jsonObject
                        if (thread != null) {
                            val latestEvent = thread["latest_event"]?.jsonObject
                            if (latestEvent != null) {
                                JsonObject(buildMap {
                                    putAll(aggregations)
                                    put("m.thread", JsonObject(buildMap {
                                        putAll(thread)
                                        put("latest_event", addRoomIdToEvent(latestEvent, roomId))
                                    }))
                                })
                            } else null
                        } else null
                    } else null
                val redactedBecause = unsigned["redacted_because"]?.jsonObject
                val newRedactedBecause =
                    if (redactedBecause != null) {
                        addRoomIdToEvent(redactedBecause, roomId)
                    } else null
                put("unsigned", JsonObject(buildMap {
                    putAll(unsigned)
                    if (newAggregations != null) {
                        put("m.relations", newAggregations)
                    }
                    if (newRedactedBecause != null) {
                        put("redacted_because", newRedactedBecause)
                    }
                }))
            }
        })
    }
}