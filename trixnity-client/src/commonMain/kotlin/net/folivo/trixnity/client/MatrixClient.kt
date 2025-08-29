package net.folivo.trixnity.client

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.MatrixClient.*
import net.folivo.trixnity.client.MatrixClient.LoginState.*
import net.folivo.trixnity.client.MatrixClientConfiguration.DeleteRooms
import net.folivo.trixnity.client.media.MediaStore
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.*
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.clientserverapi.model.authentication.LoginType
import net.folivo.trixnity.clientserverapi.model.users.Filters
import net.folivo.trixnity.core.ClientEventEmitter
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.Presence
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.subscribeAsFlow
import net.folivo.trixnity.crypto.sign.SignService
import net.folivo.trixnity.olm.OlmAccount
import net.folivo.trixnity.olm.freeAfter
import net.folivo.trixnity.utils.retry
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.jvm.JvmName
import kotlin.time.Duration

private val log = KotlinLogging.logger("net.folivo.trixnity.client.MatrixClient")

interface MatrixClient : AutoCloseable {
    companion object

    val userId: UserId
    val deviceId: String
    val baseUrl: Url
    val identityKey: Key.Curve25519Key
    val signingKey: Key.Ed25519Key

    val di: Koin

    /**
     * Use this for further access to matrix client-server-API.
     */
    val api: MatrixClientServerApiClient
    val displayName: StateFlow<String?>
    val avatarUrl: StateFlow<String?>

    val serverData: StateFlow<ServerData?>

    val syncState: StateFlow<SyncState>
    val initialSyncDone: StateFlow<Boolean>

    val loginState: StateFlow<LoginState?>

    enum class LoginState {
        LOGGED_IN,
        LOGGED_OUT_SOFT,
        LOGGED_OUT,
        LOCKED,
    }

    data class LoginInfo(
        val userId: UserId,
        val deviceId: String,
        val accessToken: String,
        val refreshToken: String?,
    )

    data class SoftLoginInfo(
        val identifier: IdentifierType,
        val password: String? = null,
        val token: String? = null,
        val loginType: LoginType = LoginType.Password,
    )

    suspend fun logout(): Result<Unit>

    suspend fun clearCache(): Result<Unit>

    suspend fun clearMediaCache(): Result<Unit>

    suspend fun startSync(presence: Presence? = Presence.ONLINE)

    /**
     * Usually used for background sync.
     */
    suspend fun syncOnce(presence: Presence? = Presence.OFFLINE, timeout: Duration = Duration.ZERO): Result<Unit>

    /**
     * Usually used for background sync.
     */
    suspend fun <T> syncOnce(
        presence: Presence? = Presence.OFFLINE,
        timeout: Duration = Duration.ZERO,
        runOnce: suspend (SyncEvents) -> T
    ): Result<T>

    suspend fun stopSync()

    suspend fun cancelSync()

    suspend fun setDisplayName(displayName: String?): Result<Unit>

    suspend fun setAvatarUrl(avatarUrl: String?): Result<Unit>

    suspend fun closeSuspending()
}

private val coroutineExceptionHandler = CoroutineExceptionHandler { _, exception ->
    log.error(exception) { "There was an unexpected exception. This should never happen!!!" }
}

@Deprecated("use login with mediaStoreModuleFactory")
@JvmName("loginDeprecatedMediaStoreFactory")
@Suppress("DEPRECATION")
suspend fun MatrixClient.Companion.login(
    baseUrl: Url,
    identifier: IdentifierType? = null,
    password: String? = null,
    token: String? = null,
    loginType: LoginType = LoginType.Password,
    deviceId: String? = null,
    initialDeviceDisplayName: String? = null,
    repositoriesModuleFactory: suspend (LoginInfo) -> Module,
    mediaStoreFactory: suspend (LoginInfo) -> MediaStore,
    coroutineContext: CoroutineContext = Dispatchers.Default,
    configuration: MatrixClientConfiguration.() -> Unit = {},
): Result<MatrixClient> =
    loginWith(
        baseUrl = baseUrl,
        repositoriesModuleFactory = repositoriesModuleFactory,
        mediaStoreFactory = mediaStoreFactory,
        getLoginInfo = { api ->
            api.authentication.login(
                identifier = identifier,
                password = password,
                token = token,
                type = loginType,
                deviceId = deviceId,
                initialDeviceDisplayName = initialDeviceDisplayName,
            ).map { login ->
                LoginInfo(
                    userId = login.userId,
                    deviceId = login.deviceId,
                    accessToken = login.accessToken,
                    refreshToken = login.refreshToken,
                )
            }
        },
        configuration = configuration,
        coroutineContext = coroutineContext,
    )

