package de.connect2x.trixnity.client.integrationtests

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import de.connect2x.lognity.api.logger.Logger
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import de.connect2x.trixnity.client.*
import de.connect2x.trixnity.client.room.decrypt
import de.connect2x.trixnity.client.room.encrypt
import de.connect2x.trixnity.client.store.repository.exposed.exposed
import de.connect2x.trixnity.client.store.repository.inMemory
import de.connect2x.trixnity.client.store.repository.room.TrixnityRoomDatabase
import de.connect2x.trixnity.client.store.repository.room.room
import de.connect2x.trixnity.core.ClientEventEmitter
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent
import de.connect2x.trixnity.core.model.events.InitialStateEvent
import de.connect2x.trixnity.core.model.events.RoomEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptionEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.subscribeEventList
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.utils.nextString
import org.jetbrains.exposed.sql.Database
import org.openjdk.jol.info.GraphStats
import org.testcontainers.containers.Network
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant


private val log = Logger("de.connect2x.trixnity.client.integrationtests.PerformanceIT")

@Testcontainers
class PerformanceIT : TrixnityBaseTest() {

    @Test
    fun realmVsExposedSyncSpeed(): Unit = runBlocking(Dispatchers.Default) {
        val results = measureSync(
            { baseUrl ->
                registerAndStartClient(
                    "exposed", "exposed", baseUrl,
                    repositoriesModule = RepositoriesModule.exposed(
                        Database.connect("jdbc:h2:./build/tmp/test/${Random.nextString(22)};DB_CLOSE_DELAY=-1;")
                    ),
                ).client
            },
            { baseUrl ->
                registerAndStartClient(
                    "room", "room", baseUrl,
                    repositoriesModule = RepositoriesModule.room(
                        Room.databaseBuilder<TrixnityRoomDatabase>("build/tmp/test/${Random.nextString(22)}.db").apply {
                            setDriver(BundledSQLiteDriver())
                        }),
                ).client
            }
        )

        val exposedDuration = results.total[0].duration
        val roomDuration = results.total[1].duration

        val diff = (exposedDuration / roomDuration) * 100

        println("################################")
        println("exposedDuration: $exposedDuration")
        println("################################")
        println("roomDuration: $roomDuration")
        println("################################")
        println("diff: ${diff.roundToInt()}%")
        println("################################")

        roomDuration shouldBeLessThan 2.seconds
        diff shouldBeGreaterThan 110.0 // % (room is faster)
    }

    @Test
    fun syncSpeedAndMemoryUsage(): Unit = runBlocking(Dispatchers.Default) {
        fun client(name: String): suspend (Url) -> MatrixClient = { baseUrl ->
            registerAndStartClient(
                name, baseUrl = baseUrl,
                repositoriesModule = RepositoriesModule.inMemory(),
            ).client
        }

        val repeat = 10
        val warmup = 1
        val results = measureSync(
            *(1..(repeat + warmup)).map { client("measure-$it") }.toTypedArray(),
            timeout = 4.minutes,
            roomsCount = 10,
            messagesCount = 5,
        ).total
            .drop(warmup)

        val duration = results.fold(0.seconds) { current, next -> current + next.duration } / repeat
        val memoryUsage = results.fold(0L) { current, next -> current + next.memoryUsage } / repeat
        val memoryFootprint = results.fold(0L) { current, next -> current + next.memoryFootprint } / repeat

        println("duration = $duration ${results.map { it.duration }}")
        println("memoryUsage = ${memoryUsage / 1_000} KB ${results.map { (it.memoryUsage / 1_000).toString() + " KB" }}")
        println("memoryFootprint = ${memoryFootprint / 1_000} KB ${results.map { (it.memoryFootprint / 1_000).toString() + " KB" }}")
        duration shouldBeLessThan 200.milliseconds
        (memoryUsage / 1_000) shouldBeLessThan 2_000 // KB
        (memoryFootprint / 1_000) shouldBeLessThan 100_000 // KB
    }

