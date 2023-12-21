package net.folivo.trixnity.client.integrationtests

import com.benasher44.uuid.uuid4
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.createTrixnityBotModules
import net.folivo.trixnity.client.room.decrypt
import net.folivo.trixnity.client.room.encrypt
import net.folivo.trixnity.client.roomEventEncryptionServices
import net.folivo.trixnity.client.store.repository.createInMemoryRepositoriesModule
import net.folivo.trixnity.client.store.repository.exposed.createExposedRepositoriesModule
import net.folivo.trixnity.client.store.repository.realm.createRealmRepositoriesModule
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClientImpl
import net.folivo.trixnity.clientserverapi.model.users.Filters
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent
import net.folivo.trixnity.core.model.events.InitialStateEvent
import net.folivo.trixnity.core.model.events.RoomEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership.INVITE
import net.folivo.trixnity.core.model.events.m.room.Membership.JOIN
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.events.roomIdOrNull
import net.folivo.trixnity.core.model.events.stateKeyOrNull
import net.folivo.trixnity.core.subscribeContentAsFlow
import net.folivo.trixnity.core.subscribeContentList
import net.folivo.trixnity.core.subscribeEventList
import org.jetbrains.exposed.sql.Database
import org.testcontainers.containers.Network
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


private val log = KotlinLogging.logger {}

@Testcontainers
class PerformanceIT {

    @Test
    fun realmVsExposedSyncSpeed(): Unit = runBlocking(Dispatchers.Default) {
        val results = syncSpeed(false, { baseUrl ->
            registerAndStartClient(
                "exposed", "exposed", baseUrl,
                repositoriesModule = createExposedRepositoriesModule(newDatabase()),
            ).client
        },
            { baseUrl ->
                registerAndStartClient(
                    "realm", "realm", baseUrl,
                    repositoriesModule = createRealmRepositoriesModule {
                        inMemory()
                        directory("build/test-db/${uuid4()}")
                    },
                ).client
            }
        )

        val exposedDuration = results.total[0].duration
        val realmDuration = results.total[1].duration

        val diff = (exposedDuration / realmDuration) * 100

        println("################################")
        println("exposedDuration: $exposedDuration")
        println("################################")
        println("realmDuration: $realmDuration")
        println("################################")
        println("diff: ${diff.roundToInt()}%")
        println("################################")

        diff shouldBeLessThan 80.0 // %
    }

    @Test
    fun fullClientVsBotModeSyncSpeed(): Unit = runBlocking(Dispatchers.Default) {
        val matrixPostgresql1 = PostgreSQLContainer("postgres:16").apply { start() }
        val matrixPostgresql2 = PostgreSQLContainer("postgres:16").apply { start() }
        val results = syncSpeed(true,
            { baseUrl ->
                registerAndStartClient(
                    "fullClient", baseUrl = baseUrl,
                    repositoriesModule = createExposedRepositoriesModule(matrixPostgresql1.getDatabase()),
                ).client
            },
            { baseUrl ->
                registerAndStartClient(
                    "bot", baseUrl = baseUrl,
                    repositoriesModule = createExposedRepositoriesModule(matrixPostgresql2.getDatabase()),
                ) {
                    modules = createTrixnityBotModules()
                }.client
            },
            { baseUrl ->
                registerAndStartClient(
                    "inMemoryBot", baseUrl = baseUrl,
                    repositoriesModule = createInMemoryRepositoriesModule(),
                ) {
                    modules = createTrixnityBotModules()
                }.client
            }
        )

        fun printTable(name: String, r: List<SyncSpeedResult>) {
            println(
                """
            #####################################################################
            |${"%-67s".format(name)}|
            +----------------+----------------+----------------+----------------+
            | what   \  mode | full           | bot            | bot (memory)   |
            +----------------+----------------+----------------+----------------+
            |sync time       |${r.joinToString("|") { "%-16s".format(it.duration.toString()) }}|
            |RoomEvent count |${r.joinToString("|") { "%-16s".format(it.roomEventsCount.toString()) }}|
            |speed/RoomEvent |${r.joinToString("|") { "%-16s".format((it.duration / it.roomEventsCount).toString()) }}|
        """.trimIndent()
            )
        }
        printTable("createRoom", results.createRoom)
        printTable("initialMessages", results.initialMessages)
        printTable("messages", results.messages)
        printTable("total", results.total)

        matrixPostgresql1.stop()
        matrixPostgresql2.stop()

        (results.total[1].duration / results.total[0].duration) shouldBeLessThan 80.0 // %
        (results.total[2].duration / results.total[1].duration) shouldBeLessThan 80.0 // %
    }

