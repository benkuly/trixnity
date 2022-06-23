package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent

typealias GlobalAccountDataRepository = TwoDimensionsStoreRepository<String, String, GlobalAccountDataEvent<*>>