suspend fun MatrixClient.Companion.login(
    baseUrl: Url,
    identifier: IdentifierType? = null,
    password: String? = null,
    token: String? = null,
    loginType: LoginType = LoginType.Password,
    deviceId: String? = null,
    initialDeviceDisplayName: String? = null,
    repositoriesModuleFactory: suspend (LoginInfo) -> Module,
    mediaStoreModuleFactory: suspend (LoginInfo) -> Module,
    coroutineContext: CoroutineContext = Dispatchers.Default,
    configuration: MatrixClientConfiguration.() -> Unit = {},
): Result<MatrixClient> =
    loginWith(
        baseUrl = baseUrl,
        repositoriesModuleFactory = repositoriesModuleFactory,
        mediaStoreModuleFactory = mediaStoreModuleFactory,
        getLoginInfo = { api ->
            api.authentication.login(
                identifier = identifier,
                password = password,
                token = token,
                type = loginType,
                deviceId = deviceId,
                initialDeviceDisplayName = initialDeviceDisplayName,
            ).map { login ->
                LoginInfo(
                    userId = login.userId,
                    deviceId = login.deviceId,
                    accessToken = login.accessToken,
                    refreshToken = login.refreshToken,
                )
            }
        },
        configuration = configuration,
        coroutineContext = coroutineContext,
    )


@Deprecated("use login with mediaStoreModule")
@JvmName("loginDeprecatedMediaStore")
@Suppress("DEPRECATION")
suspend fun MatrixClient.Companion.login(
    baseUrl: Url,
    identifier: IdentifierType? = null,
    password: String? = null,
    token: String? = null,
    loginType: LoginType = LoginType.Password,
    deviceId: String? = null,
    initialDeviceDisplayName: String? = null,
    repositoriesModule: Module,
    mediaStore: MediaStore,
    coroutineContext: CoroutineContext = Dispatchers.Default,
    configuration: MatrixClientConfiguration.() -> Unit = {},
): Result<MatrixClient> = login(
    baseUrl = baseUrl,
    identifier = identifier,
    password = password,
    token = token,
    loginType = loginType,
    deviceId = deviceId,
    initialDeviceDisplayName = initialDeviceDisplayName,
    repositoriesModuleFactory = { repositoriesModule },
    mediaStoreFactory = { mediaStore },
    configuration = configuration,
    coroutineContext = coroutineContext,
)

suspend fun MatrixClient.Companion.login(
    baseUrl: Url,
    identifier: IdentifierType? = null,
    password: String? = null,
    token: String? = null,
    loginType: LoginType = LoginType.Password,
    deviceId: String? = null,
    initialDeviceDisplayName: String? = null,
    repositoriesModule: Module,
    mediaStoreModule: Module,
    coroutineContext: CoroutineContext = Dispatchers.Default,
    configuration: MatrixClientConfiguration.() -> Unit = {},
): Result<MatrixClient> = login(
    baseUrl = baseUrl,
    identifier = identifier,
    password = password,
    token = token,
    loginType = loginType,
    deviceId = deviceId,
    initialDeviceDisplayName = initialDeviceDisplayName,
    repositoriesModuleFactory = { repositoriesModule },
    mediaStoreModuleFactory = { mediaStoreModule },
    configuration = configuration,
    coroutineContext = coroutineContext,
)

@Deprecated("use loginWithPassword with mediaStoreModuleFactory")
@JvmName("loginWithPasswordDeprecatedMediaStoreFactory")
@Suppress("DEPRECATION")
suspend fun MatrixClient.Companion.loginWithPassword(
    baseUrl: Url,
    identifier: IdentifierType? = null,
    password: String,
    deviceId: String? = null,
    initialDeviceDisplayName: String? = null,
    repositoriesModuleFactory: suspend (LoginInfo) -> Module,
    mediaStoreFactory: suspend (LoginInfo) -> MediaStore,
    coroutineContext: CoroutineContext = Dispatchers.Default,
    configuration: MatrixClientConfiguration.() -> Unit = {},
): Result<MatrixClient> =
    login(
        baseUrl = baseUrl,
        identifier = identifier,
        password = password,
        token = null,
        loginType = LoginType.Password,
        deviceId = deviceId,
        initialDeviceDisplayName = initialDeviceDisplayName,
        repositoriesModuleFactory = repositoriesModuleFactory,
        mediaStoreFactory = mediaStoreFactory,
        configuration = configuration,
        coroutineContext = coroutineContext,
    )

