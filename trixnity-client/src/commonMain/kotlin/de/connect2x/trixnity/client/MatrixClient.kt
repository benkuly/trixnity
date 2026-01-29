package de.connect2x.trixnity.client

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.lognity.api.logger.error
import de.connect2x.lognity.api.logger.warn
import de.connect2x.trixnity.client.MatrixClient.LoginState
import de.connect2x.trixnity.client.MatrixClient.LoginState.*
import de.connect2x.trixnity.client.MatrixClientConfiguration.DeleteRooms
import de.connect2x.trixnity.client.media.MediaStore
import de.connect2x.trixnity.client.store.*
import de.connect2x.trixnity.client.store.repository.RepositoryMigration
import de.connect2x.trixnity.clientserverapi.client.*
import de.connect2x.trixnity.clientserverapi.model.user.Filters
import de.connect2x.trixnity.clientserverapi.model.user.Profile
import de.connect2x.trixnity.clientserverapi.model.user.ProfileField
import de.connect2x.trixnity.core.*
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.Presence
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.crypto.driver.CryptoDriver
import de.connect2x.trixnity.crypto.driver.useAll
import de.connect2x.trixnity.crypto.of
import de.connect2x.trixnity.crypto.sign.SignService
import de.connect2x.trixnity.utils.retry
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration

private val log = Logger("de.connect2x.trixnity.client.MatrixClient")

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
    val profile: StateFlow<Profile?>

    val serverData: StateFlow<ServerData?>

    val syncState: StateFlow<SyncState>
    val initialSyncDone: StateFlow<Boolean>

    val loginState: StateFlow<LoginState?>
    val started: StateFlow<Boolean>

    enum class LoginState {
        LOGGED_IN,
        LOGGED_OUT_SOFT,
        LOGGED_OUT,
        LOCKED,
    }

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

    suspend fun setProfileField(profileField: ProfileField): Result<Unit>

    suspend fun closeSuspending()
}

fun interface RepositoriesModule {
    suspend fun create(): Module

    companion object
}

fun interface MediaStoreModule {
    suspend fun create(): Module

    companion object
}

fun interface CryptoDriverModule {
    suspend fun create(): Module

    companion object
}

