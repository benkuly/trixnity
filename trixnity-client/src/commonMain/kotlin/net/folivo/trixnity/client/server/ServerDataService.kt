package net.folivo.trixnity.client.server

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import net.folivo.trixnity.client.store.ServerData
import net.folivo.trixnity.client.store.ServerDataStore
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.EventHandler
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

private val log = KotlinLogging.logger { }

class ServerDataService(
    private val api: MatrixClientServerApiClient,
    private val serverDataStore: ServerDataStore,
) : EventHandler {
    override fun startInCoroutineScope(scope: CoroutineScope) {
        scope.launch {
            while (true) {
                coroutineScope {
                    val newVersionsAsync = async {
                        api.server.getVersions()
                            .onFailure { log.warn(it) { "failed get server version" } }
                            .getOrNull()
                    }
                    val newMediaConfigAsync = async {
                        api.media.getConfig()
                            .onFailure { log.warn(it) { "failed get media config" } }
                            .getOrNull()
                    }
                    val newVersions = newVersionsAsync.await()
                    val newMediaConfig = newMediaConfigAsync.await()
                    if (newVersions != null && newMediaConfig != null) {
                        serverDataStore.setServerData(
                            ServerData(
                                newVersions,
                                newMediaConfig,
                            )
                        )
                        delay(1.days)
                    } else {
                        log.warn { "failed to get server versions" }
                        delay(10.minutes)
                    }
                }
            }
        }
    }
}