suspend fun MatrixClient.Companion.loginWithPassword(
    baseUrl: Url,
    identifier: IdentifierType? = null,
    password: String,
    deviceId: String? = null,
    initialDeviceDisplayName: String? = null,
    repositoriesModuleFactory: suspend (LoginInfo) -> Module,
    mediaStoreModuleFactory: suspend (LoginInfo) -> Module,
    coroutineContext: CoroutineContext = Dispatchers.Default,
    configuration: MatrixClientConfiguration.() -> Unit = {},
): Result<MatrixClient> =
    login(
        baseUrl = baseUrl,
        identifier = identifier,
        password = password,
        token = null,
        loginType = LoginType.Password,
        deviceId = deviceId,
        initialDeviceDisplayName = initialDeviceDisplayName,
        repositoriesModuleFactory = repositoriesModuleFactory,
        mediaStoreModuleFactory = mediaStoreModuleFactory,
        configuration = configuration,
        coroutineContext = coroutineContext,
    )

@Deprecated("use loginWithPassword with mediaStoreModule")
@JvmName("loginWithPasswordDeprecatedMediaStore")
@Suppress("DEPRECATION")
suspend fun MatrixClient.Companion.loginWithPassword(
    baseUrl: Url,
    identifier: IdentifierType? = null,
    password: String,
    deviceId: String? = null,
    initialDeviceDisplayName: String? = null,
    repositoriesModule: Module,
    mediaStore: MediaStore,
    coroutineContext: CoroutineContext = Dispatchers.Default,
    configuration: MatrixClientConfiguration.() -> Unit = {},
): Result<MatrixClient> = loginWithPassword(
    baseUrl = baseUrl,
    identifier = identifier,
    password = password,
    deviceId = deviceId,
    initialDeviceDisplayName = initialDeviceDisplayName,
    repositoriesModuleFactory = { repositoriesModule },
    mediaStoreFactory = { mediaStore },
    configuration = configuration,
    coroutineContext = coroutineContext,
)

suspend fun MatrixClient.Companion.loginWithPassword(
    baseUrl: Url,
    identifier: IdentifierType? = null,
    password: String,
    deviceId: String? = null,
    initialDeviceDisplayName: String? = null,
    repositoriesModule: Module,
    mediaStoreModule: Module,
    coroutineContext: CoroutineContext = Dispatchers.Default,
    configuration: MatrixClientConfiguration.() -> Unit = {},
): Result<MatrixClient> = loginWithPassword(
    baseUrl = baseUrl,
    identifier = identifier,
    password = password,
    deviceId = deviceId,
    initialDeviceDisplayName = initialDeviceDisplayName,
    repositoriesModuleFactory = { repositoriesModule },
    mediaStoreModuleFactory = { mediaStoreModule },
    configuration = configuration,
    coroutineContext = coroutineContext,
)

@Deprecated("use loginWithToken with mediaStoreModuleFactory")
@JvmName("loginWithTokenDeprecatedMediaStoreFactory")
@Suppress("DEPRECATION")
suspend fun MatrixClient.Companion.loginWithToken(
    baseUrl: Url,
    identifier: IdentifierType? = null,
    token: String,
    deviceId: String? = null,
    initialDeviceDisplayName: String? = null,
    repositoriesModuleFactory: suspend (LoginInfo) -> Module,
    mediaStoreFactory: suspend (LoginInfo) -> MediaStore,
    coroutineContext: CoroutineContext = Dispatchers.Default,
    configuration: MatrixClientConfiguration.() -> Unit = {},
): Result<MatrixClient> =
    login(
        baseUrl = baseUrl,
        identifier = identifier,
        password = null,
        token = token,
        loginType = LoginType.Token(),
        deviceId = deviceId,
        initialDeviceDisplayName = initialDeviceDisplayName,
        repositoriesModuleFactory = repositoriesModuleFactory,
        mediaStoreFactory = mediaStoreFactory,
        configuration = configuration,
        coroutineContext = coroutineContext,
    )

suspend fun MatrixClient.Companion.loginWithToken(
    baseUrl: Url,
    identifier: IdentifierType? = null,
    token: String,
    deviceId: String? = null,
    initialDeviceDisplayName: String? = null,
    repositoriesModuleFactory: suspend (LoginInfo) -> Module,
    mediaStoreModuleFactory: suspend (LoginInfo) -> Module,
    coroutineContext: CoroutineContext = Dispatchers.Default,
    configuration: MatrixClientConfiguration.() -> Unit = {},
): Result<MatrixClient> =
    login(
        baseUrl = baseUrl,
        identifier = identifier,
        password = null,
        token = token,
        loginType = LoginType.Token(),
        deviceId = deviceId,
        initialDeviceDisplayName = initialDeviceDisplayName,
        repositoriesModuleFactory = repositoriesModuleFactory,
        mediaStoreModuleFactory = mediaStoreModuleFactory,
        configuration = configuration,
        coroutineContext = coroutineContext,
    )

