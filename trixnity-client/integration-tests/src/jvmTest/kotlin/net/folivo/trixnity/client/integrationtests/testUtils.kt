package net.folivo.trixnity.client.integrationtests

import com.benasher44.uuid.uuid4
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.*
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.plus
import net.folivo.trixnity.client.IMatrixClient
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.store.exposed.ExposedStoreFactory
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.client.UIA
import net.folivo.trixnity.clientserverapi.model.authentication.AccountType
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.clientserverapi.model.authentication.Register
import net.folivo.trixnity.clientserverapi.model.uia.AuthenticationRequest
import org.jetbrains.exposed.sql.Database

const val synapseVersion =
    "v1.60.0" // TODO you should update this from time to time. https://github.com/matrix-org/synapse/releases
private const val password = "user$1passw0rd"

suspend fun MatrixClientServerApiClient.register(
    username: String? = null,
    password: String,
    deviceId: String? = null
): Result<IMatrixClient.LoginInfo> {
    val registerStep = authentication.register(
        password = password,
        username = username,
        deviceId = deviceId,
        accountType = AccountType.USER,
    ).getOrThrow()
    registerStep.shouldBeInstanceOf<UIA.Step<Register.Response>>()
    val registerResult = registerStep.authenticate(AuthenticationRequest.Dummy).getOrThrow()
    registerResult.shouldBeInstanceOf<UIA.Success<Register.Response>>()
    val (userId, createdDeviceId, accessToken) = registerResult.value
    requireNotNull(createdDeviceId)
    requireNotNull(accessToken)
    return Result.success(IMatrixClient.LoginInfo(userId, createdDeviceId, accessToken, "displayName", null))
}

fun newDatabase() = Database.connect("jdbc:h2:mem:${uuid4()};DB_CLOSE_DELAY=-1;")

data class StartedClient(
    val scope: CoroutineScope,
    val database: Database,
    val client: IMatrixClient,
    val password: String
)

suspend fun registerAndStartClient(name: String, username: String = name, baseUrl: Url): StartedClient {
    val scope = CoroutineScope(Dispatchers.Default) + CoroutineName(name)
    val database = newDatabase()
    val storeFactory = ExposedStoreFactory(database, Dispatchers.IO, scope)

    val client = MatrixClient.loginWith(
        baseUrl = baseUrl,
        storeFactory = storeFactory,
        scope = scope,
        getLoginInfo = { it.register(username, password, name) }
    ).getOrThrow()
    client.startSync()
    client.syncState.first { it == SyncState.RUNNING }
    return StartedClient(scope, database, client, password)
}

suspend fun startClient(name: String, username: String = name, baseUrl: Url): StartedClient {
    val scope = CoroutineScope(Dispatchers.Default) + CoroutineName(name)
    val database = newDatabase()
    val storeFactory = ExposedStoreFactory(database, Dispatchers.IO, scope)

    val client = MatrixClient.login(
        baseUrl = baseUrl,
        identifier = IdentifierType.User(username),
        passwordOrToken = password,
        deviceId = name,
        storeFactory = storeFactory,
        scope = scope,
    ).getOrThrow()
    client.startSync()
    client.syncState.first { it == SyncState.RUNNING }
    return StartedClient(scope, database, client, password)
}
