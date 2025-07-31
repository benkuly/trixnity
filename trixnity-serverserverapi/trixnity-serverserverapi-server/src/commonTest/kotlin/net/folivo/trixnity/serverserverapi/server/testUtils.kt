package net.folivo.trixnity.serverserverapi.server

import dev.mokkery.matcher.ArgMatchersScope
import dev.mokkery.matcher.matching
import io.ktor.client.request.*
import io.ktor.http.*
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.PersistentDataUnit
import net.folivo.trixnity.core.serialization.events.RoomVersionStore

fun String.trimToFlatJson() = this.trimIndent().lines().joinToString("") { it.replace(": ", ":").trim() }

inline fun <reified T> ArgMatchersScope.assert(crossinline assertionBlock: (T) -> Unit): T =
    matching(
        toString = { "assert" },
        predicate = {
            try {
                assertionBlock(it)
                true
            } catch (error: AssertionError) {
                false
            }
        },
    )

fun HttpRequestBuilder.someSignature() =
    header(HttpHeaders.Authorization, """X-Matrix origin=other.hs.host,key="ed25519:key1",sig="sig"""")

class TestRoomVersionStore(val roomVersion: String) : RoomVersionStore {
    override fun getRoomVersion(roomId: RoomId): String = roomVersion

    override fun setRoomVersion(
        pdu: PersistentDataUnit.PersistentStateDataUnit<*>,
        roomVersion: String
    ) {
    }
}