    @Test
    fun fullClientVsBotModeSyncSpeed(): Unit = runBlocking(Dispatchers.Default) {
        val matrixPostgresql1 = PostgreSQLContainer("postgres:16").apply { start() }
        val matrixPostgresql2 = PostgreSQLContainer("postgres:16").apply { start() }
        val results = measureSync(
            { baseUrl ->
                registerAndStartClient(
                    "fullClient", baseUrl = baseUrl,
                    repositoriesModule = RepositoriesModule.exposed(matrixPostgresql1.getDatabase()),
                ).client
            },
            { baseUrl ->
                registerAndStartClient(
                    "bot", baseUrl = baseUrl,
                    repositoriesModule = RepositoriesModule.exposed(matrixPostgresql2.getDatabase()),
                ) {
                    modulesFactories = createTrixnityBotModuleFactories()
                }.client
            },
            { baseUrl ->
                registerAndStartClient(
                    "inMemoryBot", baseUrl = baseUrl,
                    repositoriesModule = RepositoriesModule.inMemory(),
                ) {
                    modulesFactories = createTrixnityBotModuleFactories()
                }.client
            },
            decrypt = true
        )

        fun printTable(name: String, r: List<MeasureSyncResult>) {
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
            |memory usage    |${r.joinToString("|") { "%-16s".format((it.memoryUsage / 1_000).toString() + " KB") }}|
            |memory footprint|${r.joinToString("|") { "%-16s".format((it.memoryFootprint / 1_000_000).toString() + " MB") }}|
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
        val createRoom: List<MeasureSyncResult>,
        val initialMessages: List<MeasureSyncResult>,
        val messages: List<MeasureSyncResult>,
    ) {
        val total: List<MeasureSyncResult> =
            List(createRoom.size) { index ->
                MeasureSyncResult(
                    duration = createRoom[index].duration + initialMessages[index].duration + messages[index].duration,
                    roomEventsCount = createRoom[index].roomEventsCount + initialMessages[index].roomEventsCount + messages[index].roomEventsCount,
                    memoryUsage = createRoom[index].memoryUsage + initialMessages[index].memoryUsage + messages[index].memoryUsage,
                    memoryFootprint = (createRoom[index].memoryFootprint + initialMessages[index].memoryFootprint + messages[index].memoryFootprint) / 3
                )
            }
    }

    private suspend fun measureSync(
        vararg clientFactories: suspend (Url) -> MatrixClient,
        decrypt: Boolean = false,
        timeout: Duration = 4.minutes,
        roomsCount: Int = 40,
        messagesCount: Int = 10,
    ): SyncSpeedResults =
        withTimeout(timeout) {
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
            val clients = clientFactories.map { it(baseUrl) }
            clients.map { async { it.stopSync() } }.awaitAll()

            val prepareTestClients = (1..roomsCount).withLimitedParallelism { i ->
                registerAndStartClient("prepare-$i", "prepare-$i", baseUrl, RepositoriesModule.inMemory()) {
                    modulesFactories = createTrixnityBotModuleFactories()
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
                it.cancelSync()
                userId to roomId
            }.toMap()
            log.info { "all rooms created" }

            val createRoomResults = clients.measureSync(decrypt)

            prepareTestClients.withLimitedParallelism {
                it.startSync()
                val roomId = rooms[userId]
                checkNotNull(roomId)
                val encryptedEvent = roomEventEncryptionServices
                    .encrypt(RoomMessageEventContent.TextBased.Text(roomId.full), roomId)
                    ?.getOrThrow()
                encryptedEvent.shouldNotBeNull()
                api.room.sendMessageEvent(roomId, encryptedEvent).getOrThrow()
                it.cancelSync()
            }
            log.info { "all initial messages sent" }

            val initialMessageResults = clients.measureSync(decrypt)

            prepareTestClients.withLimitedParallelism {
                it.startSync()
                val roomId = rooms[userId]
                checkNotNull(roomId)
                repeat(messagesCount) { i ->
                    val encryptedEvent = roomEventEncryptionServices
                        .encrypt(RoomMessageEventContent.TextBased.Text("$i"), roomId)
                        ?.getOrThrow()
                    encryptedEvent.shouldNotBeNull()
                    api.room.sendMessageEvent(roomId, encryptedEvent).getOrThrow()
                }
                it.closeSuspending()
            }
            log.info { "all messages sent" }

            val messagesResults = clients.measureSync(decrypt)

            clients.withLimitedParallelism { it.closeSuspending() }
            synapse.stop()
            synapsePostgresql.stop()

            SyncSpeedResults(
                createRoom = createRoomResults,
                initialMessages = initialMessageResults,
                messages = messagesResults
            )
        }

    data class MeasureSyncResult(
        val duration: Duration,
        val roomEventsCount: Int,
        val memoryUsage: Long,
        val memoryFootprint: Long,
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

    private suspend fun List<MatrixClient>.measureSync(decrypt: Boolean): List<MeasureSyncResult> =
        map { client ->
            var syncTime = Duration.ZERO
            var syncStartInstant: Instant? = null
            var memBefore: Long? = null
            var memAfter: Long? = null

            val unsubscribe1 = client.api.sync.subscribe(ClientEventEmitter.Priority.FIRST) {
                System.gc()
                memBefore = GraphStats.parseInstance(client).totalSize()
                syncStartInstant = Clock.System.now()
            }
            val unsubscribe2 = client.api.sync.subscribe(ClientEventEmitter.Priority.LAST) {
                syncTime += Clock.System.now() - checkNotNull(syncStartInstant)
                memAfter = GraphStats.parseInstance(client).totalSize()
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

            MeasureSyncResult(
                duration = syncTime,
                roomEventsCount = roomEventsCount.value,
                memoryUsage = checkNotNull(memAfter) - checkNotNull(memBefore),
                memoryFootprint = checkNotNull(memAfter)
            )
        }

    private fun PostgreSQLContainer.getDatabase(): Database {
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