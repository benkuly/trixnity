package net.folivo.trixnity.core.model.events

/**
 * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#event-fields">matrix spec</a>
 */
interface Event<C> {
    val type: String
    val content: C
}