@Deprecated("use loginWithToken with mediaStoreModule")
@JvmName("loginWithWithTokenDeprecatedMediaStore")
@Suppress("DEPRECATION")
suspend fun MatrixClient.Companion.loginWithToken(
    baseUrl: Url,
    identifier: IdentifierType? = null,
    token: String,
    deviceId: String? = null,
    initialDeviceDisplayName: String? = null,
    repositoriesModule: Module,
    mediaStore: MediaStore,
    coroutineContext: CoroutineContext = Dispatchers.Default,
    configuration: MatrixClientConfiguration.() -> Unit = {},
): Result<MatrixClient> = loginWithToken(
    baseUrl = baseUrl,
    identifier = identifier,
    token = token,
    deviceId = deviceId,
    initialDeviceDisplayName = initialDeviceDisplayName,
    repositoriesModuleFactory = { repositoriesModule },
    mediaStoreFactory = { mediaStore },
    configuration = configuration,
    coroutineContext = coroutineContext,
)

suspend fun MatrixClient.Companion.loginWithToken(
    baseUrl: Url,
    identifier: IdentifierType? = null,
    token: String,
    deviceId: String? = null,
    initialDeviceDisplayName: String? = null,
    repositoriesModule: Module,
    mediaStoreModule: Module,
    coroutineContext: CoroutineContext = Dispatchers.Default,
    configuration: MatrixClientConfiguration.() -> Unit = {},
): Result<MatrixClient> = loginWithToken(
    baseUrl = baseUrl,
    identifier = identifier,
    token = token,
    deviceId = deviceId,
    initialDeviceDisplayName = initialDeviceDisplayName,
    repositoriesModuleFactory = { repositoriesModule },
    mediaStoreModuleFactory = { mediaStoreModule },
    configuration = configuration,
    coroutineContext = coroutineContext,
)

@Deprecated("use loginWith with mediaStoreModuleFactory")
@JvmName("loginWithDeprecatedMediaStoreFactory")
suspend fun MatrixClient.Companion.loginWith(
    baseUrl: Url,
    repositoriesModuleFactory: suspend (LoginInfo) -> Module,
    mediaStoreFactory: suspend (LoginInfo) -> MediaStore,
    getLoginInfo: suspend (MatrixClientServerApiClient) -> Result<LoginInfo>,
    coroutineContext: CoroutineContext = Dispatchers.Default,
    configuration: MatrixClientConfiguration.() -> Unit = {},
): Result<MatrixClient> = loginWith(
    baseUrl = baseUrl,
    repositoriesModuleFactory = repositoriesModuleFactory,
    mediaStoreModuleFactory = {
        val mediaStore = mediaStoreFactory(it)
        module { single<MediaStore> { mediaStore } }
    },
    getLoginInfo = getLoginInfo,
    coroutineContext = coroutineContext,
    configuration = configuration,
)

