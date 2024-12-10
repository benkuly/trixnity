package net.folivo.trixnity.client.store

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.mocks.RepositoryTransactionManagerMock
import net.folivo.trixnity.client.store.cache.ObservableCacheStatisticCollector
import net.folivo.trixnity.client.store.repository.*
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.m.KeyRequestAction
import net.folivo.trixnity.core.model.events.m.RoomKeyRequestEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.secret.SecretKeyRequestEventContent
import net.folivo.trixnity.crypto.SecretType
import kotlin.time.Duration.Companion.seconds

class KeyStoreTest : ShouldSpec({
    timeout = 5_000

    lateinit var outdatedKeysRepository: OutdatedKeysRepository
    lateinit var deviceKeysRepository: DeviceKeysRepository
    lateinit var crossSigningKeysRepository: CrossSigningKeysRepository
    lateinit var keyVerificationStateRepository: KeyVerificationStateRepository
    lateinit var keyChainLinkRepository: KeyChainLinkRepository
    lateinit var secretsRepository: SecretsRepository
    lateinit var secretKeyRequestRepository: SecretKeyRequestRepository
    lateinit var roomKeyRequestRepository: RoomKeyRequestRepository
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
        roomKeyRequestRepository = InMemoryRoomKeyRequestRepository()
        cut = KeyStore(
            outdatedKeysRepository,
            deviceKeysRepository,
            crossSigningKeysRepository,
            keyVerificationStateRepository,
            keyChainLinkRepository,
            secretsRepository,
            secretKeyRequestRepository,
            roomKeyRequestRepository,
            RepositoryTransactionManagerMock(),
            MatrixClientConfiguration(),
            ObservableCacheStatisticCollector(),
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
                        GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf())), "s"
                    )
                )
            )
            val storedSecretKeyRequest = StoredSecretKeyRequest(
                SecretKeyRequestEventContent("1", KeyRequestAction.REQUEST, "A", "r1"),
                setOf("DEV1", "DEV2"),
                Instant.fromEpochMilliseconds(1234)
            )
            val storedRoomKeyRequest = StoredRoomKeyRequest(
                RoomKeyRequestEventContent(KeyRequestAction.REQUEST, "A", "r1"),
                setOf("DEV1", "DEV2"),
                Instant.fromEpochMilliseconds(1234)
            )

            secretKeyRequestRepository.save("1", storedSecretKeyRequest)
            roomKeyRequestRepository.save("1", storedRoomKeyRequest)

            cut.getOutdatedKeysFlow().first() shouldBe setOf(UserId("alice", "server"), UserId("bob", "server"))
            cut.getSecrets() shouldBe mapOf(
                SecretType.M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
                    GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf())), "s"
                )
            )
            cut.getAllSecretKeyRequestsFlow().first { it.isNotEmpty() }
            cut.getAllSecretKeyRequestsFlow().first() shouldBe listOf(storedSecretKeyRequest)

            cut.getAllRoomKeyRequestsFlow().first { it.isNotEmpty() }
            cut.getAllRoomKeyRequestsFlow().first() shouldBe listOf(storedRoomKeyRequest)
        }
        should("start job, which saves changes to database") {
            cut.updateOutdatedKeys { setOf(UserId("alice", "server"), UserId("bob", "server")) }
            cut.updateSecrets {
                mapOf(
                    SecretType.M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
                        GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf())), "s"
                    )
                )
            }

            eventually(5.seconds) {
                outdatedKeysRepository.get(1) shouldBe setOf(UserId("alice", "server"), UserId("bob", "server"))
                secretsRepository.get(1) shouldBe mapOf(
                    SecretType.M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
                        GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf())), "s"
                    )
                )
            }
        }
    }
    suspend fun getKeys() = cut.getDeviceKeys(UserId("alice", "server")).first()
    should("update keys when not known") {
        cut.getOutdatedKeys().shouldBeEmpty()
        val getKeysJob = async {
            getKeys()
        }
        cut.getOutdatedKeysFlow().first { it.isNotEmpty() }
        cut.updateOutdatedKeys { emptySet() }
        getKeysJob.join()
    }
    should("not update keys when context forbids it") {
        cut.getOutdatedKeys().shouldBeEmpty()
        withContext(KeyStore.SkipOutdatedKeys) {
            getKeys()
        }
        cut.getOutdatedKeys().shouldBeEmpty()
    }
})