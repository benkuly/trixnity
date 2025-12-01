package net.folivo.trixnity.client.integrationtests

import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.assertions.withClue
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.java.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.cryptodriver.vodozemac.vodozemac
import net.folivo.trixnity.client.media.inMemory
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.clientserverapi.client.*
import net.folivo.trixnity.clientserverapi.model.authentication.AccountType
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.clientserverapi.model.authentication.Register
import net.folivo.trixnity.clientserverapi.model.uia.AuthenticationRequest
import net.folivo.trixnity.utils.nextString
import org.jetbrains.exposed.sql.Database
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

const val synapseVersion =
    "v1.136.0" // TODO you should update this from time to time. https://github.com/element-hq/synapse/releases

fun synapseDocker() =
    GenericContainer<Nothing>(DockerImageName.parse("docker.io/matrixdotorg/synapse:$synapseVersion"))
        .apply {
            withEnv(
                mapOf(
                    "VIRTUAL_HOST" to "localhost",
                    "VIRTUAL_PORT" to "8008",
                    "UID" to "1000",
                    "GID" to "1000"
                )
            )
            withClasspathResourceMapping("data", "/data", BindMode.READ_WRITE)
            withExposedPorts(8008)
            waitingFor(Wait.forHealthcheck())
            withNetwork(Network.SHARED)
        }

private val javaHttpClientEngine = Java.create() // reuse engine in all tests
private const val defaultPassword = "user$1passw0rd"

suspend fun MatrixClientServerApiClient.register(
    username: String? = null,
    password: String = defaultPassword,
    deviceId: String? = null
): ClassicMatrixClientAuthProviderData {
    val registerStep = authentication.register(
        password = password,
        username = username,
        deviceId = deviceId,
        accountType = AccountType.USER,
        refreshToken = true,
    ).getOrThrow()
    registerStep.shouldBeInstanceOf<UIA.Step<Register.Response>>()
    val registerResult = registerStep.authenticate(AuthenticationRequest.Dummy).getOrThrow()
        .shouldBeInstanceOf<UIA.Success<Register.Response>>()
    val (_, _, accessToken, expiresIn, refreshToken) = registerResult.value
    requireNotNull(accessToken)
    return ClassicMatrixClientAuthProviderData(accessToken, expiresIn, refreshToken)
}

fun newDatabase() = Database.connect("jdbc:h2:mem:${Random.nextString(22)};DB_CLOSE_DELAY=-1;")

data class StartedClient(
    val client: MatrixClient,
    val password: String
)

suspend fun registerAndStartClient(
    name: String,
    username: String = name,
    baseUrl: Url,
    repositoriesModule: RepositoriesModule,
    configuration: MatrixClientConfiguration.() -> Unit = {}
): StartedClient {
    val client = MatrixClient.login(
        baseUrl = baseUrl,
        repositoriesModule = repositoriesModule,
        mediaStoreModule = MediaStoreModule.inMemory(),
        cryptoDriverModule = CryptoDriverModule.vodozemac(),
        authProviderData = MatrixClientAuthProviderData.classicLoginWith(baseUrl) {
            it.register(username, defaultPassword, name)
        }.getOrThrow(),
        configuration = {
            this.name = name
            httpClientEngine = javaHttpClientEngine
            configuration()
        },
    ).getOrThrow()
    client.startSync()
    client.syncState.firstWithTimeout { it == SyncState.RUNNING }
    return StartedClient(client, defaultPassword)
}

suspend fun startClient(
    name: String,
    username: String = name,
    baseUrl: Url,
    repositoriesModule: RepositoriesModule,
    configuration: MatrixClientConfiguration.() -> Unit = {}
): StartedClient {
    val client = MatrixClient.login(
        baseUrl = baseUrl,
        repositoriesModule = repositoriesModule,
        mediaStoreModule = MediaStoreModule.inMemory(),
        cryptoDriverModule = CryptoDriverModule.vodozemac(),
        authProviderData = MatrixClientAuthProviderData.classicLogin(
            baseUrl = baseUrl,
            identifier = IdentifierType.User(username),
            password = defaultPassword,
            deviceId = name,
        ).getOrThrow(),
        configuration = {
            this.name = name
            httpClientEngine = javaHttpClientEngine
            configuration()
        },
    ).getOrThrow()
    client.startSync()
    client.syncState.firstWithTimeout { it == SyncState.RUNNING }
    return StartedClient(client, defaultPassword)
}

suspend fun RoomService.waitForOutboxSent() =
    getOutbox().flatten().firstWithTimeout { outbox -> outbox.all { it.sentAt != null } }

@OptIn(FlowPreview::class)
suspend fun <T> Flow<T>.firstWithTimeout(
    timeout: Duration = 5.seconds,
    predicate: suspend (T) -> Boolean = { true }
): T = channelFlow {
    var currentValue: T? = null
    val timeoutJob = launch {
        delay(timeout)
        close(CancellationException("timed out after $timeout with value $currentValue", null))
    }
    val result = onEach { currentValue = it }.first(predicate)
    timeoutJob.cancel()
    send(result)
}.first()

val clueLog = KotlinLogging.logger("net.folivo.trixnity.client.integrationtests.clues")
inline fun <R> withCluePrintln(clue: Any?, thunk: () -> R): R {
    clueLog.info { ">>> $clue" }
    val result = withClue(clue, thunk)
    clueLog.info { "<<< $clue" }
    return result
}