package de.connect2x.trixnity.client.server

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.lognity.api.logger.warn
import de.connect2x.trixnity.client.store.ServerData
import de.connect2x.trixnity.client.store.ServerDataStore
import de.connect2x.trixnity.client.store.StoreTransactionManager
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.clientserverapi.client.oauth2.OAuth2MatrixClientAuthProvider
import de.connect2x.trixnity.clientserverapi.model.media.GetMediaConfig
import de.connect2x.trixnity.core.EventHandler
import de.connect2x.trixnity.core.MSC4143
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val log = Logger("de.connect2x.trixnity.client.server.ServerDataService")

class ServerDataService(
    private val api: MatrixClientServerApiClient,
    private val serverDataStore: ServerDataStore,
    private val tm: StoreTransactionManager,
) : EventHandler {
    companion object {
        private const val MATRIX_SPEC_1_11 = "v1.11"
    }

    @OptIn(MSC4143::class)
    override fun startInCoroutineScope(scope: CoroutineScope) {
        scope.launch {
            while (currentCoroutineContext().isActive) {
                coroutineScope {
                    val newVersionsAsync = async {
                        api.server.getVersions().onFailure { log.warn(it) { "failed get server version" } }.getOrNull()
                    }

                    val newCapabilitiesAsync = async {
                        api.server
                            .getCapabilities()
                            .onFailure { log.warn(it) { "failed get server capabilities" } }
                            .getOrNull()
                    }
                    val newVersions = newVersionsAsync.await()
                    val newMediaConfigAsync = async {
                        if (newVersions == null) return@async null
                        if (newVersions.versions.contains(MATRIX_SPEC_1_11)) {
                                api.media.getConfig()
                            } else {
                                @Suppress("DEPRECATION")
                                api.media.getConfigLegacy().map { GetMediaConfig.Response(it.maxUploadSize) }
                            }
                            .onFailure { log.warn(it) { "failed get media config" } }
                            .getOrNull()
                    }
                    val newRtcTransportsAsync = async {
                        if (newVersions == null) return@async null
                        if (
                            !newVersions.unstableFeatures.containsKey("org.matrix.msc4143") &&
                                !newVersions.unstableFeatures.containsKey("org.matrix.msc4143.stable")
                        )
                            return@async null
                        api.rtc
                            .getTransports()
                            .onFailure { log.warn(it) { "failed get server capabilities" } }
                            .getOrNull()
                    }
                    val newOAuth2ServerMetadataAsync = async {
                        if (api.authProviderType != OAuth2MatrixClientAuthProvider::class) return@async null
                        api.authentication
                            .getOAuth2ServerMetadata()
                            .onFailure { log.warn(it) { "failed get oAuth2ServerMetadata" } }
                            .getOrNull()
                    }
                    val newMediaConfig = newMediaConfigAsync.await()
                    val newCapabilities = newCapabilitiesAsync.await()
                    val newOAuth2ServerMetadata = newOAuth2ServerMetadataAsync.await()
                    val newRtcTransports = newRtcTransportsAsync.await()
                    if (newVersions != null && newMediaConfig != null && newCapabilities != null) {
                        tm.writeTransaction {
                            serverDataStore.setServerData(
                                ServerData(
                                    versions = newVersions,
                                    mediaConfig = newMediaConfig,
                                    capabilities = newCapabilities,
                                    auth = newOAuth2ServerMetadata,
                                    rtcTransports = newRtcTransports,
                                )
                            )
                        }
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
