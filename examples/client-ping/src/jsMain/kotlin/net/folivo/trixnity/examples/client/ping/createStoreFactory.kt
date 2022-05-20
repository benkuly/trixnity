package net.folivo.trixnity.examples.client.ping

import kotlinx.coroutines.CoroutineScope
import net.folivo.trixnity.client.store.InMemoryStore
import net.folivo.trixnity.client.store.InMemoryStoreFactory
import net.folivo.trixnity.client.store.StoreFactory

actual fun createStoreFactory(scope: CoroutineScope): StoreFactory {
    return InMemoryStoreFactory(InMemoryStore(scope))
}