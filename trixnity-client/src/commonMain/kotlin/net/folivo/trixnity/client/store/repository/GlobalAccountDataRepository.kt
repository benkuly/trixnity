package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.core.model.events.Event.GlobalAccountDataEvent

typealias GlobalAccountDataRepository = MinimalStoreRepository<String, GlobalAccountDataEvent<*>>