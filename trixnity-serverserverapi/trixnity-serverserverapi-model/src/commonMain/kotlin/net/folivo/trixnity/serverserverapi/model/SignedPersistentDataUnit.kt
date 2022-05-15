package net.folivo.trixnity.serverserverapi.model

import kotlinx.serialization.Contextual
import net.folivo.trixnity.core.model.events.PersistentDataUnit
import net.folivo.trixnity.core.model.keys.Signed

typealias SignedPersistentDataUnit<T> = Signed<@Contextual PersistentDataUnit<T>, String>