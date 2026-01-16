package de.connect2x.trixnity.serverserverapi.model

import kotlinx.serialization.Contextual
import de.connect2x.trixnity.core.model.events.PersistentDataUnit
import de.connect2x.trixnity.core.model.keys.Signed

typealias SignedPersistentDataUnit<T> = Signed<@Contextual PersistentDataUnit<T>, String>
typealias SignedPersistentStateDataUnit<T> = Signed<@Contextual PersistentDataUnit.PersistentStateDataUnit<T>, String>
typealias SignedPersistentMessageDataUnit<T> = Signed<@Contextual PersistentDataUnit.PersistentMessageDataUnit<T>, String>