package net.folivo.trixnity.client.server

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.store.ServerVersions
import net.folivo.trixnity.client.store.ServerVersionsStore
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.EventHandler
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

private val log = KotlinLogging.logger { }

class ServerVersionsService(
    private val api: MatrixClientServerApiClient,
    private val serverVersionsStore: ServerVersionsStore,
) : EventHandler {
    override fun startInCoroutineScope(scope: CoroutineScope) {
        scope.launch {
            while (true) {
                val newVersions = api.server.getVersions()
                    .onFailure { log.warn(it) { "failed get server version" } }
                    .getOrNull()
                if (newVersions != null) {
                    serverVersionsStore.setServerVersion(
                        ServerVersions(
                            newVersions.versions,
                            newVersions.unstableFeatures
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