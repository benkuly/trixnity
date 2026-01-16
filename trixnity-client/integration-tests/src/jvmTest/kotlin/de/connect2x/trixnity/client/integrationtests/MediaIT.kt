package de.connect2x.trixnity.client.integrationtests

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import de.connect2x.trixnity.client.*
import de.connect2x.trixnity.client.cryptodriver.vodozemac.vodozemac
import de.connect2x.trixnity.client.media.inMemory
import de.connect2x.trixnity.client.store.repository.exposed.exposed
import de.connect2x.trixnity.clientserverapi.client.MatrixClientAuthProviderData
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.clientserverapi.client.classicLoginWith
import de.connect2x.trixnity.utils.toByteArrayFlow
import org.jetbrains.exposed.sql.Database
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@Testcontainers
class MediaIT {

    private lateinit var client: MatrixClient
    private lateinit var database: Database

    @Container
    val synapseDocker = synapseDocker()

    @BeforeTest
    fun beforeEach(): Unit = runBlocking {
        val password = "user$1passw0rd"
        val baseUrl = URLBuilder(
            protocol = URLProtocol.HTTP,
            host = synapseDocker.host,
            port = synapseDocker.firstMappedPort
        ).build()
        database = newDatabase()

        val repositoriesModule1 = RepositoriesModule.exposed(database)

        client = MatrixClient.create(
            repositoriesModule = repositoriesModule1,
            mediaStoreModule = MediaStoreModule.inMemory(),
            cryptoDriverModule = CryptoDriverModule.vodozemac(),
            authProviderData = MatrixClientAuthProviderData.classicLoginWith(baseUrl) {
                it.register("user1", password)
            }.getOrThrow()
        ).getOrThrow()
        client.startSync()
        client.syncState.firstWithTimeout { it == SyncState.RUNNING }
    }

    @AfterTest
    fun afterEach() {
        client.close()
    }

    @Test
    fun shouldUploadAndDownloadMedia(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30_000) {
            val cacheUri =
                client.media.prepareUploadMedia("Test".toByteArray().toByteArrayFlow(), ContentType.Text.Plain)
            val mxcUri = client.media.uploadMedia(cacheUri).getOrThrow()
            client.media.getMedia(mxcUri).getOrThrow().toByteArray()?.decodeToString() shouldBe "Test"
        }
    }

    val miniPng = listOf(
        0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x03, 0x00, 0x00, 0x00, 0x66, 0xBC, 0x3A,
        0x25, 0x00, 0x00, 0x00, 0x03, 0x50, 0x4C, 0x54, 0x45, 0xB5, 0xD0, 0xD0, 0x63, 0x04, 0x16, 0xEA,
        0x00, 0x00, 0x00, 0x1F, 0x49, 0x44, 0x41, 0x54, 0x68, 0x81, 0xED, 0xC1, 0x01, 0x0D, 0x00, 0x00,
        0x00, 0xC2, 0xA0, 0xF7, 0x4F, 0x6D, 0x0E, 0x37, 0xA0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0xBE, 0x0D, 0x21, 0x00, 0x00, 0x01, 0x9A, 0x60, 0xE1, 0xD5, 0x00, 0x00, 0x00, 0x00, 0x49,
        0x45, 0x4E, 0x44, 0xAE, 0x42, 0x60, 0x82
    ).let { ByteArray(it.size) { pos -> it[pos].toByte() } }

    @Test
    fun shouldDownloadThumbnail(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30_000) {
            val cacheUri =
                client.media.prepareUploadMedia(miniPng.toByteArrayFlow(), ContentType.Image.PNG)
            val mxcUri = client.media.uploadMedia(cacheUri).getOrThrow()
            client.media.getThumbnail(mxcUri, 100, 100).getOrThrow().toByteArray()?.size shouldNotBe 0
        }
    }
}