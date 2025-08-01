package net.folivo.trixnity.client.integrationtests

import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.java.*
import io.ktor.http.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.timeout
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.media.createInMemoryMediaStoreModule
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.client.UIA
import net.folivo.trixnity.clientserverapi.model.authentication.AccountType
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.clientserverapi.model.authentication.Register
import net.folivo.trixnity.clientserverapi.model.uia.AuthenticationRequest
import net.folivo.trixnity.utils.nextString
import org.jetbrains.exposed.sql.Database
import org.koin.core.module.Module
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

const val synapseVersion =
    "v1.130.0" // TODO you should update this from time to time. https://github.com/element-hq/synapse/releases

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
): Result<MatrixClient.LoginInfo> {
    val registerStep = authentication.register(
        password = password,
        username = username,
        deviceId = deviceId,
        accountType = AccountType.USER,
        refreshToken = true,
    ).getOrThrow()
    registerStep.shouldBeInstanceOf<UIA.Step<Register.Response>>()
    val registerResult = registerStep.authenticate(AuthenticationRequest.Dummy).getOrThrow()
    registerResult.shouldBeInstanceOf<UIA.Success<Register.Response>>()
    val (userId, createdDeviceId, accessToken, _, refreshToken) = registerResult.value
    requireNotNull(createdDeviceId)
    requireNotNull(accessToken)
    return Result.success(MatrixClient.LoginInfo(userId, createdDeviceId, accessToken, refreshToken))
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
    repositoriesModule: Module,
    configuration: MatrixClientConfiguration.() -> Unit = {}
): StartedClient {
    val client = MatrixClient.loginWith(
        baseUrl = baseUrl,
        repositoriesModule = repositoriesModule,
        mediaStoreModule = createInMemoryMediaStoreModule(),
        getLoginInfo = { it.register(username, defaultPassword, name) },
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
    repositoriesModule: Module,
    configuration: MatrixClientConfiguration.() -> Unit = {}
): StartedClient {
    val client = MatrixClient.login(
        baseUrl = baseUrl,
        identifier = IdentifierType.User(username),
        password = defaultPassword,
        deviceId = name,
        repositoriesModule = repositoriesModule,
        mediaStoreModule = createInMemoryMediaStoreModule(),
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

suspend fun startClientFromStore(
    name: String,
    repositoriesModule: Module,
    configuration: MatrixClientConfiguration.() -> Unit = {}
): StartedClient {
    val client = MatrixClient.fromStore(
        repositoriesModule = repositoriesModule,
        mediaStoreModule = createInMemoryMediaStoreModule(),
        configuration = {
            this.name = name
            httpClientEngine = javaHttpClientEngine
            configuration()
        },
    ).getOrThrow()
    checkNotNull(client)
    client.startSync()
    client.syncState.firstWithTimeout { it == SyncState.RUNNING }
    return StartedClient(client, defaultPassword)
}

suspend fun RoomService.waitForOutboxSent() =
    getOutbox().flatten().firstWithTimeout { outbox -> outbox.all { it.sentAt != null } }

@OptIn(FlowPreview::class)
suspend fun <T> Flow<T>.firstWithTimeout(timeout: Duration = 5.seconds, predicate: suspend (T) -> Boolean): T =
    timeout(timeout).first(predicate)

@OptIn(FlowPreview::class)
suspend fun <T> Flow<T>.firstWithTimeout(timeout: Duration = 5.seconds): T =
    timeout(timeout).first()