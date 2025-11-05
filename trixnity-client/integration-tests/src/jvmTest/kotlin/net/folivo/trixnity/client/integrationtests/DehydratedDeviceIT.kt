package net.folivo.trixnity.client.integrationtests

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.room.message.text
import net.folivo.trixnity.client.store.repository.exposed.createExposedRepositoriesModule
import net.folivo.trixnity.client.verification.SelfVerificationMethod
import net.folivo.trixnity.client.verification.VerificationService.SelfVerificationMethods
import net.folivo.trixnity.clientserverapi.client.UIA
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType.User
import net.folivo.trixnity.clientserverapi.model.uia.AuthenticationRequest.Password
import net.folivo.trixnity.core.MSC3814
import net.folivo.trixnity.core.model.events.InitialStateEvent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.crypto.key.DeviceTrustLevel
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@Testcontainers
@MSC3814
class DehydratedDeviceIT {
    private lateinit var startedClient1: StartedClient
    private lateinit var startedClient2: StartedClient

    private val scope = CoroutineScope(Dispatchers.Default)

    @Container
    val synapseDocker = synapseDocker()

    lateinit var baseUrl: Url

    @BeforeTest
    fun beforeEach(): Unit = runBlocking {
        baseUrl = URLBuilder(
            protocol = URLProtocol.HTTP,
            host = synapseDocker.host,
            port = synapseDocker.firstMappedPort
        ).build()
        startedClient1 = registerAndStartClient(
            "client1", "user1", baseUrl,
            createExposedRepositoriesModule(newDatabase())
        )
        startedClient2 = registerAndStartClient(
            "client2", "user2", baseUrl,
            createExposedRepositoriesModule(newDatabase())
        ) {
            experimentalFeatures.enableMSC3814 = true
        }
    }

