package net.folivo.trixnity.core.model.events.m

import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.ToDeviceEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mdummy">matrix spec</a>
 */
@Serializable
data object DummyEventContent : ToDeviceEventContent