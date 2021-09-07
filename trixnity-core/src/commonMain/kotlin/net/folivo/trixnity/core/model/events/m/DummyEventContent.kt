package net.folivo.trixnity.core.model.events.m

import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.ToDeviceEventContent

/**
 * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#id82">matrix spec</a>
 */
@Serializable
object DummyEventContent : ToDeviceEventContent