suspend fun MatrixClient.Companion.loginWith(
    baseUrl: Url,
    repositoriesModuleFactory: suspend (LoginInfo) -> Module,
    mediaStoreModuleFactory: suspend (LoginInfo) -> Module,
    getLoginInfo: suspend (MatrixClientServerApiClient) -> Result<LoginInfo>,
    coroutineContext: CoroutineContext = Dispatchers.Default,
    configuration: MatrixClientConfiguration.() -> Unit = {},
): Result<MatrixClient> = kotlin.runCatching {
    val config = MatrixClientConfiguration().apply(configuration)
    val finalCoroutineContext = (config.name?.let { CoroutineName(it) } ?: EmptyCoroutineContext) + coroutineContext

    val loginInfo = config.matrixClientServerApiClientFactory.create(
        baseUrl = baseUrl,
        httpClientEngine = config.httpClientEngine,
        httpClientConfig = config.httpClientConfig,
    ).use { loginApi ->
        getLoginInfo(loginApi).getOrThrow()
    }

    val (displayName, avatarUrl) = config.matrixClientServerApiClientFactory.create(
        baseUrl = baseUrl,
        authProvider = MatrixAuthProvider.classicInMemory(
            accessToken = loginInfo.accessToken,
            refreshToken = loginInfo.refreshToken
        ),
        httpClientEngine = config.httpClientEngine,
        httpClientConfig = config.httpClientConfig,
        coroutineContext = finalCoroutineContext,
    ).use { loginApi ->
        loginApi.user.getProfile(loginInfo.userId).getOrThrow()
    }

    val mediaStoreModule = mediaStoreModuleFactory(loginInfo)
    val repositoriesModule = repositoriesModuleFactory(loginInfo)

    val koinApplication = initMatrixClientKoinApplication(
        repositoriesModule = repositoriesModule,
        mediaStoreModule = mediaStoreModule,
        coroutineContext = finalCoroutineContext,
        config = config
    )
    val di = koinApplication.koin

    val accountStore = di.get<AccountStore>()
    val olmPickleKey = ""

    accountStore.updateAccount {
        Account(
            olmPickleKey = olmPickleKey,
            baseUrl = baseUrl.toString(),
            accessToken = loginInfo.accessToken,
            refreshToken = loginInfo.refreshToken,
            userId = loginInfo.userId,
            deviceId = loginInfo.deviceId,
            displayName = displayName,
            avatarUrl = avatarUrl,
            backgroundFilterId = null,
            filterId = null,
            syncBatchToken = null,
            isLocked = false,
        )
    }

    koinApplication.createMatrixClient(
        baseUrl = baseUrl,
        userId = loginInfo.userId,
        deviceId = loginInfo.deviceId,
        olmPickleKey = olmPickleKey,
        json = di.get(),
        eventContentSerializerMappings = di.get(),
        accountStore = accountStore,
        olmCryptoStore = di.get(),
        coroutineContext = finalCoroutineContext,
        config = config
    ) { matrixClient ->
        val keyStore = di.get<KeyStore>()

        val selfSignedDeviceKeys = di.get<SignService>().getSelfSignedDeviceKeys()
        selfSignedDeviceKeys.signed.keys.forEach {
            keyStore.saveKeyVerificationState(it, KeyVerificationState.Verified(it.value.value))
        }
        matrixClient.api.key.setKeys(deviceKeys = selfSignedDeviceKeys).getOrThrow()
        keyStore.updateOutdatedKeys { it + loginInfo.userId }
        matrixClient.also {
            log.trace { "finished creating MatrixClient" }
        }
    }
}

suspend fun MatrixClient.Companion.loginWith(
    baseUrl: Url,
    repositoriesModule: Module,
    mediaStoreModule: Module,
    getLoginInfo: suspend (MatrixClientServerApiClient) -> Result<LoginInfo>,
    coroutineContext: CoroutineContext = Dispatchers.Default,
    configuration: MatrixClientConfiguration.() -> Unit = {},
): Result<MatrixClient> = loginWith(
    baseUrl = baseUrl,
    repositoriesModuleFactory = { repositoriesModule },
    mediaStoreModuleFactory = { mediaStoreModule },
    getLoginInfo = getLoginInfo,
    configuration = configuration,
    coroutineContext = coroutineContext,
)

@Deprecated("use loginWithToken with mediaStoreModule")
@JvmName("fromStoreDeprecatedMediaStore")
suspend fun MatrixClient.Companion.fromStore(
    repositoriesModule: Module,
    mediaStore: MediaStore,
    onSoftLogin: (suspend () -> SoftLoginInfo)? = null,
    coroutineContext: CoroutineContext = Dispatchers.Default,
    configuration: MatrixClientConfiguration.() -> Unit = {},
): Result<MatrixClient?> =
    fromStore(
        repositoriesModule = repositoriesModule,
        mediaStoreModule = module { single<MediaStore> { mediaStore } },
        onSoftLogin = onSoftLogin,
        coroutineContext = coroutineContext,
        configuration = configuration,
    )

suspend fun MatrixClient.Companion.fromStore(
    repositoriesModule: Module,
    mediaStoreModule: Module,
    onSoftLogin: (suspend () -> SoftLoginInfo)? = null,
    coroutineContext: CoroutineContext = Dispatchers.Default,
    configuration: MatrixClientConfiguration.() -> Unit = {},
): Result<MatrixClient?> = kotlin.runCatching {
    val config = MatrixClientConfiguration().apply(configuration)

    val koinApplication = initMatrixClientKoinApplication(
        repositoriesModule = repositoriesModule,
        mediaStoreModule = mediaStoreModule,
        coroutineContext = coroutineContext,
        config = config
    )
    val di = koinApplication.koin

    val accountStore = di.get<AccountStore>()
    val olmCryptoStore = di.get<OlmCryptoStore>()

    val account = accountStore.getAccount()
    val baseUrl = account?.baseUrl?.let { Url(it) }
    val userId = account?.userId
    val deviceId = account?.deviceId
    val olmPickleKey = account?.olmPickleKey
    val olmAccount = olmCryptoStore.getOlmAccount()

    if (olmPickleKey != null && userId != null && deviceId != null && baseUrl != null && olmAccount != null) {
        koinApplication.createMatrixClient(
            baseUrl = baseUrl,
            userId = userId,
            deviceId = deviceId,
            olmPickleKey = olmPickleKey,
            json = di.get(),
            eventContentSerializerMappings = di.get(),
            accountStore = accountStore,
            olmCryptoStore = olmCryptoStore,
            config = config,
            coroutineContext = coroutineContext,
        ) { matrixClient ->
            val accessToken = account.accessToken ?: onSoftLogin?.let {
                val (identifier, password, token, loginType) = onSoftLogin()
                matrixClient.api.authentication.login(
                    identifier = identifier,
                    password = password,
                    token = token,
                    type = loginType,
                    deviceId = deviceId,
                ).getOrThrow().accessToken
                    .also { accountStore.updateAccount { account -> account?.copy(accessToken = it) } }
            }
            if (accessToken != null) matrixClient
            else null
        }
    } else null
}

