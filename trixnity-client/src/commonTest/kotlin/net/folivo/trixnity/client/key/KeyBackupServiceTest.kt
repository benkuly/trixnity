package net.folivo.trixnity.client.key

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.timing.continually
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.beEmpty
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.MatrixServerException
import net.folivo.trixnity.client.api.SyncApiClient.SyncState.RUNNING
import net.folivo.trixnity.client.api.model.ErrorResponse
import net.folivo.trixnity.client.api.model.keys.GetRoomKeysVersionResponse
import net.folivo.trixnity.client.api.model.keys.SetRoomKeysResponse
import net.folivo.trixnity.client.api.model.keys.SetRoomKeysVersionRequest
import net.folivo.trixnity.client.crypto.OlmService
import net.folivo.trixnity.client.crypto.OlmSignService.SignWith.Custom
import net.folivo.trixnity.client.crypto.OlmSignService.SignWith.DeviceKey
import net.folivo.trixnity.client.store.AllowedSecretType.M_MEGOLM_BACKUP_V1
import net.folivo.trixnity.client.store.InMemoryStore
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.StoredInboundMegolmSession
import net.folivo.trixnity.client.store.StoredSecret
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.MegolmBackupV1EventContent
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.model.keys.KeyAlgorithm
import net.folivo.trixnity.core.model.keys.RoomKeyBackupAlgorithm
import net.folivo.trixnity.core.model.keys.RoomKeyBackupAuthData.RoomKeyBackupV1AuthData
import net.folivo.trixnity.core.model.keys.RoomKeyBackupData
import net.folivo.trixnity.core.model.keys.RoomKeyBackupSessionData.EncryptedRoomKeyBackupV1SessionData
import net.folivo.trixnity.core.model.keys.RoomKeyBackupSessionData.EncryptedRoomKeyBackupV1SessionData.RoomKeyBackupV1SessionData
import net.folivo.trixnity.core.model.keys.keysOf
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.olm.*
import kotlin.random.Random
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class KeyBackupServiceTest : ShouldSpec(body)

