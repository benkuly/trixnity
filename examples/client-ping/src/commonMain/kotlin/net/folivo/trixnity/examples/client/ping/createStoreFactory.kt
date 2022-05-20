package net.folivo.trixnity.examples.client.ping

import kotlinx.coroutines.CoroutineScope
import net.folivo.trixnity.client.store.StoreFactory

expect fun createStoreFactory(scope: CoroutineScope): StoreFactory