private suspend fun initMatrixClientKoinApplication(
    repositoriesModule: Module,
    mediaStoreModule: Module,
    coroutineContext: CoroutineContext,
    config: MatrixClientConfiguration
): KoinApplication {
    val coroutineScope =
        CoroutineScope(coroutineContext + SupervisorJob(coroutineContext[Job]) + coroutineExceptionHandler)

    val koinApplication = koinApplication {
        modules(module {
            single { coroutineScope }
            single { config }
        })
        modules(repositoriesModule)
        modules(mediaStoreModule)
        @Suppress("DEPRECATION")
        modules(config.modules ?: config.modulesFactory?.invoke() ?: config.modulesFactories.map { it.invoke() })
    }
    val di = koinApplication.koin
    val rootStore = di.get<RootStore>()
    rootStore.init(di.get())
    return koinApplication
}

private suspend fun <T : MatrixClient?> KoinApplication.createMatrixClient(
    baseUrl: Url,
    userId: UserId,
    deviceId: String,
    olmPickleKey: String,
    json: Json,
    eventContentSerializerMappings: EventContentSerializerMappings,
    accountStore: AccountStore,
    olmCryptoStore: OlmCryptoStore,
    config: MatrixClientConfiguration,
    coroutineContext: CoroutineContext,
    doFinally: suspend (MatrixClient) -> T,
): T {
    val api = config.matrixClientServerApiClientFactory.create(
        baseUrl = baseUrl,
        authProvider = MatrixAuthProvider.classic(AccountStoreBearerAccessTokenStore(accountStore)) {
            onLogout(it, accountStore)
        },
        httpClientEngine = config.httpClientEngine,
        httpClientConfig = config.httpClientConfig,
        json = json,
        eventContentSerializerMappings = eventContentSerializerMappings,
        syncBatchTokenStore = object : SyncBatchTokenStore {
            override suspend fun getSyncBatchToken(): String? =
                accountStore.getAccount()?.syncBatchToken

            override suspend fun setSyncBatchToken(token: String) {
                accountStore.updateAccount { if (it?.accessToken != null) it.copy(syncBatchToken = token) else it }
            }
        },
        syncErrorDelayConfig = config.syncErrorDelayConfig,
        coroutineContext = coroutineContext,
    )
    val (signingKey, identityKey) = freeAfter(
        olmCryptoStore.getOlmAccount()
            ?.let { OlmAccount.unpickle(olmPickleKey, it) }
            ?: OlmAccount.create()
                .also { olmAccount -> olmCryptoStore.updateOlmAccount { olmAccount.pickle(olmPickleKey) } }
    ) {
        Key.Ed25519Key(deviceId, it.identityKeys.ed25519) to
                Key.Curve25519Key(deviceId, it.identityKeys.curve25519)
    }
    modules(module {
        single { UserInfo(userId, deviceId, signingKey, identityKey) }
        single<MatrixClientServerApiClient> { api }
        single { CurrentSyncState(api.sync.currentSyncState) }
    })
    val matrixClient = MatrixClientImpl(baseUrl, koin)
    try {
        return doFinally(matrixClient)
    } catch (e: Exception) {
        matrixClient.logout()
        matrixClient.deleteAll()
        matrixClient.close()
        throw e
    }
}

