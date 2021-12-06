package net.folivo.trixnity.client

import io.ktor.http.*
import net.folivo.trixnity.client.crypto.OlmService
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.getByStateKey
import net.folivo.trixnity.client.user.UserService
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.*
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent

fun Event<*>?.getStateKey(): String? {
    return when (this) {
        is StateEvent -> this.stateKey
        is StrippedStateEvent -> this.stateKey
        else -> null
    }
}

fun Event<*>?.getEventId(): EventId? {
    return when (this) {
        is RoomEvent -> this.id
        else -> null
    }
}

fun Event<*>?.getOriginTimestamp(): Long? {
    return when (this) {
        is RoomEvent -> this.originTimestamp
        else -> null
    }
}

fun Event<*>?.getRoomId(): RoomId? {
    return when (this) {
        is RoomEvent -> this.roomId
        is StrippedStateEvent -> this.roomId
        is MegolmEvent -> this.roomId
        is RoomAccountDataEvent -> this.roomId
        else -> null
    }
}

fun Event<*>?.getSender(): UserId? {
    return when (this) {
        is StateEvent -> this.sender
        is StrippedStateEvent -> this.sender
        is RoomEvent -> this.sender
        is ToDeviceEvent -> this.sender
        is EphemeralEvent -> this.sender
        is OlmEvent -> this.sender
        else -> null
    }
}

fun String.toMxcUri(): Url =
    Url(this).also { require(it.protocol.name == "mxc") { "uri protocol was not mxc" } }

suspend fun possiblyEncryptEvent(
    content: MessageEventContent,
    roomId: RoomId,
    store: Store,
    olm: OlmService,
    user: UserService
): MessageEventContent {
    return if (store.room.get(roomId).value?.encryptionAlgorithm == EncryptionAlgorithm.Megolm) {
        // The UI should do that, when a room gets opened, because of lazy loading
        // members Trixnity may not know all devices for encryption yet.
        // To ensure an easy usage of Trixnity and because
        // the impact on performance is minimal, we call it here for prevention.
        user.loadMembers(roomId)

        val megolmSettings = store.roomState.getByStateKey<EncryptionEventContent>(roomId)?.content
        requireNotNull(megolmSettings) { "room was marked as encrypted, but did not contain EncryptionEventContent in state" }
        olm.events.encryptMegolm(content, roomId, megolmSettings)
    } else content
}