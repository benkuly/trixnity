package net.folivo.trixnity.serverserverapi.model

import kotlinx.serialization.Contextual
import net.folivo.trixnity.core.model.events.PersistentDataUnit
import net.folivo.trixnity.core.model.keys.Signed

typealias SignedPersistentDataUnit<T> = Signed<@Contextual PersistentDataUnit<T>, String>
typealias SignedPersistentStateDataUnit<T> = Signed<@Contextual PersistentDataUnit.PersistentStateDataUnit<T>, String>
typealias SignedPersistentMessageDataUnit<T> = Signed<@Contextual PersistentDataUnit.PersistentMessageDataUnit<T>, String>