private class AccountStoreBearerAccessTokenStore(
    private val accountStore: AccountStore
) : ClassicMatrixAuthProvider.BearerTokensStore {
    override suspend fun getBearerTokens(): ClassicMatrixAuthProvider.BearerTokens? {
        val currentAccount = accountStore.getAccount()
        return if (currentAccount?.accessToken != null)
            ClassicMatrixAuthProvider.BearerTokens(
                accessToken = currentAccount.accessToken,
                refreshToken = currentAccount.refreshToken
            )
        else null
    }

    override suspend fun setBearerTokens(bearerTokens: ClassicMatrixAuthProvider.BearerTokens) {
        accountStore.updateAccount {
            it?.copy(accessToken = bearerTokens.accessToken, refreshToken = bearerTokens.refreshToken)
        }
    }
}

private suspend fun onLogout(
    logoutInfo: LogoutInfo,
    accountStore: AccountStore
) {
    log.debug { "This device has been logged out ($logoutInfo)." }
    withContext(NonCancellable) {
        accountStore.updateAccount {
            it?.copy(
                accessToken = if (logoutInfo.isLocked) it.accessToken else null,
                refreshToken = if (logoutInfo.isLocked) it.refreshToken else null,
                syncBatchToken = if (logoutInfo.isSoft) it.syncBatchToken else null,
                isLocked = logoutInfo.isLocked
            )
        }
    }
}

