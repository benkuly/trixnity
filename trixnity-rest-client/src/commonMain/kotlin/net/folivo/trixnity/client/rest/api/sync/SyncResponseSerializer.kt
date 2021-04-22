package net.folivo.trixnity.client.rest.api.sync

import kotlinx.serialization.json.*

object SyncResponseSerializer : JsonTransformingSerializer<SyncResponse>(SyncResponse.serializer()) {
    @ExperimentalStdlibApi
    override fun transformDeserialize(element: JsonElement): JsonElement {
        require(element is JsonObject)
        val rooms = element["rooms"] ?: return element
        require(rooms is JsonObject)
        val newRooms = JsonObject(buildMap {
            putAll(rooms)
            putAndConvertEventMap("join", rooms["join"], setOf("timeline", "state", "ephermal"))
            putAndConvertEventMap("leave", rooms["leave"], setOf("timeline", "state"))
            putAndConvertEventMap("invite", rooms["invite"], setOf("invite_state"))
        })
        return JsonObject(buildMap {
            putAll(element)
            put("rooms", newRooms)
        })
    }

    @ExperimentalStdlibApi
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

    @ExperimentalStdlibApi
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

    @ExperimentalStdlibApi
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

    @ExperimentalStdlibApi
    private fun addRoomIdToEvents(source: JsonArray, roomId: String): JsonArray {
        return JsonArray(source.map { timelineEvent ->
            require(timelineEvent is JsonObject)
            JsonObject(buildMap {
                putAll(timelineEvent)
                put("room_id", JsonPrimitive(roomId))
            })
        })
    }
}