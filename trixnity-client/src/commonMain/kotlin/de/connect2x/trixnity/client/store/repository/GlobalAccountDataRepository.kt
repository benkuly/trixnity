package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent

interface GlobalAccountDataRepository : MapRepository<String, String, GlobalAccountDataEvent<*>> {
    override fun serializeKey(firstKey: String, secondKey: String): String =
        firstKey + secondKey
}