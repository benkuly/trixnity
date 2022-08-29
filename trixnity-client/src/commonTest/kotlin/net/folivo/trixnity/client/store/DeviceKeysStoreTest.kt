package net.folivo.trixnity.client.store

import io.kotest.assertions.timing.eventually
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.NoopRepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.*
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.KeyRequestAction
import net.folivo.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.secret.SecretKeyRequestEventContent
import net.folivo.trixnity.crypto.SecretType
import kotlin.time.Duration.Companion.seconds

class DeviceKeysStoreTest : ShouldSpec({
    timeout = 30_000

    lateinit var outdatedKeysRepository: OutdatedKeysRepository
    lateinit var deviceKeysRepository: DeviceKeysRepository
    lateinit var crossSigningKeysRepository: CrossSigningKeysRepository
    lateinit var keyVerificationStateRepository: KeyVerificationStateRepository
    lateinit var keyChainLinkRepository: KeyChainLinkRepository
    lateinit var secretsRepository: SecretsRepository
    lateinit var secretKeyRequestRepository: SecretKeyRequestRepository
    lateinit var storeScope: CoroutineScope
    lateinit var cut: KeyStore

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        outdatedKeysRepository = InMemoryOutdatedKeysRepository()
        deviceKeysRepository = InMemoryDeviceKeysRepository()
        crossSigningKeysRepository = InMemoryCrossSigningKeysRepository()
        keyVerificationStateRepository = InMemoryKeyVerificationStateRepository()
        keyChainLinkRepository = InMemoryKeyChainLinkRepository()
        secretsRepository = InMemorySecretsRepository()
        secretKeyRequestRepository = InMemorySecretKeyRequestRepository()
        cut = KeyStore(
            outdatedKeysRepository,
            deviceKeysRepository,
            crossSigningKeysRepository,
            keyVerificationStateRepository,
            keyChainLinkRepository,
            secretsRepository,
            secretKeyRequestRepository,
            NoopRepositoryTransactionManager,
            storeScope
        )
    }
    afterTest {
        storeScope.cancel()
    }

    context(KeyStore::init.name) {
        should("load values from database") {
            outdatedKeysRepository.save(
                1, setOf(
                    UserId("alice", "server"), UserId("bob", "server")
                )
            )
            secretsRepository.save(
                1, mapOf(
                    SecretType.M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
                        Event.GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf())), "s"
                    )
                )
            )
            val storedSecretKeyRequest = StoredSecretKeyRequest(
                SecretKeyRequestEventContent("1", KeyRequestAction.REQUEST, "A", "r1"),
                setOf("DEV1", "DEV2"),
                Instant.fromEpochMilliseconds(1234)
            )
            secretKeyRequestRepository.save(
                "1", StoredSecretKeyRequest(
                    SecretKeyRequestEventContent("1", KeyRequestAction.REQUEST, "A", "r1"),
                    setOf("DEV1", "DEV2"),
                    Instant.fromEpochMilliseconds(1234)
                )
            )

            cut.init()

            cut.outdatedKeys.value shouldBe setOf(UserId("alice", "server"), UserId("bob", "server"))
            cut.secrets.value shouldBe mapOf(
                SecretType.M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
                    Event.GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf())), "s"
                )
            )
            cut.allSecretKeyRequests.first { it.isNotEmpty() }
            cut.allSecretKeyRequests.value shouldBe listOf(storedSecretKeyRequest)
        }
        should("start job, which saves changes to database") {
            cut.init()

            cut.outdatedKeys.value = setOf(UserId("alice", "server"), UserId("bob", "server"))
            cut.secrets.value = mapOf(
                SecretType.M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
                    Event.GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf())), "s"
                )
            )

            eventually(5.seconds) {
                outdatedKeysRepository.get(1) shouldBe setOf(UserId("alice", "server"), UserId("bob", "server"))
                secretsRepository.get(1) shouldBe mapOf(
                    SecretType.M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
                        Event.GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf())), "s"
                    )
                )
            }
        }
    }
})