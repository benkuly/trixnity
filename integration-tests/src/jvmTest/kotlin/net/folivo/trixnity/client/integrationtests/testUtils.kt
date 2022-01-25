package net.folivo.trixnity.client.integrationtests

import com.benasher44.uuid.uuid4
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.*
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.plus
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.SyncApiClient
import net.folivo.trixnity.client.api.UIA
import net.folivo.trixnity.client.api.model.authentication.AccountType
import net.folivo.trixnity.client.api.model.authentication.RegisterResponse
import net.folivo.trixnity.client.api.model.uia.AuthenticationRequest
import net.folivo.trixnity.client.store.exposed.ExposedStoreFactory
import org.jetbrains.exposed.sql.Database

const val synapseVersion = "v1.51.0" // TODO you should update this from time to time.

suspend fun MatrixApiClient.register(
    username: String? = null,
    password: String
): Result<MatrixClient.Companion.LoginInfo> {
    val registerStep = authentication.register(
        password = password,
        username = username,
        accountType = AccountType.USER,
    ).getOrThrow()
    registerStep.shouldBeInstanceOf<UIA.UIAStep<RegisterResponse>>()
    val registerResult = registerStep.authenticate(AuthenticationRequest.Dummy).getOrThrow()
    registerResult.shouldBeInstanceOf<UIA.UIASuccess<RegisterResponse>>()
    val (userId, deviceId, accessToken) = registerResult.value
    requireNotNull(deviceId)
    requireNotNull(accessToken)
    return Result.success(MatrixClient.Companion.LoginInfo(userId, deviceId, accessToken, "displayName", null))
}

fun newDatabase() = Database.connect("jdbc:h2:mem:${uuid4()};DB_CLOSE_DELAY=-1;")

data class StartedClient(
    val scope: CoroutineScope,
    val database: Database,
    val client: MatrixClient,
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
        getLoginInfo = { it.register(username, "user$1passw0rd") }
    ).getOrThrow()
    client.startSync()
    client.syncState.first { it == SyncApiClient.SyncState.RUNNING }
    return StartedClient(scope, database, client, "user$1passw0rd")
}