    @AfterTest
    fun afterEach() {
        startedClient1.client.close()
        startedClient2.client.close()
        scope.cancel()
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    @Test
    fun createAndUseDehydratedDevice(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30_000) {
            val roomId = startedClient1.client.api.room.createRoom(
                invite = setOf(startedClient2.client.userId),
                initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), ""))
            ).getOrThrow()

            val recoveryKey = withCluePrintln("bootstrap client2 second time") {
                val bootstrap = startedClient2.client.key.bootstrapCrossSigning()
                bootstrap.result.getOrThrow()
                    .shouldBeInstanceOf<UIA.Success<Unit>>()
                startedClient2.client.waitForDehydratedDevice()
                bootstrap.recoveryKey
            }
            startedClient2.client.room.getById(roomId).filterNotNull().firstWithTimeout()
            startedClient2.client.api.room.joinRoom(roomId)
            startedClient2.client.logout()
            startedClient2.client.closeSuspending()

            startedClient1.client.user.getAll(roomId).firstWithTimeout { it.size == 2 }
            startedClient1.client.room.sendMessage(roomId) {
                text("some encrypted message")
            }
            startedClient1.client.room.waitForOutboxSent()
            val eventId = checkNotNull(
                startedClient1.client.room.getOutbox(roomId).firstWithTimeout().first().firstWithTimeout()?.eventId
            )

            val startedClient3 =
                startClient("client3", "user2", baseUrl, createExposedRepositoriesModule(newDatabase())) {
                    experimentalFeatures.enableMSC3814 = true
                }
            withCluePrintln("self verify client3") {
                val client3VerificationMethods =
                    startedClient3.client.verification.getSelfVerificationMethods()
                        .filterIsInstance<SelfVerificationMethods.CrossSigningEnabled>()
                        .firstWithTimeout().methods
                client3VerificationMethods.filterIsInstance<SelfVerificationMethod.AesHmacSha2RecoveryKey>()
                    .first()
                    .verify(recoveryKey).getOrThrow()
            }
            val foundContent = startedClient3.client.room.getTimelineEvent(roomId, eventId)
                .map { it?.content?.getOrNull() }.filterNotNull()
                .firstWithTimeout()
            foundContent.shouldBeInstanceOf<RoomMessageEventContent.TextBased.Text>().body shouldBe "some encrypted message"
            startedClient3.client.syncOnce().getOrThrow()

            withCluePrintln("send message from client3") {
                startedClient3.client.room.sendMessage(roomId) { text("hi") }
                startedClient1.client.room.getLastTimelineEvent(roomId).filterNotNull().flatMapLatest { it }
                    .firstWithTimeout {
                        val content = it.content?.getOrNull()
                        content is RoomMessageEventContent.TextBased.Text && content.body == "hi"
                    }
            }
            startedClient3.client.closeSuspending()
        }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    @Test
    fun createAndUseDehydratedDeviceAfterAccountKeyReset(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30_000) {
            val roomId = startedClient1.client.api.room.createRoom(
                invite = setOf(startedClient2.client.userId),
                initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), ""))
            ).getOrThrow()

            val firstDehydratedDeviceId = withCluePrintln("bootstrap client2") {
                startedClient2.client.key.bootstrapCrossSigning().result.getOrThrow()
                    .shouldBeInstanceOf<UIA.Success<Unit>>()
                startedClient2.client.waitForDehydratedDevice()
            }

            startedClient2.client.logout()
            startedClient2.client.closeSuspending()

            val startedClient3 =
                startClient("client3", "user2", baseUrl, createExposedRepositoriesModule(newDatabase())) {
                    experimentalFeatures.enableMSC3814 = true
                }
            val secondDehydratedDeviceId = withCluePrintln("bootstrap client3 first time") {
                val bootstrap = startedClient3.client.key.bootstrapCrossSigning()
                bootstrap.result.getOrThrow()
                    .shouldBeInstanceOf<UIA.Step<Unit>>()
                    .authenticate(Password(User("user2"), startedClient1.password)).getOrThrow()
                startedClient3.client.waitForDehydratedDevice(shouldNotBe = firstDehydratedDeviceId)
            }
            val recoveryKey = withCluePrintln("bootstrap client3 second time") {
                val bootstrap = startedClient3.client.key.bootstrapCrossSigning()
                bootstrap.result.getOrThrow()
                    .shouldBeInstanceOf<UIA.Step<Unit>>()
                    .authenticate(Password(User("user2"), startedClient1.password)).getOrThrow()
                    .shouldBeInstanceOf<UIA.Success<Unit>>()
                startedClient3.client.waitForDehydratedDevice(shouldNotBe = secondDehydratedDeviceId)
                bootstrap.recoveryKey
            }
            startedClient3.client.room.getById(roomId).filterNotNull().firstWithTimeout()
            startedClient3.client.api.room.joinRoom(roomId)
            startedClient3.client.logout()
            startedClient3.client.closeSuspending()

            startedClient1.client.user.getAll(roomId).firstWithTimeout { it.size == 2 }
            startedClient1.client.room.sendMessage(roomId) {
                text("some encrypted message")
            }
            startedClient1.client.room.waitForOutboxSent()
            val eventId =
                checkNotNull(
                    startedClient1.client.room.getOutbox(roomId).firstWithTimeout().first().firstWithTimeout()?.eventId
                )

            val startedClient4 =
                startClient("client4", "user2", baseUrl, createExposedRepositoriesModule(newDatabase())) {
                    experimentalFeatures.enableMSC3814 = true
                }
            withCluePrintln("self verify client4") {
                val client4VerificationMethods =
                    startedClient4.client.verification.getSelfVerificationMethods()
                        .filterIsInstance<SelfVerificationMethods.CrossSigningEnabled>()
                        .firstWithTimeout().methods
                client4VerificationMethods.filterIsInstance<SelfVerificationMethod.AesHmacSha2RecoveryKey>()
                    .first()
                    .verify(recoveryKey).getOrThrow()
            }
            withCluePrintln("decrypt content in client4") {
                val foundContent = startedClient4.client.room.getTimelineEvent(roomId, eventId)
                    .map { it?.content?.getOrNull() }.filterNotNull()
                    .firstWithTimeout()
                foundContent.shouldBeInstanceOf<RoomMessageEventContent.TextBased.Text>().body shouldBe "some encrypted message"
                startedClient4.client.syncOnce()
            }

            withCluePrintln("send message from client4") {
                startedClient4.client.room.sendMessage(roomId) { text("hi") }
                startedClient1.client.room.getLastTimelineEvent(roomId).filterNotNull().flatMapLatest { it }
                    .firstWithTimeout {
                        val content = it.content?.getOrNull()
                        content is RoomMessageEventContent.TextBased.Text && content.body == "hi"
                    }
            }

            startedClient4.client.closeSuspending()
        }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    @Test
    fun recreateDehydratedDeviceOnDeletion(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30_000) {
            val firstDehydratedDeviceId = withCluePrintln("bootstrap client2") {
                val bootstrap = startedClient2.client.key.bootstrapCrossSigning()
                bootstrap.result.getOrThrow()
                    .shouldBeInstanceOf<UIA.Success<Unit>>()
                startedClient2.client.waitForDehydratedDevice()
            }
            startedClient2.client.api.device.deleteDevice(firstDehydratedDeviceId).getOrThrow()
            withCluePrintln("wait for new dehydrated device") {
                val bootstrap = startedClient2.client.key.bootstrapCrossSigning()
                bootstrap.result.getOrThrow()
                    .shouldBeInstanceOf<UIA.Step<Unit>>()
                    .authenticate(Password(User("user2"), startedClient1.password)).getOrThrow()
                    .shouldBeInstanceOf<UIA.Success<Unit>>()
                startedClient2.client.waitForDehydratedDevice(shouldNotBe = firstDehydratedDeviceId)
            }
        }
    }

    private suspend fun MatrixClient.waitForDehydratedDevice(shouldNotBe: String? = null): String {
        val dehydratedDeviceId =
            key.getDeviceKeys(userId).map { deviceKeys ->
                deviceKeys?.find { it.dehydrated == true }
            }.filterNotNull().firstWithTimeout { it.deviceId != shouldNotBe }.deviceId
        key.getTrustLevel(userId, dehydratedDeviceId)
            .firstWithTimeout { it is DeviceTrustLevel.CrossSigned && it.verified }
        return dehydratedDeviceId
    }
}