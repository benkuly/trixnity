package net.folivo.trixnity.client.integrationtests

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.key
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.room.message.text
import net.folivo.trixnity.client.store.repository.exposed.createExposedRepositoriesModule
import net.folivo.trixnity.client.user
import net.folivo.trixnity.client.verification
import net.folivo.trixnity.client.verification.SelfVerificationMethod
import net.folivo.trixnity.client.verification.VerificationService.SelfVerificationMethods
import net.folivo.trixnity.clientserverapi.client.UIA
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.clientserverapi.model.uia.AuthenticationRequest
import net.folivo.trixnity.core.MSC3814
import net.folivo.trixnity.core.model.events.InitialStateEvent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
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

            val bootstrap = startedClient2.client.key.bootstrapCrossSigning()
            withClue("bootstrap client2") {
                bootstrap.result.getOrThrow()
                    .shouldBeInstanceOf<UIA.Success<Unit>>()
            }
            startedClient2.client.room.getById(roomId).filterNotNull().first()
            startedClient2.client.api.room.joinRoom(roomId)
            startedClient2.client.key.getDeviceKeys(startedClient2.client.userId).first { deviceKeys ->
                deviceKeys?.any { it.dehydrated == true } == true
            }
            startedClient2.client.logout()
            startedClient2.client.closeSuspending()

            startedClient1.client.user.getAll(roomId).first { it.size == 2 }
            startedClient1.client.room.sendMessage(roomId) {
                text("some encrypted message")
            }
            startedClient1.client.room.waitForOutboxSent()
            val eventId = checkNotNull(startedClient1.client.room.getOutbox(roomId).first().first().first()?.eventId)

            val startedClient3 =
                startClient("client3", "user2", baseUrl, createExposedRepositoriesModule(newDatabase())) {
                    experimentalFeatures.enableMSC3814 = true
                }
            withClue("self verify client3") {
                val client3VerificationMethods =
                    startedClient3.client.verification.getSelfVerificationMethods()
                        .filterIsInstance<SelfVerificationMethods.CrossSigningEnabled>()
                        .first().methods
                client3VerificationMethods.filterIsInstance<SelfVerificationMethod.AesHmacSha2RecoveryKey>()
                    .first()
                    .verify(bootstrap.recoveryKey).getOrThrow()
            }
            val foundContent = startedClient3.client.room.getTimelineEvent(roomId, eventId)
                .map { it?.content?.getOrNull() }.filterNotNull()
                .first()
            foundContent.shouldBeInstanceOf<RoomMessageEventContent.TextBased.Text>().body shouldBe "some encrypted message"
            startedClient3.client.syncOnce().getOrThrow()

            withClue("send message from client3") {
                startedClient3.client.room.sendMessage(roomId) { text("hi") }
                startedClient1.client.room.getLastTimelineEvent(roomId).filterNotNull().flatMapLatest { it }
                    .first {
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

            withClue("bootstrap client2") {
                startedClient2.client.key.bootstrapCrossSigning().result.getOrThrow()
                    .shouldBeInstanceOf<UIA.Success<Unit>>()
            }
            val firstDehydratedDeviceId = startedClient2.client.key.getDeviceKeys(startedClient2.client.userId)
                .map { deviceKeys -> deviceKeys?.firstOrNull { it.dehydrated == true } }
                .filterNotNull().first().deviceId

            startedClient2.client.closeSuspending()

            val startedClient3 =
                startClient("client3", "user2", baseUrl, createExposedRepositoriesModule(newDatabase())) {
                    experimentalFeatures.enableMSC3814 = true
                }
            val bootstrap = startedClient3.client.key.bootstrapCrossSigning()
            withClue("bootstrap client3") {
                val step1 = bootstrap.result.getOrThrow()
                    .shouldBeInstanceOf<UIA.Step<Unit>>()
                step1.authenticate(
                    AuthenticationRequest.Password(
                        IdentifierType.User("user2"),
                        startedClient3.password
                    )
                )
                    .getOrThrow()
                    .shouldBeInstanceOf<UIA.Success<Unit>>()
            }
            startedClient3.client.key.getDeviceKeys(startedClient3.client.userId)
                .map { deviceKeys -> deviceKeys?.firstOrNull { it.dehydrated == true && it.deviceId != firstDehydratedDeviceId } }
                .filterNotNull().first().deviceId shouldNotBe firstDehydratedDeviceId
            startedClient3.client.room.getById(roomId).filterNotNull().first()
            startedClient3.client.api.room.joinRoom(roomId)
            startedClient3.client.logout()
            startedClient3.client.closeSuspending()

            startedClient1.client.user.getAll(roomId).first { it.size == 2 }
            startedClient1.client.room.sendMessage(roomId) {
                text("some encrypted message")
            }
            startedClient1.client.room.waitForOutboxSent()
            val eventId =
                checkNotNull(startedClient1.client.room.getOutbox(roomId).first().first().first()?.eventId)

            val startedClient4 =
                startClient("client4", "user2", baseUrl, createExposedRepositoriesModule(newDatabase())) {
                    experimentalFeatures.enableMSC3814 = true
                }
            withClue("self verify client4") {
                val client4VerificationMethods =
                    startedClient4.client.verification.getSelfVerificationMethods()
                        .filterIsInstance<SelfVerificationMethods.CrossSigningEnabled>()
                        .first().methods
                client4VerificationMethods.filterIsInstance<SelfVerificationMethod.AesHmacSha2RecoveryKey>()
                    .first()
                    .verify(bootstrap.recoveryKey).getOrThrow()
            }
            withClue("decrypt content in client4") {
                val foundContent = startedClient4.client.room.getTimelineEvent(roomId, eventId)
                    .map { it?.content?.getOrNull() }.filterNotNull()
                    .first()
                foundContent.shouldBeInstanceOf<RoomMessageEventContent.TextBased.Text>().body shouldBe "some encrypted message"
                startedClient4.client.syncOnce()
            }

            withClue("send message from client4") {
                startedClient4.client.room.sendMessage(roomId) { text("hi") }
                startedClient1.client.room.getLastTimelineEvent(roomId).filterNotNull().flatMapLatest { it }
                    .first {
                        val content = it.content?.getOrNull()
                        content is RoomMessageEventContent.TextBased.Text && content.body == "hi"
                    }
            }

            startedClient4.client.closeSuspending()
        }
    }
}