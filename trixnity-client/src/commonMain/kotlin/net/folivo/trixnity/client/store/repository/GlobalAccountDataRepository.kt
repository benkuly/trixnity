package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.core.model.events.Event.GlobalAccountDataEvent

interface GlobalAccountDataRepository : MapRepository<String, String, GlobalAccountDataEvent<*>> {
    override fun serializeKey(key: String): String = this::class.simpleName + key
    override fun serializeKey(firstKey: String, secondKey: String): String = serializeKey(firstKey) + secondKey
}