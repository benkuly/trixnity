package net.folivo.trixnity.client.server

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import net.folivo.trixnity.client.store.ServerData
import net.folivo.trixnity.client.store.ServerDataStore
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.media.GetMediaConfig
import net.folivo.trixnity.core.EventHandler
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

private val log = KotlinLogging.logger("net.folivo.trixnity.client.server.ServerDataService")

class ServerDataService(
    private val api: MatrixClientServerApiClient,
    private val serverDataStore: ServerDataStore,
) : EventHandler {
    companion object {
        private const val MATRIX_SPEC_1_11 = "v1.11"
    }

    override fun startInCoroutineScope(scope: CoroutineScope) {
        scope.launch {
            while (true) {
                coroutineScope {
                    val newVersionsAsync = async {
                        api.server.getVersions()
                            .onFailure { log.warn(it) { "failed get server version" } }
                            .getOrNull()
                    }

                    val newCapabilitiesAsync = async {
                        api.server.getCapabilities()
                            .onFailure { log.warn(it) { "failed get server capabilities" } }
                            .getOrNull()
                    }
                    val newVersions = newVersionsAsync.await()
                    val newMediaConfigAsync = async {
                        if (newVersions != null) {
                            if (newVersions.versions.contains(MATRIX_SPEC_1_11)) {
                                api.media.getConfig()
                            } else {
                                @Suppress("DEPRECATION")
                                api.media.getConfigLegacy().map { GetMediaConfig.Response(it.maxUploadSize) }
                            }.onFailure { log.warn(it) { "failed get media config" } }
                                .getOrNull()
                        } else null
                    }
                    val newMediaConfig = newMediaConfigAsync.await()
                    val newCapabilities = newCapabilitiesAsync.await()
                    if (newVersions != null && newMediaConfig != null && newCapabilities != null) {
                        serverDataStore.setServerData(
                            ServerData(
                                newVersions,
                                newMediaConfig,
                                newCapabilities,
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