private val body: ShouldSpec.() -> Unit = {
    timeout = 10_000

    val ownUserId = UserId("alice", "server")
    val ownDeviceId = "DEV"

    lateinit var scope: CoroutineScope
    lateinit var store: Store
    val api = mockk<MatrixApiClient>()
    val olm = mockk<OlmService>()
    val json = createMatrixJson()
    lateinit var cut: KeyBackupService

    mockkStatic(::keyBackupCanBeTrusted)

    beforeTest {
        coEvery { api.json } returns json
        scope = CoroutineScope(Dispatchers.Default)
        store = InMemoryStore(scope).apply { init() }
        cut = KeyBackupService("", ownUserId, ownDeviceId, store, api, olm)
    }
    afterTest {
        scope.cancel()
        clearAllMocks()
    }

    suspend fun setVersion(keyBackupPrivateKey: String, keyBackupPublicKey: String, version: String) {
        val keyVersion = GetRoomKeysVersionResponse.V1(
            authData = RoomKeyBackupV1AuthData(
                publicKey = Curve25519Key(null, keyBackupPublicKey),
                signatures = mapOf(ownUserId to keysOf(Ed25519Key("DEV", "s1"), Ed25519Key("MSK", "s2")))
            ),
            count = 1,
            etag = "etag",
            version = version
        )
        store.keys.secrets.value = mapOf(M_MEGOLM_BACKUP_V1 to StoredSecret(mockk(), keyBackupPrivateKey))
        coEvery { keyBackupCanBeTrusted(any(), any(), any(), any()) } returns true
        coEvery { api.keys.getRoomKeysVersion() } returns Result.success(keyVersion)
        coEvery { olm.sign.signatures(any<RoomKeyBackupV1AuthData>()) } returns mapOf(
            ownUserId to keysOf(Ed25519Key("DEV", "s1"))
        )
        scope.launch {
            cut.setAndSignNewKeyBackupVersion()
        }.also {
            cut.version.first { newVersion -> newVersion == keyVersion }
            it.cancel()
        }
    }

    context(KeyBackupService::setAndSignNewKeyBackupVersion.name) {
        val keyVersion = GetRoomKeysVersionResponse.V1(
            authData = RoomKeyBackupV1AuthData(
                publicKey = Curve25519Key(null, "pub"),
                signatures = mapOf(ownUserId to keysOf(Ed25519Key("DEV", "s1"), Ed25519Key("MSK", "s2")))
            ),
            count = 1,
            etag = "etag",
            version = "1"
        )
        beforeTest {
            coEvery { api.sync.currentSyncState } returns MutableStateFlow(RUNNING)
        }
        should("set version to null when algorithm not supported") {
            coEvery { api.keys.getRoomKeysVersion() } returns Result.success(keyVersion) andThen Result.success(
                GetRoomKeysVersionResponse.Unknown(
                    JsonObject(mapOf()),
                    RoomKeyBackupAlgorithm.Unknown("")
                )
            )
            coEvery { olm.sign.signatures(any<RoomKeyBackupV1AuthData>()) } returns mapOf(
                ownUserId to keysOf(Ed25519Key("DEV", "s1"))
            )
            coEvery { keyBackupCanBeTrusted(any(), any(), any(), any()) } returns true
            val job = launch {
                cut.setAndSignNewKeyBackupVersion()
            }
            cut.version.value shouldBe null
            store.keys.secrets.value = mapOf(M_MEGOLM_BACKUP_V1 to StoredSecret(mockk(), "pri1"))
            cut.version.first { it != null } shouldBe keyVersion
            store.keys.secrets.value = mapOf(M_MEGOLM_BACKUP_V1 to StoredSecret(mockk(), "pri2"))
            cut.version.first { it == null } shouldBe null
            job.cancel()
        }
        context("key backup can be trusted") {
            beforeTest {
                coEvery { keyBackupCanBeTrusted(any(), any(), any(), any()) } returns true
            }
            should("just set version when already signed") {
                coEvery { api.keys.getRoomKeysVersion() } returns Result.success(keyVersion)
                coEvery { olm.sign.signatures(any<RoomKeyBackupV1AuthData>()) } returns mapOf(
                    ownUserId to keysOf(Ed25519Key("DEV", "s1"))
                )
                val job = launch {
                    cut.setAndSignNewKeyBackupVersion()
                }
                store.keys.secrets.value = mapOf(M_MEGOLM_BACKUP_V1 to StoredSecret(mockk(), "pri"))
                cut.version.first { it != null } shouldBe keyVersion
                job.cancel()
            }
            should("set version and sign when not signed by own device") {
                coEvery { api.keys.getRoomKeysVersion() } returns Result.success(keyVersion)
                coEvery { olm.sign.signatures(any<RoomKeyBackupV1AuthData>()) } returns mapOf(
                    ownUserId to keysOf(Ed25519Key("DEV", "s24"))
                )
                coEvery { api.keys.setRoomKeysVersion(any()) } returns Result.success("1")
                val job = launch {
                    cut.setAndSignNewKeyBackupVersion()
                }
                store.keys.secrets.value = mapOf(M_MEGOLM_BACKUP_V1 to StoredSecret(mockk(), "pri"))
                cut.version.first { it != null } shouldBe keyVersion
                job.cancel()
                coVerify {
                    api.keys.setRoomKeysVersion(coWithArg {
                        it.shouldBeInstanceOf<SetRoomKeysVersionRequest.V1>()
                        it.version shouldBe "1"
                        it.authData.publicKey shouldBe Curve25519Key(null, "pub")
                        it.authData.signatures shouldBe mapOf(
                            ownUserId to keysOf(
                                Ed25519Key("DEV", "s24"),
                                Ed25519Key("MSK", "s2")
                            )
                        )
                    })
                }
            }
        }
        context("key backup cannot be trusted") {
            beforeTest {
                coEvery { keyBackupCanBeTrusted(any(), any(), any(), any()) } returns true andThen false
            }
            should("set version to null, remove secret and remove signatures when signed by own device") {
                coEvery { api.keys.getRoomKeysVersion() } returns Result.success(keyVersion)
                coEvery { olm.sign.signatures(any<RoomKeyBackupV1AuthData>()) } returns mapOf(
                    ownUserId to keysOf(Ed25519Key("DEV", "s1"))
                )
                coEvery { api.keys.setRoomKeysVersion(any()) } returns Result.success("1")
                val job = launch {
                    cut.setAndSignNewKeyBackupVersion()
                }
                store.keys.secrets.value = mapOf(M_MEGOLM_BACKUP_V1 to StoredSecret(mockk(), "pri1"))
                cut.version.first { it != null } shouldBe keyVersion
                store.keys.secrets.value = mapOf(M_MEGOLM_BACKUP_V1 to StoredSecret(mockk(), "pri2"))
                cut.version.first { it == null } shouldBe null

                job.cancel()
                coVerify {
                    api.keys.setRoomKeysVersion(coWithArg {
                        it.shouldBeInstanceOf<SetRoomKeysVersionRequest.V1>()
                        it.version shouldBe "1"
                        it.authData.publicKey shouldBe Curve25519Key(null, "pub")
                        it.authData.signatures shouldBe mapOf(
                            ownUserId to keysOf(
                                Ed25519Key("MSK", "s2")
                            )
                        )
                    })
                }
                store.keys.secrets.value.shouldBeEmpty()
            }
        }
    }
    context(KeyBackupService::handleLoadMegolmSessionQueue.name) {
        val roomId = RoomId("room", "server")
        val sessionId = "sessionId"
        val version = "1"
        val senderKey = Curve25519Key(null, "senderKey")
        beforeTest {
            scope.launch(start = CoroutineStart.UNDISPATCHED) { cut.handleLoadMegolmSessionQueue() }
            coEvery { api.sync.currentSyncState } returns MutableStateFlow(RUNNING)
        }
        should("do nothing when version is null") {
            cut.loadMegolmSession(roomId, sessionId, senderKey)
            continually(500.milliseconds) {
                coVerify { api.keys wasNot Called }
                store.olm.getInboundMegolmSession(senderKey, sessionId, roomId) shouldBe null
            }
        }
        context("megolm session on server") {
            val sessionKey = freeAfter(OlmOutboundGroupSession.create()) { os ->
                os.encrypt("bla")
                freeAfter(OlmInboundGroupSession.create(os.sessionKey)) { it.export(1) }
            }
            val (keyBackupPrivateKey, keyBackupPublicKey) = freeAfter(OlmPkDecryption.create(null)) { it.privateKey to it.publicKey }
            val encryptedRoomKeyBackupV1SessionData = freeAfter(OlmPkEncryption.create(keyBackupPublicKey)) {
                val e = it.encrypt(
                    json.encodeToString(
                        RoomKeyBackupV1SessionData(
                            senderKey, listOf(), mapOf(KeyAlgorithm.Ed25519.name to "edKey"), sessionKey
                        )
                    )
                )
                EncryptedRoomKeyBackupV1SessionData(
                    ciphertext = e.cipherText,
                    mac = e.mac,
                    ephemeral = e.ephemeralKey
                )
            }
            beforeTest {
                setVersion(keyBackupPrivateKey, keyBackupPublicKey, version)
            }
            context("without error") {
                beforeTest {
                    coEvery {
                        api.keys.getRoomKeys<EncryptedRoomKeyBackupV1SessionData>(any(), version, roomId, sessionId)
                    } returns Result.success(
                        RoomKeyBackupData(1, 0, false, encryptedRoomKeyBackupV1SessionData)
                    )
                }
                should("fetch megolm session and save, when index is older than known index") {
                    val currentSession = StoredInboundMegolmSession(
                        senderKey = senderKey,
                        sessionId = sessionId,
                        roomId = roomId,
                        firstKnownIndex = 24,
                        hasBeenBackedUp = true,
                        isTrusted = true,
                        senderSigningKey = Ed25519Key(null, "edKey"),
                        forwardingCurve25519KeyChain = listOf(),
                        pickled = "pickle"
                    )
                    store.olm.updateInboundMegolmSession(senderKey, sessionId, roomId) { currentSession }

                    cut.loadMegolmSession(roomId, sessionId, senderKey)
                    assertSoftly(
                        store.olm.getInboundMegolmSession(senderKey, sessionId, roomId, scope)
                            .first { it?.firstKnownIndex == 1L }
                    ) {
                        assertNotNull(this)
                        this.senderKey shouldBe senderKey
                        this.sessionId shouldBe sessionId
                        this.roomId shouldBe roomId
                        this.firstKnownIndex shouldBe 1
                        this.hasBeenBackedUp shouldBe true
                        this.isTrusted shouldBe false
                        this.pickled shouldNotBe "pickle"
                    }
                }
                should("fetch megolm session, but keep old session, when index is older than known index") {
                    val currentSession = StoredInboundMegolmSession(
                        senderKey = senderKey,
                        sessionId = sessionId,
                        roomId = roomId,
                        firstKnownIndex = 0,
                        hasBeenBackedUp = true,
                        isTrusted = true,
                        senderSigningKey = Ed25519Key(null, "key"),
                        forwardingCurve25519KeyChain = listOf(),
                        pickled = "pickle"
                    )
                    store.olm.updateInboundMegolmSession(senderKey, sessionId, roomId) { currentSession }

                    cut.loadMegolmSession(roomId, sessionId, senderKey)
                    continually(300.milliseconds) {
                        store.olm.getInboundMegolmSession(senderKey, sessionId, roomId) shouldBe currentSession
                    }
                }
            }
            context("with delay") {
                val allLoadMegolmSessionsCalled = MutableStateFlow(false)
                beforeTest {
                    allLoadMegolmSessionsCalled.value = false
                    coEvery {
                        api.keys.getRoomKeys<EncryptedRoomKeyBackupV1SessionData>(any(), version, roomId, sessionId)
                    } coAnswers {
                        allLoadMegolmSessionsCalled.first { it }
                        Result.success(
                            RoomKeyBackupData(0, 0, false, encryptedRoomKeyBackupV1SessionData)
                        )
                    }
                }
                should("fetch one megolm session only once at a time") {
                    repeat(20) {
                        cut.loadMegolmSession(roomId, sessionId, senderKey)
                    }
                    allLoadMegolmSessionsCalled.value = true
                    store.olm.getInboundMegolmSession(senderKey, sessionId, roomId, scope).first { it != null }
                    api.keys.let {
                        coVerify(exactly = 1) {
                            it.getRoomKeys<EncryptedRoomKeyBackupV1SessionData>(any(), version, roomId, sessionId)
                        }
                    }
                }
            }
            context("with error") {
                beforeTest {
                    coEvery {
                        api.keys.getRoomKeys<EncryptedRoomKeyBackupV1SessionData>(any(), version, roomId, sessionId)
                    } returns Result.failure(RuntimeException("")) andThen Result.success(
                        RoomKeyBackupData(0, 0, false, encryptedRoomKeyBackupV1SessionData)
                    )
                }
                should("retry fetch megolm session") {
                    cut.loadMegolmSession(roomId, sessionId, senderKey)
                    assertSoftly(store.olm.getInboundMegolmSession(senderKey, sessionId, roomId, scope)
                        .first { it != null }) {
                        assertNotNull(this)
                        this.senderKey shouldBe senderKey
                        this.sessionId shouldBe sessionId
                        this.roomId shouldBe roomId
                        this.firstKnownIndex shouldBe 1
                        this.hasBeenBackedUp shouldBe true
                        this.pickled shouldNot beEmpty()
                    }
                    api.keys.let {
                        coVerify(exactly = 2) {
                            it.getRoomKeys<EncryptedRoomKeyBackupV1SessionData>(any(), version, roomId, sessionId)
                        }
                    }
                }
            }
        }
    }
    context(KeyBackupService::bootstrapRoomKeyBackup.name) {
        should("upload new room key version and add secret to account data") {
            val (masterSigningPrivateKey, masterSigningPublicKey) =
                freeAfter(OlmPkSigning.create(null)) { it.privateKey to it.publicKey }
            coEvery { api.keys.setRoomKeysVersion(any()) } returns Result.success("1")
            coEvery { api.users.setAccountData(any(), ownUserId) } returns Result.success(Unit)
            coEvery { olm.sign.signatures<RoomKeyBackupV1AuthData>(any(), any(), signWith = DeviceKey) } returns mapOf(
                ownUserId to keysOf(Ed25519Key(ownDeviceId, "s1"))
            )
            coEvery {
                olm.sign.signatures<RoomKeyBackupV1AuthData>(
                    any(), any(),
                    signWith = Custom(masterSigningPrivateKey, masterSigningPublicKey)
                )
            } returns mapOf(
                ownUserId to keysOf(Ed25519Key(masterSigningPublicKey, "s2"))
            )

            cut.bootstrapRoomKeyBackup(
                Random.nextBytes(32),
                "defaultKeyId",
                masterSigningPrivateKey,
                masterSigningPublicKey
            ).getOrThrow()

            coVerify {
                api.keys.setRoomKeysVersion(withArg { request ->
                    request.shouldBeInstanceOf<SetRoomKeysVersionRequest.V1>()
                    request.authData.publicKey.value shouldNot beEmpty()
                    request.authData.signatures[ownUserId]?.keys shouldBe setOf(
                        Ed25519Key(ownDeviceId, "s1"),
                        Ed25519Key(masterSigningPublicKey, "s2")
                    )
                    request.version shouldBe null
                })
                api.users.setAccountData(coWithArg {
                    it.shouldBeInstanceOf<MegolmBackupV1EventContent>()
                }, ownUserId)
            }
            store.keys.secrets.value.keys shouldContain M_MEGOLM_BACKUP_V1
        }
    }
    context(KeyBackupService::uploadRoomKeyBackup.name) {
        val room1 = RoomId("room1", "server")
        val room2 = RoomId("room2", "server")
        val sessionId1 = "session1"
        val sessionId2 = "session2"
        val pickle1 = freeAfter(OlmOutboundGroupSession.create()) { os ->
            freeAfter(OlmInboundGroupSession.create(os.sessionKey)) { it.pickle("") }
        }
        val pickle2 = freeAfter(OlmOutboundGroupSession.create()) { os ->
            freeAfter(OlmInboundGroupSession.create(os.sessionKey)) { it.pickle("") }
        }
        val session1 = StoredInboundMegolmSession(
            senderKey = Curve25519Key(null, "curve1"),
            sessionId = sessionId1,
            roomId = room1,
            firstKnownIndex = 2,
            hasBeenBackedUp = false,
            isTrusted = true,
            senderSigningKey = Ed25519Key(null, "ed1"),
            forwardingCurve25519KeyChain = listOf(),
            pickled = pickle1
        )
        val session2 = StoredInboundMegolmSession(
            senderKey = Curve25519Key(null, "curve2"),
            sessionId = sessionId2,
            roomId = room2,
            firstKnownIndex = 4,
            hasBeenBackedUp = false,
            isTrusted = true,
            senderSigningKey = Ed25519Key(null, "ed2"),
            forwardingCurve25519KeyChain = listOf(Curve25519Key(null, "curve2")),
            pickled = pickle2
        )
        beforeTest {
            coEvery { api.sync.currentSyncState } returns MutableStateFlow(RUNNING)
            scope.launch(start = CoroutineStart.UNDISPATCHED) { cut.uploadRoomKeyBackup() }
            session1.run { store.olm.updateInboundMegolmSession(senderKey, sessionId, roomId) { this } }
            session2.run { store.olm.updateInboundMegolmSession(senderKey, sessionId, roomId) { this } }
        }
        should("do nothing when version is null") {
            continually(2.seconds) {
                coVerify(exactly = 0) {
                    api.keys.setRoomKeys<EncryptedRoomKeyBackupV1SessionData>(any(), "1", any())
                }
                store.olm.notBackedUpInboundMegolmSessions.value.size shouldBe 2
                session1.run {
                    store.olm.getInboundMegolmSession(senderKey, sessionId, roomId)
                }?.hasBeenBackedUp shouldBe false
                session2.run {
                    store.olm.getInboundMegolmSession(senderKey, sessionId, roomId)
                }?.hasBeenBackedUp shouldBe false
            }
        }
        should("do nothing when not backed up is empty") {
            session1.run {
                store.olm.updateInboundMegolmSession(senderKey, sessionId, roomId) { this.copy(hasBeenBackedUp = true) }
            }
            session2.run {
                store.olm.updateInboundMegolmSession(senderKey, sessionId, roomId) { this.copy(hasBeenBackedUp = true) }
            }
            val (keyBackupPrivateKey, keyBackupPublicKey) = freeAfter(OlmPkDecryption.create(null)) { it.privateKey to it.publicKey }
            setVersion(keyBackupPrivateKey, keyBackupPublicKey, "1")
            val keys = api.keys
            continually(2.seconds) {
                coVerify(exactly = 0) {
                    keys.setRoomKeys<EncryptedRoomKeyBackupV1SessionData>(any(), "1", any())
                }
            }
        }
        should("upload key backup and set flag, that session has been backed up") {
            coEvery {
                api.keys.setRoomKeys<EncryptedRoomKeyBackupV1SessionData>(any(), version = any(), backup = any())
            } returns Result.success(SetRoomKeysResponse(2, "etag"))
            val (keyBackupPrivateKey, keyBackupPublicKey) = freeAfter(OlmPkDecryption.create(null)) { it.privateKey to it.publicKey }
            setVersion(keyBackupPrivateKey, keyBackupPublicKey, "1")
            coVerify(timeout = 2_000) {
                api.keys.setRoomKeys<EncryptedRoomKeyBackupV1SessionData>(any(), "1", coWithArg {
                    it.rooms.keys shouldBe setOf(RoomId("room1", "server"), RoomId("room2", "server"))
                    assertSoftly(it.rooms[room1]?.sessions?.get(sessionId1)) {
                        assertNotNull(this)
                        this.firstMessageIndex shouldBe 2
                        this.isVerified shouldBe true
                        this.forwardedCount shouldBe 0
                    }
                    assertSoftly(it.rooms[room2]?.sessions?.get(sessionId2)) {
                        assertNotNull(this)
                        this.firstMessageIndex shouldBe 4
                        this.isVerified shouldBe true
                        this.forwardedCount shouldBe 1
                    }
                })
            }
            store.olm.notBackedUpInboundMegolmSessions.first { it.isEmpty() }
            session1.run {
                store.olm.getInboundMegolmSession(senderKey, sessionId, roomId)
            }?.hasBeenBackedUp shouldBe true
            session2.run {
                store.olm.getInboundMegolmSession(senderKey, sessionId, roomId)
            }?.hasBeenBackedUp shouldBe true
        }
        should("update key backup version when error is M_WRONG_ROOM_KEYS_VERSION") {
            coEvery {
                api.keys.setRoomKeys<EncryptedRoomKeyBackupV1SessionData>(any(), version = any(), backup = any())
            } returns Result.failure(
                MatrixServerException(HttpStatusCode.Forbidden, ErrorResponse.WrongRoomKeysVersion())
            ) andThen Result.success(SetRoomKeysResponse(2, "etag"))
            val (keyBackupPrivateKey, keyBackupPublicKey) = freeAfter(OlmPkDecryption.create(null)) { it.privateKey to it.publicKey }
            setVersion(keyBackupPrivateKey, keyBackupPublicKey, "1")
            store.olm.notBackedUpInboundMegolmSessions.first { it.isEmpty() }
            val keys = api.keys
            coVerify(exactly = 2) {
                keys.getRoomKeysVersion()
            }
        }
    }
}