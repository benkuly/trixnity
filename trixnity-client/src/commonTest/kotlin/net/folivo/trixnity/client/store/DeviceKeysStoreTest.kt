package net.folivo.trixnity.client.store

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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

class DeviceKeysStoreTest : ShouldSpec({
    timeout = 30_000

    val outdatedKeysRepository = mockk<OutdatedKeysRepository>(relaxUnitFun = true)
    val deviceKeysRepository = mockk<DeviceKeysRepository>(relaxUnitFun = true)
    val crossSigningKeysRepository = mockk<CrossSigningKeysRepository>(relaxUnitFun = true)
    val keyVerificationStateRepository = mockk<KeyVerificationStateRepository>(relaxUnitFun = true)
    val keyChainLinkRepository = mockk<KeyChainLinkRepository>(relaxUnitFun = true)
    val secretsRepository = mockk<SecretsRepository>(relaxUnitFun = true)
    val secretKeyRequestRepository = mockk<SecretKeyRequestRepository>(relaxUnitFun = true)
    lateinit var storeScope: CoroutineScope
    lateinit var cut: KeyStore

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
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
        clearAllMocks()
        storeScope.cancel()
    }

    context(KeyStore::init.name) {
        should("load values from database") {
            coEvery { outdatedKeysRepository.get(1) } returns setOf(
                UserId("alice", "server"), UserId("bob", "server")
            )
            coEvery { secretsRepository.get(1) } returns mapOf(
                AllowedSecretType.M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
                    Event.GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf())), "s"
                )
            )
            val storedSecretKeyRequest = StoredSecretKeyRequest(
                SecretKeyRequestEventContent("1", KeyRequestAction.REQUEST, "A", "r1"),
                setOf("DEV1", "DEV2"),
                Instant.fromEpochMilliseconds(1234)
            )
            coEvery { secretKeyRequestRepository.getAll() } returns listOf(storedSecretKeyRequest)

            cut.init()

            cut.outdatedKeys.value shouldBe setOf(UserId("alice", "server"), UserId("bob", "server"))
            cut.secrets.value shouldBe mapOf(
                AllowedSecretType.M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
                    Event.GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf())), "s"
                )
            )
            cut.allSecretKeyRequests.first { it.isNotEmpty() }
            cut.allSecretKeyRequests.value shouldBe listOf(storedSecretKeyRequest)
        }
        should("start job, which saves changes to database") {
            coEvery { outdatedKeysRepository.get(1) } returns null
            coEvery { secretsRepository.get(1) } returns null
            coEvery { secretKeyRequestRepository.getAll() } returns listOf()

            cut.init()

            cut.outdatedKeys.value = setOf(UserId("alice", "server"), UserId("bob", "server"))
            cut.secrets.value = mapOf(
                AllowedSecretType.M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
                    Event.GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf())), "s"
                )
            )

            coVerify(timeout = 1_000) {
                outdatedKeysRepository.save(
                    1, setOf(UserId("alice", "server"), UserId("bob", "server"))
                )
                secretsRepository.save(
                    1, mapOf(
                        AllowedSecretType.M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
                            Event.GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf())), "s"
                        )
                    )
                )
            }
        }
    }
})