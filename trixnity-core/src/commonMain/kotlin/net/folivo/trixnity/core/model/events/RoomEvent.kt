package net.folivo.trixnity.core.model.events

import net.folivo.trixnity.core.model.MatrixId.*

/**
 * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#room-event-fields">matrix spec</a>
 */
interface RoomEvent<C> : Event<C> {
    val id: EventId
    val sender: UserId
    val originTimestamp: Long
    val roomId: RoomId?
    val unsigned: UnsignedData?

    interface UnsignedData {
        val age: Long?
        val redactedBecause: Event<Any>?
        val transactionId: String?
    }
}