package de.connect2x.trixnity.serverserverapi.server

import dev.mokkery.matcher.MokkeryMatcherScope
import dev.mokkery.matcher.matches
import io.ktor.client.request.*
import io.ktor.http.*
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.PersistentDataUnit
import de.connect2x.trixnity.core.serialization.events.RoomVersionStore

fun String.trimToFlatJson() = this.trimIndent().lines().joinToString("") { it.replace(": ", ":").trim() }

inline fun <reified T> MokkeryMatcherScope.assert(crossinline assertionBlock: (T) -> Unit): T =
    matches(
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