class MatrixClientImpl internal constructor(
    override val baseUrl: Url,
    override val di: Koin,
) : MatrixClient {
    private val coroutineScope: CoroutineScope = di.get()
    private val rootStore: RootStore = di.get()
    private val accountStore: AccountStore = di.get()
    private val mediaStore: MediaStore = di.get()
    private val mediaCacheMappingStore: MediaCacheMappingStore = di.get()
    private val eventHandlers: List<EventHandler> = di.getAll()
    private val config: MatrixClientConfiguration = di.get()
    override val api: MatrixClientServerApiClient = di.get()
    private val userInfo: UserInfo = di.get()
    override val userId: UserId = userInfo.userId
    override val deviceId: String = userInfo.deviceId
    override val identityKey: Key.Curve25519Key = userInfo.identityPublicKey
    override val signingKey: Key.Ed25519Key = userInfo.signingPublicKey

    val started: MatrixClientStarted = di.get()

    override val displayName: StateFlow<String?> = accountStore.getAccountAsFlow().map { it?.displayName }
        .stateIn(coroutineScope, Eagerly, null)
    override val avatarUrl: StateFlow<String?> = accountStore.getAccountAsFlow().map { it?.avatarUrl }
        .stateIn(coroutineScope, Eagerly, null)
    override val serverData: StateFlow<ServerData?> = di.get<ServerDataStore>().getServerDataFlow()
        .stateIn(coroutineScope, Eagerly, null)

    override val syncState = api.sync.currentSyncState

    override val initialSyncDone: StateFlow<Boolean> =
        accountStore.getAccountAsFlow().map { it?.syncBatchToken }
            .map { token -> token != null }
            .stateIn(coroutineScope, Eagerly, true)

    override val loginState: StateFlow<LoginState?> =
        accountStore.getAccountAsFlow().map { account ->
            when {
                account?.isLocked == true -> LOCKED
                account?.accessToken != null -> LOGGED_IN
                account?.syncBatchToken != null -> LOGGED_OUT_SOFT
                else -> LOGGED_OUT
            }
        }.stateIn(coroutineScope, Eagerly, null)

    init {
        coroutineScope.launch {
            val allHandlersStarted = MutableStateFlow(false)
            launch {
                eventHandlers.forEach {
                    log.debug { "start EventHandler: ${it::class.simpleName}" }
                    it.startInCoroutineScope(this)
                }
                allHandlersStarted.value = true
            }
            allHandlersStarted.first { it }
            log.debug { "all EventHandler started" }
            launch {
                loginState.collectLatest {
                    log.info { "login state: $it" }
                    when (it) {
                        LOGGED_OUT_SOFT -> {
                            log.info { "stop sync" }
                            stopSync()
                        }

                        LOGGED_OUT -> {
                            log.info { "stop sync and delete all" }
                            stopSync()
                            rootStore.deleteAll()
                        }

                        LOCKED -> {
                            log.info { "account is locked - waiting for successful sync for unlocking" }
                            api.sync.subscribeAsFlow(ClientEventEmitter.Priority.FIRST).first()
                            accountStore.updateAccount { it?.copy(isLocked = false) }
                            log.info { "account unlocked" }
                        }

                        else -> {}
                    }
                }
            }

            val filterId = accountStore.getAccount()?.filterId
            if (filterId == null) {
                val newFilterId = retry(
                    onError = { error, delay -> log.warn(error) { "could not set filter, retry again in $delay" } }
                ) {
                    val filter = config.syncFilter.applyDefaultFilter()
                    api.user.setFilter(userId, filter)
                        .getOrThrow().also { log.debug { "set new filter for sync with id $it: $filter" } }
                }
                accountStore.updateAccount { it?.copy(filterId = newFilterId) }
            }
            val backgroundFilterId = accountStore.getAccount()?.backgroundFilterId
            if (backgroundFilterId == null) {
                val newFilterId = retry(
                    onError = { error, delay -> log.warn(error) { "could not set background filter, retry again in $delay" } }
                ) {
                    val filter = config.syncOnceFilter.applyDefaultFilter()
                    api.user.setFilter(userId, filter)
                        .getOrThrow().also { log.debug { "set new background filter for sync with id $it: $filter" } }
                }
                accountStore.updateAccount { it?.copy(backgroundFilterId = newFilterId) }
            }
            started.delegate.value = true
        }
    }

    private fun Filters.applyDefaultFilter(): Filters {
        val mappings = di.get<EventContentSerializerMappings>()
        return copy(
            accountData = (accountData ?: Filters.EventFilter()).copy(
                types = mappings.globalAccountData.map { it.type }.toSet(),
            ),
            room = (room ?: Filters.RoomFilter()).copy(
                accountData = (room?.accountData ?: Filters.RoomFilter.RoomEventFilter()).copy(
                    types = mappings.roomAccountData.map { it.type }.toSet(),
                ),
                ephemeral = (room?.ephemeral ?: Filters.RoomFilter.RoomEventFilter()).copy(
                    types = mappings.ephemeral.map { it.type }.toSet(),
                ),
                state = (room?.state ?: Filters.RoomFilter.RoomEventFilter()).copy(
                    lazyLoadMembers = true,
                    types = mappings.state.map { it.type }.toSet(),
                ),
                timeline = (room?.timeline ?: Filters.RoomFilter.RoomEventFilter()).copy(
                    types = (mappings.message + mappings.state).map { it.type }.toSet(),
                ),
                includeLeave = config.deleteRooms !is DeleteRooms.OnLeave,
            )
        )
    }

    override suspend fun logout(): Result<Unit> {
        cancelSync()
        return if (loginState.value == LOGGED_OUT_SOFT) {
            deleteAll()
            Result.success(Unit)
        } else api.authentication.logout()
            .mapCatching {
                deleteAll()
            }
    }

    internal suspend fun deleteAll() {
        rootStore.deleteAll()
    }

    /**
     * Be aware, that most StateFlows you got before will not be updated after calling this method.
     */
    override suspend fun clearCache(): Result<Unit> = kotlin.runCatching {
        stopSync()
        accountStore.updateAccount { it?.copy(syncBatchToken = null) }
        rootStore.clearCache()
        startSync()
    }

    override suspend fun clearMediaCache(): Result<Unit> = kotlin.runCatching {
        stopSync()
        mediaCacheMappingStore.clearCache()
        mediaStore.clearCache()
        startSync()
    }

    override suspend fun startSync(presence: Presence?) {
        started.first { it }
        api.sync.start(
            timeout = config.syncLoopTimeout,
            filter = checkNotNull(accountStore.getAccount()?.filterId),
            setPresence = presence,
        )
    }

    override suspend fun syncOnce(presence: Presence?, timeout: Duration): Result<Unit> =
        syncOnce(presence = presence, timeout = timeout) { }

    override suspend fun <T> syncOnce(
        presence: Presence?,
        timeout: Duration,
        runOnce: suspend (SyncEvents) -> T,
    ): Result<T> {
        started.first { it }
        return api.sync.startOnce(
            filter = checkNotNull(accountStore.getAccount()?.backgroundFilterId),
            setPresence = presence,
            timeout = timeout,
            runOnce = runOnce
        )
    }

    override suspend fun stopSync() {
        api.sync.stop()
    }

    override suspend fun cancelSync() {
        api.sync.cancel()
    }

    override fun close() {
        started.delegate.value = false
        api.close()
        di.close()
        coroutineScope.cancel("stopped MatrixClient")
    }

    override suspend fun closeSuspending() {
        val job = coroutineScope.coroutineContext.job
        close()
        job.join()
    }

    override suspend fun setDisplayName(displayName: String?): Result<Unit> {
        return api.user.setDisplayName(userId, displayName).map {
            accountStore.updateAccount { it?.copy(displayName = displayName) }
        }
    }

    override suspend fun setAvatarUrl(avatarUrl: String?): Result<Unit> {
        return api.user.setAvatarUrl(userId, avatarUrl).map {
            accountStore.updateAccount { it?.copy(avatarUrl = avatarUrl) }
        }
    }
}