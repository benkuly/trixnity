package net.folivo.trixnity.core.model.events


/**
 * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#state-event-fields">matrix spec</a>
 */
interface StateEvent<C> : RoomEvent<C> {
    val stateKey: String
    val previousContent: C?
}