suspend fun MatrixClient.Companion.create(
    repositoriesModule: RepositoriesModule,
    mediaStoreModule: MediaStoreModule,
    cryptoDriverModule: CryptoDriverModule,
    authProviderData: MatrixClientAuthProviderData? = null,
    coroutineContext: CoroutineContext = Dispatchers.Default,
    configuration: MatrixClientConfiguration.() -> Unit = {},
): Result<MatrixClient> = kotlin.runCatching {
    val config = MatrixClientConfiguration().apply(configuration)
    val finalCoroutineContext = (config.name?.let { CoroutineName(it) } ?: EmptyCoroutineContext) + coroutineContext
    val coroutineScope =
        CoroutineScope(finalCoroutineContext + SupervisorJob(coroutineContext[Job]) + coroutineExceptionHandler)

    try {
        val extraModules = listOf(
            repositoriesModule.create(),
            mediaStoreModule.create(),
            cryptoDriverModule.create(),
        )
        val koinApplication = koinApplication {
            modules(module {
                single { coroutineScope }
                single { config }
            })
            modules(extraModules)
            modules(config.modulesFactories.map { it.invoke() })
        }
        val di = koinApplication.koin

        runMigrationsAndInitStores(di)

        val authenticationStore = di.get<AuthenticationStore>()
        val accountStore = di.get<AccountStore>()

        val userId: UserId
        val deviceId: String
        val initialAccount = accountStore.getAccount()
        val initialAuthentication = authenticationStore.getAuthentication()
        val isFirstInit = initialAccount == null

        val authProviderDataStore =
            AuthenticationStoreMatrixClientAuthProviderDataStore(
                di.get<MatrixClientAuthProviderDataSerializerMappings>(),
                authenticationStore
            )

        if (isFirstInit) {
            requireNotNull(authProviderData) { "authProviderData must not be null when repositories are empty." }
            val whoAmI = authProviderData.useApi(
                coroutineContext = finalCoroutineContext,
                httpClientEngine = config.httpClientEngine,
                httpClientConfig = config.httpClientConfig,
            ) {
                it.authentication.whoAmI().getOrThrow()
            }
            userId = whoAmI.userId
            deviceId = checkNotNull(whoAmI.deviceId) { "deviceId in whoAmI response must not be null" }
        } else {
            userId = initialAccount.userId
            deviceId = initialAccount.deviceId
        }

        @Suppress("DEPRECATION")
        val finalAuthProviderData =
            when {
                initialAccount?.accessToken != null && initialAccount.baseUrl != null -> {
                    log.debug { "migrate to new authentication system" }
                    accountStore.updateAccount { it?.copy(baseUrl = null, accessToken = null, refreshToken = null) }
                    ClassicMatrixClientAuthProviderData(
                        baseUrl = Url(initialAccount.baseUrl),
                        accessToken = initialAccount.accessToken,
                        accessTokenExpiresInMs = null,
                        refreshToken = initialAccount.refreshToken
                    ).also { authProviderDataStore.setAuthData(it) }
                }

                initialAuthentication != null && authProviderData != null -> {
                    log.debug { "re-authenticate" }
                    val (newUserId, newDeviceId) = authProviderData.useApi(
                        coroutineContext = finalCoroutineContext,
                        httpClientEngine = config.httpClientEngine,
                        httpClientConfig = config.httpClientConfig,
                    ) {
                        it.authentication.whoAmI().getOrThrow()
                    }
                    require(newUserId != userId || newDeviceId != deviceId) {
                        "newly authenticated userId ($newUserId) and deviceId ($newDeviceId) " +
                                "must match stored authenticated userId ($userId) and deviceId ($deviceId). "
                    }
                    authProviderDataStore.setAuthData(authProviderData)
                    authProviderData
                }

                isFirstInit && authProviderData != null -> {
                    authProviderDataStore.setAuthData(authProviderData)
                    authProviderData
                }

                else -> checkNotNull(authProviderDataStore.getAuthData()) { "No stored authProviderData found, you need to provide one." }
            }

        val authProvider = finalAuthProviderData.createAuthProvider(
            store = authProviderDataStore,
            onLogout = { onLogout(it, authenticationStore) },
            httpClientEngine = config.httpClientEngine,
            httpClientConfig = config.httpClientConfig
        )

        val api = config.matrixClientServerApiClientFactory.create(
            authProvider = authProvider,
            json = di.get(),
            eventContentSerializerMappings = di.get(),
            syncBatchTokenStore = AccountStoreSyncBatchTokenStore(accountStore),
            syncErrorDelayConfig = config.syncErrorDelayConfig,
            coroutineContext = finalCoroutineContext,
            httpClientEngine = config.httpClientEngine,
            httpClientConfig = config.httpClientConfig,
        )
        try {
            val newProfile =
                if (isFirstInit || initialAccount.profile == null) {
                    api.user.getProfile(userId)
                        .fold(
                            onSuccess = { it },
                            onFailure = {
                                if (it is MatrixServerException && it.statusCode == HttpStatusCode.NotFound) Profile()
                                else throw it
                            }
                        )
                } else null
            if (isFirstInit) {
                accountStore.updateAccount {
                    Account(
                        olmPickleKey = null,
                        userId = userId,
                        deviceId = deviceId,
                        backgroundFilterId = null,
                        filterId = null,
                        syncBatchToken = null,
                        profile = newProfile
                    )
                }
            } else if (newProfile != null) { // migration path
                accountStore.updateAccount { it?.copy(profile = newProfile) }
            }


            val userInfo = getUserInfo(userId, deviceId, di)

            koinApplication.modules(module {
                single { userInfo }
                single<MatrixClientServerApiClient> { api }
            })

            if (isFirstInit) {
                val keyStore = di.get<KeyStore>()

                val selfSignedDeviceKeys = di.get<SignService>().getSelfSignedDeviceKeys()

                api.key.setKeys(deviceKeys = selfSignedDeviceKeys).getOrThrow()
                selfSignedDeviceKeys.signed.keys.forEach {
                    keyStore.saveKeyVerificationState(it, KeyVerificationState.Verified(it.value.value))
                }
                keyStore.updateOutdatedKeys { it + userId }
            }

            log.trace { "finished create MatrixClient" }
            MatrixClientImpl(finalAuthProviderData.baseUrl, di)
        } catch (t: Throwable) {
            api.close()
            throw t
        }
    } catch (t: Throwable) {
        coroutineScope.cancel()
        throw t
    }
}

