package net.folivo.trixnity.client.key

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.store.GlobalAccountDataStore
import net.folivo.trixnity.client.store.KeyStore
import net.folivo.trixnity.client.store.StoredSecret
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.MSC3814
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.m.DehydratedDeviceEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.crypto.SecretType
import net.folivo.trixnity.crypto.key.decryptSecret
import net.folivo.trixnity.crypto.key.encryptSecret
import net.folivo.trixnity.utils.encodeUnpaddedBase64
import kotlin.random.Random

private val log = KotlinLogging.logger("net.folivo.trixnity.client.key.KeySecretService")

interface KeySecretService {
    suspend fun decryptOrCreateMissingSecrets(
        key: ByteArray,
        keyId: String,
        keyInfo: SecretKeyEventContent,
    )
}

class KeySecretServiceImpl(
    private val json: Json,
    private val keyStore: KeyStore,
    private val globalAccountDataStore: GlobalAccountDataStore,
    private val api: MatrixClientServerApiClient,
    private val userInfo: UserInfo,
    private val matrixClientConfiguration: MatrixClientConfiguration,
) : KeySecretService {


    override suspend fun decryptOrCreateMissingSecrets(
        key: ByteArray,
        keyId: String,
        keyInfo: SecretKeyEventContent,
    ) {
        val decryptedSecrets = SecretType.entries
            .filter { it.cacheable }
            .subtract(keyStore.getSecrets().keys)
            .mapNotNull { allowedSecret ->
                val event = allowedSecret.getEncryptedSecret(globalAccountDataStore).first()
                if (event != null) {
                    kotlin.runCatching {
                        decryptSecret(key, keyId, keyInfo, allowedSecret.id, event.content, json)
                    }.onFailure {
                        log.warn(it) { "failed to decrypt secret ${allowedSecret.id}" }
                    }.getOrNull()
                        ?.let { allowedSecret to StoredSecret(event, it) }
                } else {
                    log.info { "could not find secret ${allowedSecret.id} to decrypt and cache, try to create a new one" }
                    when (allowedSecret) {
                        // TODO We should implement this to repair corrupt accounts.
                        SecretType.M_CROSS_SIGNING_MASTER,
                        SecretType.M_CROSS_SIGNING_SELF_SIGNING,
                        SecretType.M_CROSS_SIGNING_USER_SIGNING,
                        SecretType.M_MEGOLM_BACKUP_V1 -> {
                            null
                        }

                        @OptIn(MSC3814::class)
                        SecretType.M_DEHYDRATED_DEVICE -> @OptIn(MSC3814::class) {
                            if (matrixClientConfiguration.experimentalFeatures.enableMSC3814) {
                                val newKey =
                                    Random.nextBytes(32).encodeUnpaddedBase64()
                                val event = DehydratedDeviceEventContent(
                                    encryptSecret(
                                        key = key,
                                        keyId = keyId,
                                        secretName = allowedSecret.id,
                                        secret = newKey,
                                        json = json
                                    )
                                )
                                api.user.setAccountData(event, userInfo.userId).fold(
                                    onSuccess = {
                                        allowedSecret to StoredSecret(GlobalAccountDataEvent(event), newKey)
                                    },
                                    onFailure = {
                                        log.error(it) { "failed to add secret ${allowedSecret.id}" }
                                        null
                                    }
                                )
                            } else null
                        }
                    }
                }
            }.toMap()
        keyStore.updateSecrets {
            it + decryptedSecrets
        }
    }
}