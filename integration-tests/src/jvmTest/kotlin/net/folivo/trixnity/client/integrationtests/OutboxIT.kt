package net.folivo.trixnity.client.integrationtests

import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import io.kotest.matchers.collections.shouldHaveSize
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.room.message.text
import net.folivo.trixnity.client.store.SecureStore
import net.folivo.trixnity.client.store.sqldelight.SqlDelightStoreFactory
import net.folivo.trixnity.client.store.sqldelight.db.Database
import org.kodein.log.Logger
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@Testcontainers
class OutboxIT {

    private lateinit var client: MatrixClient
    private lateinit var scope: CoroutineScope
    private lateinit var driver: JdbcSqliteDriver

    @Container
    val synapseDocker = GenericContainer<Nothing>(DockerImageName.parse("matrixdotorg/synapse:latest"))
        .apply {
            withEnv(
                mapOf(
                    "VIRTUAL_HOST" to "localhost",
                    "VIRTUAL_PORT" to "8008",
                    "SYNAPSE_SERVER_NAME" to "localhost",
                    "SYNAPSE_REPORT_STATS" to "no",
                    "UID" to "1000",
                    "GID" to "1000"
                )
            )
            withClasspathResourceMapping("data", "/data", BindMode.READ_WRITE)
            withExposedPorts(8008)
            waitingFor(Wait.forHealthcheck())
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @BeforeTest
    fun beforeEach() = runBlocking {
        deleteDbFiles()
        scope = CoroutineScope(Dispatchers.Default)
        val password = "user$1passw0rd"
        val baseUrl = URLBuilder(
            protocol = URLProtocol.HTTP,
            host = synapseDocker.host,
            port = synapseDocker.firstMappedPort
        ).build()
        driver = JdbcSqliteDriver("jdbc:sqlite:outbox-it.db")
        val storeFactory = SqlDelightStoreFactory(
            driver,
            scope,
            newSingleThreadContext("sqlite"),
            newSingleThreadContext("transactions")
        )
        val secureStore = object : SecureStore {
            override val olmPickleKey = ""
        }

        client = MatrixClient.loginWith(
            baseUrl = baseUrl,
//            baseHttpClient = HttpClient(Java) { install(Logging) { level = LogLevel.INFO } },
            storeFactory = storeFactory,
            secureStore = secureStore,
            scope = scope,
            loggerFactory = loggerFactory("user 🔴", Logger.Level.DEBUG),
            getLoginInfo = { it.register("user", password) }
        )
        client.startSync()
    }

    @AfterTest
    fun afterEach() {
        scope.cancel()
        driver.close()
        deleteDbFiles()
    }

    private fun deleteDbFiles() {
        File("outbox-it.db").delete()
        File("outbox-it.db-journal").delete()
    }

    @Test
    fun shouldSendManyMessagesAndHaveEmptyOutboxAfterThat(): Unit = runBlocking {
        val room = client.api.rooms.createRoom()

        repeat(30) {
            client.room.sendMessage(room) { text("message $it") }
        }

        try {
            withTimeout(120_000) {
                client.room.getOutbox()
                    .first { outbox -> outbox.none { it.sentAt != null } }
                delay(10_000)
                client.room.sendMessage(room) { text("finish") }
                client.room.getOutbox().onEach { it.map { it.transactionId } }.first { it.isEmpty() }
            }
        } catch (error: TimeoutCancellationException) {
            throw error
        }
        scope.cancel()
        delay(500) // let everything stop
        val database = Database(driver)
        database.roomOutboxMessageQueries.getAllRoomOutboxMessages().executeAsList() shouldHaveSize 0
    }
}