package net.folivo.trixnity.client.integrationtests

import com.benasher44.uuid.uuid4
import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.flattenValues
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.room.message.reply
import net.folivo.trixnity.client.room.message.text
import net.folivo.trixnity.client.store.membership
import net.folivo.trixnity.client.store.repository.createInMemoryRepositoriesModule
import net.folivo.trixnity.client.store.repository.exposed.createExposedRepositoriesModule
import net.folivo.trixnity.client.store.repository.realm.createRealmRepositoriesModule
import net.folivo.trixnity.client.store.sender
import net.folivo.trixnity.client.user
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClientImpl
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.InitialStateEvent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership.INVITE
import net.folivo.trixnity.core.model.events.m.room.Membership.JOIN
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import org.jetbrains.exposed.sql.Database
import org.testcontainers.containers.Network
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime


private val log = KotlinLogging.logger {}

@Testcontainers
@OptIn(ExperimentalCoroutinesApi::class)
class PerformanceIT {

    @Test
    fun realmVsExposed(): Unit = runBlocking {
        withTimeout(120_000) {
            val synapse = synapseDocker().also { it.start() }
            val baseUrl = URLBuilder(
                protocol = URLProtocol.HTTP,
                host = synapse.host,
                port = synapse.firstMappedPort
            ).build()
            val prepareTestClient =
                registerAndStartClient("prepare", "user1", baseUrl, createInMemoryRepositoriesModule())
            val exposedClient = startClient(
                "exposed", "user1", baseUrl,
                repositoriesModule = createExposedRepositoriesModule(newDatabase()),
            )
            val realmClient = startClient(
                "realm", "user1", baseUrl,
                repositoriesModule = createRealmRepositoriesModule {
                    inMemory()
                    directory("build/test-db/${uuid4()}")
                },
            )

            exposedClient.client.cancelSync(true)
            realmClient.client.cancelSync(true)

            withContext(Dispatchers.Default.limitedParallelism(20)) {
                repeat(50) {
                    launch {
                        val roomId = prepareTestClient.client.api.room.createRoom(
                            initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), ""))
                        ).getOrThrow()
                        prepareTestClient.client.room.getById(roomId).filterNotNull().first()
                        repeat(10) { i ->
                            prepareTestClient.client.room.sendMessage(roomId) {
                                text(i.toString())
                            }
                        }
                    }
                }
            }
            prepareTestClient.client.room.waitForOutboxSent()
            prepareTestClient.client.stop()
            suspend fun StartedClient.measureSyncProcessing() =
                measureTime {
                    client.syncOnce().getOrThrow()
                }

            val exposedTransactionsTime = exposedClient.measureSyncProcessing()
            exposedClient.client.stop()
            val realmTransactionTime = realmClient.measureSyncProcessing()
            realmClient.client.stop()

            val diff = (exposedTransactionsTime / realmTransactionTime) * 100

            println("################################")
            println("exposed transaction: $realmTransactionTime")
            println("################################")
            println("realm transaction: $exposedTransactionsTime")
            println("################################")
            println("diff: ${diff.roundToInt()}%")
            println("################################")

            diff shouldBeLessThan 200.0 // %
            diff shouldBeGreaterThan 50.0 // %
            synapse.stop()
        }
    }

    @Test
    fun throughput() = runBlocking {
        withTimeout(4.minutes) {
            val requestsCount = 30
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
                createExposedRepositoriesModule(
                    Database.connect(
                        url = matrixPostgresql.getJdbcUrl(), driver = "org.postgresql.Driver",
                        user = matrixPostgresql.username, password = matrixPostgresql.password
                    )
                )
            ) {
                name = "receiver"
                syncLoopDelays = MatrixClientConfiguration.SyncLoopDelays(Duration.ZERO, Duration.ZERO)
                cacheExpireDurations = MatrixClientConfiguration.CacheExpireDurations.default(30.seconds)
            }

            val sentMessages = MutableStateFlow(0)
            val answeredMessages = MutableStateFlow(0)
            val totalEvents = MutableStateFlow(0)

            log.info { "start" }

            val startInstant = Clock.System.now()
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
                            }
                        val roomId = sendingClient.client.api.room.createRoom(
                            initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), "")),
                            invite = setOf(receivingClient.client.userId),
                        ).getOrThrow()
                        sendingClient.client.user.getById(roomId, receivingClient.client.userId)
                            .first { it?.membership == JOIN }
                        repeat(messagesPerRequestCount) {
                            sendingClient.client.room.sendMessage(roomId) {
                                text("ping ${sentMessages.getAndUpdate { it + 1 }}")
                            }
                        }
                        delay(1.seconds) // wait for outbox to be updated
                        sendingClient.client.room.waitForOutboxSent()
                        sendingClient.client.stop()
                        currentParallelRequestsCount.update { it - 1 }
                    }
                }
            }
            val autoJoinAndAnswerJob = launch {
                launch {
                    val autoJoinedRooms = mutableSetOf<RoomId>()
                    receivingClient.client.room.getAll().flattenValues()
                        .map { allRooms -> allRooms.filter { it.membership == INVITE }.map { it.roomId } }
                        .collect { allRooms ->
                            coroutineScope {
                                allRooms.forEach { roomId ->
                                    if (!autoJoinedRooms.contains(roomId)) {
                                        autoJoinedRooms.add(roomId)
                                        launch {
                                            receivingClient.client.api.room.joinRoom(roomId).onFailure {
                                                log.error(it) { "could not join room $roomId" }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                }
                launch {
                    receivingClient.client.room.getTimelineEventsFromNowOn(
                        decryptionTimeout = 10.seconds,
                        syncResponseBufferSize = 100
                    ).collect { timelineEvent ->
                        totalEvents.value++
                        val content =
                            requireNotNull(timelineEvent.content?.getOrThrow()) { "content of $timelineEvent was null" }
                        if (content is RoomMessageEventContent.TextMessageEventContent && timelineEvent.sender != receivingClient.client.userId) {
                            check(timelineEvent.gap?.hasGapBefore != true) { "there was a gap in timelineEvent $timelineEvent" }
                            receivingClient.client.room.sendMessage(timelineEvent.roomId) {
                                text("pong  ${answeredMessages.getAndUpdate { it + 1 }}")
                                reply(timelineEvent)
                            }
                        }
                    }
                }
            }
            val progressJob = launch {
                combine(
                    sentMessages,
                    answeredMessages,
                    receivingClient.client.room.getOutbox().flattenValues()
                        .map { outbox -> outbox.filter { it.sentAt != null }.size }
                        .distinctUntilChanged(),
                ) { sent, answered, outbox ->
                    val percent = (100F * answered / (requestsCount * messagesPerRequestCount)).roundToInt()

                    "====== progress: " +
                            "sent=${sent.toString().padStart(8, '0')} " +
                            "answered=${answered.toString().padStart(8, '0')} " +
                            "outbox=${outbox.toString().padStart(8, '0')} " +
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
            receivingClient.client.room.waitForOutboxSent()

            val measuredTime = Clock.System.now() - startInstant

            progressJob.cancelAndJoin()
            autoJoinAndAnswerJob.cancelAndJoin()
            receivingClient.client.stop()

            val allMessagesCount = requestsCount * messagesPerRequestCount * 2
            val allEventsCount = totalEvents.value * 2
            val messageThroughputPerSecond = allMessagesCount / measuredTime.inWholeSeconds
            val eventsThroughputPerSecond = allEventsCount / measuredTime.inWholeSeconds
            val averageTimePerMessage = measuredTime / allMessagesCount
            val averageTimePerEvent = measuredTime / allEventsCount
            println("################################")
            println(
                "parameters: requestsCount=$requestsCount " +
                        "parallelRequestsCount=$parallelRequestsCount " +
                        "messagesPerRequestCount=$messagesPerRequestCount " +
                        "allMessagesCount=$allMessagesCount " +
                        "allEventsCount=$allEventsCount"
            )
            println("################################")
            println("measuredTime=$measuredTime")
            println("################################")
            println("eventsThroughputPerSecond=$eventsThroughputPerSecond")
            println("################################")
            println("averageTimePerEvent: $averageTimePerEvent")
            println("################################")
            println("messageThroughputPerSecond=$messageThroughputPerSecond")
            println("################################")
            println("averageTimePerMessage: $averageTimePerMessage")
            println("################################")

            synapse.stop()
            matrixPostgresql.stop()
            synapsePostgresql.stop()
        }
    }
}