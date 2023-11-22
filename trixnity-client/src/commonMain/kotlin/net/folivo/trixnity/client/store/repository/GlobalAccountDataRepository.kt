package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent

interface GlobalAccountDataRepository : MapRepository<String, String, GlobalAccountDataEvent<*>> {
    override fun serializeKey(firstKey: String, secondKey: String): String =
        firstKey + secondKey
}