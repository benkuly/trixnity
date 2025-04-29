package net.folivo.trixnity.client.store

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.eventually
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
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.test.utils.testClock
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class KeyStoreTest : TrixnityBaseTest() {

    private val outdatedKeysRepository = InMemoryOutdatedKeysRepository() as OutdatedKeysRepository
    private val deviceKeysRepository = InMemoryDeviceKeysRepository() as DeviceKeysRepository
    private val crossSigningKeysRepository = InMemoryCrossSigningKeysRepository() as CrossSigningKeysRepository
    private val keyVerificationStateRepository =
        InMemoryKeyVerificationStateRepository() as KeyVerificationStateRepository
    private val keyChainLinkRepository = InMemoryKeyChainLinkRepository() as KeyChainLinkRepository
    private val secretsRepository = InMemorySecretsRepository() as SecretsRepository
    private val secretKeyRequestRepository = InMemorySecretKeyRequestRepository() as SecretKeyRequestRepository
    private val roomKeyRequestRepository = InMemoryRoomKeyRequestRepository() as RoomKeyRequestRepository

    private val cut = KeyStore(
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
        testScope.backgroundScope,
        testScope.testClock,
    )

    @Test
    fun `init » load values from database`() = runTest {
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

    @Test
    fun `init » start job which saves changes to database`() = runTest {
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

    @Test
    fun `update keys when not known`() = runTest {
        cut.getOutdatedKeys().shouldBeEmpty()
        val getKeysJob = async {
            getKeys()
        }
        cut.getOutdatedKeysFlow().first { it.isNotEmpty() }
        cut.updateOutdatedKeys { emptySet() }
        getKeysJob.join()
    }

    @Test
    fun `not update keys when context forbids it`() = runTest {
        cut.getOutdatedKeys().shouldBeEmpty()
        withContext(KeyStore.SkipOutdatedKeys) {
            getKeys()
        }
        cut.getOutdatedKeys().shouldBeEmpty()
    }

    private suspend fun getKeys() = cut.getDeviceKeys(UserId("alice", "server")).first()
}