private class AuthenticationStoreMatrixClientAuthProviderDataStore(
    private val mappings: MatrixClientAuthProviderDataSerializerMappings,
    private val store: AuthenticationStore,
) : MatrixClientAuthProviderDataStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    override suspend fun getAuthData(): MatrixClientAuthProviderData? =
        store.getAuthentication()?.let { authentication ->
            val mapping = checkNotNull(mappings.find { it.id == authentication.providerId }) {
                "authProviderData id=${authentication.providerId} is not supported. Supported ids: ${mappings.map { it.id }}"
            }
            json.decodeFromString(mapping.serializer, authentication.providerData)
        }

    override suspend fun setAuthData(authData: MatrixClientAuthProviderData?) = store.updateAuthentication {
        if (authData != null) {
            val mapping = checkNotNull(mappings.find { it.kClass == authData::class }) {
                "authProviderData kClass=${authData::class} is not supported. Supported types: ${mappings.map { it.kClass }}"
            }

            @Suppress("UNCHECKED_CAST")
            val providerData =
                json.encodeToString(mapping.serializer as KSerializer<MatrixClientAuthProviderData>, authData)
            it?.copy(providerData = providerData)
                ?: Authentication(
                    providerId = mapping.id,
                    providerData = providerData,
                    logoutInfo = null,
                )
        } else null
    }
}

private class AccountStoreSyncBatchTokenStore(
    private val store: AccountStore,
) : SyncBatchTokenStore {
    override suspend fun getSyncBatchToken(): String? =
        store.getAccount()?.syncBatchToken

    override suspend fun setSyncBatchToken(token: String) {
        store.updateAccount { it?.copy(syncBatchToken = token) }
    }
}

private suspend fun onLogout(
    logoutInfo: LogoutInfo,
    authenticationStore: AuthenticationStore,
) {
    log.debug { "This device has been logged out ($logoutInfo)." }
    withContext(NonCancellable) {
        authenticationStore.updateAuthentication {
            it?.copy(logoutInfo = logoutInfo)
        }
    }
}

private val coroutineExceptionHandler = CoroutineExceptionHandler { _, exception ->
    log.error(exception) { "There was an unexpected exception. This should never happen!!!" }
}

private suspend fun runMigrationsAndInitStores(di: Koin) {
    di.getAll<RepositoryMigration>().forEach { it.run() }
    val rootStore = di.get<RootStore>()
    rootStore.init(di.get())
}

private suspend fun getUserInfo(userId: UserId, deviceId: String, di: Koin): UserInfo {
    val driver = di.get<CryptoDriver>()

    val pickleKey = driver.key.pickleKey(di.get<AccountStore>().getAccount()?.olmPickleKey)

    val olmCryptoStore = di.get<OlmCryptoStore>()
    val (signingKey, identityKey) = (olmCryptoStore.getOlmAccount()
        ?.let { driver.olm.account.fromPickle(it, pickleKey) }
        ?: driver.olm.account())
        .use { account ->
            olmCryptoStore.updateOlmAccount { account.pickle(pickleKey) }

            useAll(
                { account.ed25519Key },
                { account.curve25519Key }
            ) { signingKey, identityKey ->
                Key.of(deviceId, signingKey) to Key.of(deviceId, identityKey)
            }
        }
    return UserInfo(userId, deviceId, signingKey, identityKey)
}

class MatrixClientImpl internal constructor(
    override val baseUrl: Url,
    override val di: Koin,
) : MatrixClient {
    private val coroutineScope: CoroutineScope = di.get()
    private val rootStore: RootStore = di.get()
    private val authenticationStore: AuthenticationStore = di.get()
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

    override val started: MatrixClientStarted = di.get()

    override val profile: StateFlow<Profile?> = accountStore.getAccountAsFlow().map { it?.profile ?: Profile() }
        .stateIn(coroutineScope, Eagerly, null)
    override val serverData: StateFlow<ServerData?> = di.get<ServerDataStore>().getServerDataFlow()
        .stateIn(coroutineScope, Eagerly, null)

    override val syncState = api.sync.currentSyncState

    override val initialSyncDone: StateFlow<Boolean> =
        accountStore.getAccountAsFlow().map { it?.syncBatchToken }
            .map { token -> token != null }
            .stateIn(coroutineScope, Eagerly, true)

    override val loginState: StateFlow<LoginState?> =
        authenticationStore.getAuthenticationAsFlow().map { authentication ->
            val logoutInfo = authentication?.logoutInfo
            when {
                logoutInfo == null -> LOGGED_IN
                logoutInfo.isSoft -> LOGGED_OUT_SOFT
                logoutInfo.isLocked -> LOCKED
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
                            authenticationStore.updateAuthentication { it?.copy(logoutInfo = null) }
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
        coroutineScope.cancel("stopped MatrixClient")
    }

    override suspend fun closeSuspending() {
        val job = coroutineScope.coroutineContext.job
        close()
        job.join()
    }

    override suspend fun setProfileField(profileField: ProfileField): Result<Unit> {
        return api.user.setProfileField(userId, profileField).map {
            accountStore.updateAccount { it?.copy(profile = (it.profile ?: Profile()) + profileField) }
        }
    }
}