    data class SyncSpeedResults(
        val createRoom: List<SyncSpeedResult>,
        val initialMessages: List<SyncSpeedResult>,
        val messages: List<SyncSpeedResult>,
    ) {
        val total: List<SyncSpeedResult> =
            List(createRoom.size) { index ->
                SyncSpeedResult(
                    duration = createRoom[index].duration + initialMessages[index].duration + messages[index].duration,
                    roomEventsCount = createRoom[index].roomEventsCount + initialMessages[index].roomEventsCount + messages[index].roomEventsCount,
                )
            }
    }

    private suspend fun syncSpeed(
        decrypt: Boolean = false,
        clientFactory: suspend (Url) -> MatrixClient,
        vararg moreClientFactories: suspend (Url) -> MatrixClient,
    ): SyncSpeedResults =
        withTimeout(4.minutes) {
            val roomsCount = 40
            val messagesCount = 10

            val network = Network.newNetwork()
            val synapsePostgresql = PostgreSQLContainer("postgres:16").apply {
                withNetworkAliases("synapse-postgresql")
                withNetwork(network)
                withEnv(mapOf("POSTGRES_INITDB_ARGS" to "--encoding=UTF-8 --lc-collate=C --lc-ctype=C"))
                start()
            }
            val synapse = synapseDocker()
                .apply {
                    withNetwork(network)
                    withEnv(mapOf("SYNAPSE_CONFIG_PATH" to "/data/homeserver-postgresql.yaml"))
                    start()
                }
            val baseUrl = URLBuilder(
                protocol = URLProtocol.HTTP,
                host = synapse.host,
                port = synapse.firstMappedPort
            ).build()
            val clients = (listOf(clientFactory) + moreClientFactories).map { it(baseUrl) }
            clients.map { async { it.stopSync(true) } }.awaitAll()

            val prepareTestClients = (1..roomsCount).withLimitedParallelism { i ->
                registerAndStartClient("prepare-$i", "prepare-$i", baseUrl, createInMemoryRepositoriesModule()) {
                    modules = createTrixnityBotModules()
                    cacheExpireDurations = MatrixClientConfiguration.CacheExpireDurations.default(5.seconds)
                }.client
            }
            log.info { "created prepare clients" }

            val rooms = prepareTestClients.withLimitedParallelism {
                val roomId = api.room.createRoom(
                    initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), "")),
                    invite = clients.map { it.userId }.toSet(),
                ).getOrThrow()
                clients.forEach { it.api.room.joinRoom(roomId).getOrThrow() }
                userId to roomId
            }.toMap()
            log.info { "all rooms created" }

            val createRoomResults = clients.measureSyncSpeed(decrypt)

            prepareTestClients.withLimitedParallelism {
                val roomId = rooms[userId]
                checkNotNull(roomId)
                val encryptedEvent = roomEventEncryptionServices
                    .encrypt(RoomMessageEventContent.TextMessageEventContent(roomId.full), roomId)
                    ?.getOrThrow()
                encryptedEvent.shouldNotBeNull()
                api.room.sendMessageEvent(roomId, encryptedEvent).getOrThrow()
            }
            log.info { "all initial messages sent" }

            val initialMessageResults = clients.measureSyncSpeed(decrypt)

            prepareTestClients.withLimitedParallelism {
                val roomId = rooms[userId]
                checkNotNull(roomId)
                repeat(messagesCount) { i ->
                    val encryptedEvent = roomEventEncryptionServices
                        .encrypt(RoomMessageEventContent.TextMessageEventContent("$i"), roomId)
                        ?.getOrThrow()
                    encryptedEvent.shouldNotBeNull()
                    api.room.sendMessageEvent(roomId, encryptedEvent).getOrThrow()
                }
            }
            prepareTestClients.forEach { it.stop() }
            log.info { "all messages sent" }

            val messagesResults = clients.measureSyncSpeed(decrypt)

            clients.forEach { it.stop() }
            synapse.stop()
            synapsePostgresql.stop()

            SyncSpeedResults(
                createRoom = createRoomResults,
                initialMessages = initialMessageResults,
                messages = messagesResults
            )
        }

    data class SyncSpeedResult(
        val duration: Duration,
        val roomEventsCount: Int,
    )

    private suspend fun <S, T> Iterable<S>.withLimitedParallelism(
        limit: Int = 30,
        block: suspend S.(S) -> T,
    ): List<T> = coroutineScope {
        val semaphore = Semaphore(limit)
        map { s ->
            async {
                semaphore.withPermit {
                    block(s, s)
                }
            }
        }.awaitAll()
    }

    private suspend fun List<MatrixClient>.measureSyncSpeed(decrypt: Boolean): List<SyncSpeedResult> =
        map { client ->
            var syncTime = Duration.ZERO
            var syncStartInstant: Instant? = null
            val unsubscribe1 = client.api.sync.subscribe(Int.MAX_VALUE) {
                syncStartInstant = Clock.System.now()
            }
            val unsubscribe2 = client.api.sync.subscribe(Int.MIN_VALUE) {
                syncTime += Clock.System.now() - checkNotNull(syncStartInstant)
            }

            val roomEventsCount = MutableStateFlow(0)
            val unsubscribe3 =
                client.api.sync.subscribeEventList<RoomEventContent, RoomEvent<RoomEventContent>> { events ->
                    if (decrypt) {
                        coroutineScope {
                            events.forEach { event ->
                                val content = event.content
                                if (event is RoomEvent.MessageEvent && content is EncryptedMessageEventContent && event.sender != client.userId) {
                                    launch {
                                        val decryptedContent = withTimeout(5.seconds) {
                                            client.roomEventEncryptionServices.decrypt(event)
                                                ?.getOrThrow()
                                        }
                                        decryptedContent.shouldNotBeNull()
                                    }
                                }
                            }
                        }
                    }
                    roomEventsCount.update { it + events.size }
                }
            log.info { "start sync of ${client.userId}" }
            client.syncOnce().getOrThrow()
            log.info { "finished sync of ${client.userId}" }

            unsubscribe1()
            unsubscribe2()
            unsubscribe3()

            delay(1.seconds) // cool down

            SyncSpeedResult(syncTime, roomEventsCount.value)
        }

    @Test
    fun fullClientVsBotModeThroughput(): Unit =
        runBlocking(Dispatchers.Default) {
            val fullClientSyncTime = throughput()
            val botSyncTime = throughput { modules = createTrixnityBotModules() }
            val diff = (botSyncTime / fullClientSyncTime) * 100
            println("################################")
            println("fullClientSyncTime: $fullClientSyncTime")
            println("################################")
            println("botSyncTime: $botSyncTime")
            println("################################")
            println("diff: ${diff.roundToInt()}%")
            println("################################")

            // diff can be very small, because many components are involved
            diff shouldBeLessThan 100.0 // %
        }

    private suspend fun throughput(
        configuration: MatrixClientConfiguration.() -> Unit = {},
    ): Duration = withTimeout(4.minutes) {
        val requestsCount = 20
        val parallelRequestsCount = 10
        val messagesPerRequestCount = 10

        val network = Network.newNetwork()
        val matrixPostgresql = PostgreSQLContainer("postgres:16").apply { start() }
        val synapsePostgresql = PostgreSQLContainer("postgres:16").apply {
            withNetworkAliases("synapse-postgresql")
            withNetwork(network)
            withEnv(mapOf("POSTGRES_INITDB_ARGS" to "--encoding=UTF-8 --lc-collate=C --lc-ctype=C"))
            start()
        }
        val synapse = synapseDocker()
            .apply {
                withNetwork(network)
                withEnv(mapOf("SYNAPSE_CONFIG_PATH" to "/data/homeserver-postgresql.yaml"))
                start()
            }

        val baseUrl = URLBuilder(
            protocol = URLProtocol.HTTP,
            host = synapse.host,
            port = synapse.firstMappedPort
        ).build()
        val api = MatrixClientServerApiClientImpl(baseUrl)
        log.info { "register clients" }
        coroutineScope {
            repeat(requestsCount) { i ->
                launch {
                    api.register("sender-$i")
                }
            }
        }
        val receivingClient = registerAndStartClient(
            "receiver", "receiver", baseUrl,
            createExposedRepositoriesModule(matrixPostgresql.getDatabase())
        ) {
            name = "receiver"
            syncLoopDelays = MatrixClientConfiguration.SyncLoopDelays(Duration.ZERO, Duration.ZERO)
            cacheExpireDurations = MatrixClientConfiguration.CacheExpireDurations.default(30.seconds)
            syncFilter = Filters(
                room = Filters.RoomFilter(
                    timeline = Filters.RoomFilter.RoomEventFilter(limit = 20)
                )
            )
            configuration()
        }.client

        val sentMessages = MutableStateFlow(0)
        val answeredMessages = MutableStateFlow(0)
        val totalRoomEvents = MutableStateFlow(0)

        log.info { "start" }

        val startInstant = Clock.System.now()
        var syncTime = Duration.ZERO
        var syncStartInstant = Clock.System.now()
        receivingClient.api.sync.subscribe(Int.MAX_VALUE) { syncStartInstant = Clock.System.now() }
        receivingClient.api.sync.subscribe(Int.MIN_VALUE) { syncTime += Clock.System.now() - syncStartInstant }
        val sendingJob = launch {
            val currentParallelRequestsCount = MutableStateFlow(0)
            repeat(requestsCount) { i ->
                currentParallelRequestsCount.first { it < parallelRequestsCount }
                currentParallelRequestsCount.update { it + 1 }
                launch {
                    val sendingClient =
                        startClient("sender-$i", "sender-$i", baseUrl, createInMemoryRepositoriesModule()) {
                            name = "sender-$i"
                            syncLoopDelays = MatrixClientConfiguration.SyncLoopDelays(Duration.ZERO, Duration.ZERO)
                            configuration()
                        }.client
                    val roomId = sendingClient.api.room.createRoom(
                        initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), "")),
                        invite = setOf(receivingClient.userId),
                    ).getOrThrow()
                    sendingClient.api.sync.subscribeContentAsFlow<MemberEventContent>()
                        .first {
                            it.roomIdOrNull == roomId &&
                                    it.stateKeyOrNull == receivingClient.userId.full &&
                                    it.content.membership == JOIN
                        }
                    repeat(messagesPerRequestCount) {
                        val encryptedEvent = sendingClient.roomEventEncryptionServices
                            .encrypt(RoomMessageEventContent.TextMessageEventContent("ping $it"), roomId)
                            ?.getOrThrow()
                        encryptedEvent.shouldNotBeNull()
                        sendingClient.api.room.sendMessageEvent(roomId, encryptedEvent).getOrThrow()
                        sentMessages.getAndUpdate { it + 1 }
                    }
                    sendingClient.stop()
                    currentParallelRequestsCount.update { it - 1 }
                }
            }
        }
        val autoJoinAndAnswerJob = launch {
            launch {
                receivingClient.api.sync.subscribeContentList<MemberEventContent> { events ->
                    coroutineScope {
                        events.forEach { event ->
                            launch {
                                if (event.content.membership == INVITE && event.stateKeyOrNull == receivingClient.userId.full) {
                                    val roomId = event.roomIdOrNull
                                    roomId.shouldNotBeNull()
                                    receivingClient.api.room.joinRoom(roomId).getOrThrow()
                                }
                            }
                        }
                    }
                }
            }
            launch {
                receivingClient.api.sync.subscribeEventList<RoomEventContent, RoomEvent<RoomEventContent>> { events ->
                    coroutineScope {
                        events.forEach { event ->
                            launch {
                                val content = event.content
                                if (event is RoomEvent.MessageEvent && content is EncryptedMessageEventContent && event.sender != receivingClient.userId) {
                                    val decryptedContent = withTimeout(5.seconds) {
                                        receivingClient.roomEventEncryptionServices
                                            .decrypt(event)
                                            ?.getOrThrow()
                                    }
                                    decryptedContent.shouldNotBeNull()
                                    if (decryptedContent is RoomMessageEventContent.TextMessageEventContent) {
                                        receivingClient.api.room.sendMessageEvent(event.roomId, content)
                                            .getOrThrow()
                                        answeredMessages.getAndUpdate { it + 1 }
                                    }
                                }
                                totalRoomEvents.update { it + 1 }
                            }
                        }
                    }
                }
            }
        }
        val progressJob = launch {
            combine(
                sentMessages,
                answeredMessages,
            ) { sent, answered ->
                val percent = (100F * answered / (requestsCount * messagesPerRequestCount)).roundToInt()

                "====== progress: " +
                        "sent=${sent.toString().padStart(8, '0')} " +
                        "answered=${answered.toString().padStart(8, '0')} " +
                        "\n" +
                        "||" +
                        (0..percent).joinToString("") { "#" } +
                        (percent..100).joinToString("") { "-" } +
                        "||"
                            .also { delay(1.seconds) }
            }.collect {
                println(it)
            }
        }
        sendingJob.join()
        log.trace { "sender sent all messages" }
        sentMessages.first { it == requestsCount * messagesPerRequestCount }
        answeredMessages.first { it == requestsCount * messagesPerRequestCount }

        val measuredTime = Clock.System.now() - startInstant

        progressJob.cancelAndJoin()
        autoJoinAndAnswerJob.cancelAndJoin()
        receivingClient.stop()

        val allMessagesCount = requestsCount * messagesPerRequestCount * 2
        val allRoomEventsCount = totalRoomEvents.value
        println("")
        println("################################")
        println(
            "parameters: requestsCount=$requestsCount " +
                    "parallelRequestsCount=$parallelRequestsCount " +
                    "messagesPerRequestCount=$messagesPerRequestCount " +
                    "allMessagesCount=$allMessagesCount " +
                    "allRoomEventsCount=$allRoomEventsCount"
        )
        println("################################")
        println("syncTime=$syncTime")
        println("################################")
        println("eventsThroughputPerSecond=${allRoomEventsCount / syncTime.inWholeSeconds}")
        println("averageTimePerEvent: ${syncTime / allRoomEventsCount}")
        println("################################")
        println("messageThroughputPerSecond=${allMessagesCount / syncTime.inWholeSeconds}")
        println("averageTimePerMessage: ${syncTime / allMessagesCount}")
        println("################################")
        println("")
        println("################################")
        println("measuredTime=$measuredTime")
        println("################################")
        println("eventsRoundtripPerSecond=${allRoomEventsCount / measuredTime.inWholeSeconds}")
        println("averageRoundtripTimePerEvent: ${measuredTime / allRoomEventsCount}")
        println("################################")
        println("messageRoundtripPerSecond=${allMessagesCount / measuredTime.inWholeSeconds}")
        println("averageRoundtripTimePerMessage: ${measuredTime / allMessagesCount}")
        println("################################")
        println("")

        synapse.stop()
        matrixPostgresql.stop()
        synapsePostgresql.stop()

        syncTime
    }

    private fun PostgreSQLContainer<out PostgreSQLContainer<*>>.getDatabase(): Database {
        val config = HikariConfig().apply {
            jdbcUrl = this@getDatabase.getJdbcUrl()
            driverClassName = "org.postgresql.Driver"
            username = this@getDatabase.username
            password = this@getDatabase.password
            maximumPoolSize = 10
        }
        val dataSource = HikariDataSource(config)
        return Database.connect(dataSource)
    }
}