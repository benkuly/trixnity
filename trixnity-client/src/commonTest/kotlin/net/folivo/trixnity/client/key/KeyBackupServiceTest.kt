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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.mocks.SignServiceMock
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.client.SyncState.RUNNING
import net.folivo.trixnity.clientserverapi.model.keys.*
import net.folivo.trixnity.clientserverapi.model.users.SetGlobalAccountData
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.MegolmBackupV1EventContent
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.model.keys.RoomKeyBackupAuthData.RoomKeyBackupV1AuthData
import net.folivo.trixnity.core.model.keys.RoomKeyBackupSessionData.EncryptedRoomKeyBackupV1SessionData
import net.folivo.trixnity.core.model.keys.RoomKeyBackupSessionData.EncryptedRoomKeyBackupV1SessionData.RoomKeyBackupV1SessionData
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.SecretType.M_MEGOLM_BACKUP_V1
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmSession
import net.folivo.trixnity.olm.*
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
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
    lateinit var accountStore: AccountStore
    lateinit var olmCryptoStore: OlmCryptoStore
    lateinit var keyStore: KeyStore
    lateinit var apiConfig: PortableMockEngineConfig

    lateinit var olmSignMock: SignServiceMock
    val json = createMatrixEventJson()
    val mappings = createEventContentSerializerMappings()
    lateinit var cut: KeyBackupServiceImpl

    lateinit var validKeyBackupPrivateKey: String
    lateinit var validKeyBackupPublicKey: String

    val currentSyncState = MutableStateFlow(SyncState.STOPPED)
    beforeTest {
        olmSignMock = SignServiceMock()
        val (newValidKeyBackupPrivateKey, newValidKeyBackupPublicKey) = freeAfter(OlmPkDecryption.create(null)) { it.privateKey to it.publicKey }
        validKeyBackupPrivateKey = newValidKeyBackupPrivateKey
        validKeyBackupPublicKey = newValidKeyBackupPublicKey
        scope = CoroutineScope(Dispatchers.Default)
        accountStore = getInMemoryAccountStore(scope)
        olmCryptoStore = getInMemoryOlmStore(scope)
        keyStore = getInMemoryKeyStore(scope)
        accountStore.updateAccount { it.copy(olmPickleKey = "") }
        val (api, newApiConfig) = mockMatrixClientServerApiClient(json)
        apiConfig = newApiConfig
        cut = KeyBackupServiceImpl(
            UserInfo(ownUserId, ownDeviceId, Ed25519Key(null, ""), Curve25519Key(null, "")),
            accountStore,
            olmCryptoStore,
            keyStore,
            api,
            olmSignMock,
            CurrentSyncState(currentSyncState),
            scope,
        )
        cut.startInCoroutineScope(scope)
    }
    afterTest {
        scope.cancel()
    }

    suspend fun setVersion(
        keyBackupPrivateKey: String,
        keyBackupPublicKey: String,
        version: String
    ) {
        val keyVersion = GetRoomKeysBackupVersionResponse.V1(
            authData = RoomKeyBackupV1AuthData(
                publicKey = Curve25519Key(null, keyBackupPublicKey),
                signatures = mapOf(ownUserId to keysOf(Ed25519Key("DEV", "s1"), Ed25519Key("MSK", "s2")))
            ),
            count = 1,
            etag = "etag",
            version = version
        )
        keyStore.updateSecrets {
            mapOf(
                M_MEGOLM_BACKUP_V1 to StoredSecret(
                    Event.GlobalAccountDataEvent(MegolmBackupV1EventContent(mapOf())),
                    keyBackupPrivateKey
                )
            )
        }
        olmSignMock.returnSignatures = listOf(mapOf(ownUserId to keysOf(Ed25519Key("DEV", "s1"))))
        apiConfig.endpoints {
            matrixJsonEndpoint(json, mappings, GetRoomKeyBackupVersion()) {
                keyVersion
            }
        }
        cut.version.first { newVersion -> newVersion == keyVersion }
    }

    context(KeyBackupServiceImpl::setAndSignNewKeyBackupVersion.name) {
        lateinit var keyVersion: GetRoomKeysBackupVersionResponse.V1
        beforeTest {
            currentSyncState.value = RUNNING
            keyVersion = GetRoomKeysBackupVersionResponse.V1(
                authData = RoomKeyBackupV1AuthData(
                    publicKey = Curve25519Key(null, validKeyBackupPublicKey),
                    signatures = mapOf(ownUserId to keysOf(Ed25519Key("DEV", "s1"), Ed25519Key("MSK", "s2")))
                ),
                count = 1,
                etag = "etag",
                version = "1"
            )
        }
        should("set version to null when algorithm not supported") {
            apiConfig.endpoints {
                matrixJsonEndpoint(json, mappings, GetRoomKeyBackupVersion()) {
                    keyVersion
                }
                matrixJsonEndpoint(json, mappings, GetRoomKeyBackupVersion()) {
                    GetRoomKeysBackupVersionResponse.Unknown(
                        JsonObject(mapOf()),
                        RoomKeyBackupAlgorithm.Unknown("")
                    )
                }
            }
            olmSignMock.returnSignatures = listOf(mapOf(ownUserId to keysOf(Ed25519Key("DEV", "s1"))))
            cut.version.value shouldBe null
            keyStore.updateSecrets {
                mapOf(
                    M_MEGOLM_BACKUP_V1 to StoredSecret(
                        Event.GlobalAccountDataEvent(MegolmBackupV1EventContent(mapOf())),
                        validKeyBackupPrivateKey
                    )
                )
            }
            cut.version.first { it != null } shouldBe keyVersion
            keyStore.updateSecrets {
                mapOf(
                    M_MEGOLM_BACKUP_V1 to StoredSecret(
                        Event.GlobalAccountDataEvent(MegolmBackupV1EventContent(mapOf("" to JsonPrimitive("something")))),
                        validKeyBackupPrivateKey
                    )
                )
            }
            cut.version.first { it == null } shouldBe null
        }
        context("key backup can be trusted") {
            should("just set version when already signed") {
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, GetRoomKeyBackupVersion()) {
                        keyVersion
                    }
                }
                olmSignMock.returnSignatures = listOf(mapOf(ownUserId to keysOf(Ed25519Key("DEV", "s1"))))
                keyStore.updateSecrets {
                    mapOf(
                        M_MEGOLM_BACKUP_V1 to StoredSecret(
                            Event.GlobalAccountDataEvent(
                                MegolmBackupV1EventContent(mapOf())
                            ), validKeyBackupPrivateKey
                        )
                    )
                }
                cut.version.first { it != null } shouldBe keyVersion
            }
            should("set version and sign when not signed by own device") {
                var setRoomKeyBackupVersionCalled = false
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, GetRoomKeyBackupVersion()) {
                        keyVersion
                    }
                    matrixJsonEndpoint(json, mappings, SetRoomKeyBackupVersionByVersion("1")) {
                        setRoomKeyBackupVersionCalled = true
                        it.shouldBeInstanceOf<SetRoomKeyBackupVersionRequest.V1>()
                        it.version shouldBe "1"
                        it.authData.publicKey shouldBe Curve25519Key(null, validKeyBackupPublicKey)
                        it.authData.signatures shouldBe mapOf(
                            ownUserId to keysOf(
                                Ed25519Key("DEV", "s24"),
                                Ed25519Key("MSK", "s2")
                            )
                        )
                        SetRoomKeyBackupVersion.Response("1")
                    }
                }
                olmSignMock.returnSignatures = listOf(mapOf(ownUserId to keysOf(Ed25519Key("DEV", "s24"))))
                keyStore.updateSecrets {
                    mapOf(
                        M_MEGOLM_BACKUP_V1 to StoredSecret(
                            Event.GlobalAccountDataEvent(
                                MegolmBackupV1EventContent(mapOf())
                            ), validKeyBackupPrivateKey
                        )
                    )
                }
                cut.version.first { it != null } shouldBe keyVersion
                setRoomKeyBackupVersionCalled shouldBe true
            }
        }
        context("key backup cannot be trusted") {
            should("set version to null, remove secret and remove signatures when signed by own device") {
                var setRoomKeyBackupVersionCalled = false
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, GetRoomKeyBackupVersion()) {
                        keyVersion
                    }
                    matrixJsonEndpoint(json, mappings, GetRoomKeyBackupVersion()) {
                        keyVersion
                    }
                    matrixJsonEndpoint(json, mappings, SetRoomKeyBackupVersionByVersion("1")) {
                        setRoomKeyBackupVersionCalled = true
                        it.shouldBeInstanceOf<SetRoomKeyBackupVersionRequest.V1>()
                        it.version shouldBe "1"
                        it.authData.publicKey shouldBe Curve25519Key(null, validKeyBackupPublicKey)
                        it.authData.signatures shouldBe mapOf(
                            ownUserId to keysOf(
                                Ed25519Key("MSK", "s2")
                            )
                        )
                        SetRoomKeyBackupVersion.Response("1")
                    }
                }
                olmSignMock.returnSignatures = listOf(mapOf(ownUserId to keysOf(Ed25519Key("DEV", "s1"))))
                keyStore.updateSecrets {
                    mapOf(
                        M_MEGOLM_BACKUP_V1 to StoredSecret(
                            Event.GlobalAccountDataEvent(
                                MegolmBackupV1EventContent(mapOf())
                            ), validKeyBackupPrivateKey
                        )
                    )
                }
                cut.version.first { it != null } shouldBe keyVersion
                keyStore.updateSecrets {
                    mapOf(
                        M_MEGOLM_BACKUP_V1 to StoredSecret(
                            Event.GlobalAccountDataEvent(
                                MegolmBackupV1EventContent(mapOf())
                            ), "invalidPri"
                        )
                    )
                }
                cut.version.first { it == null } shouldBe null

                setRoomKeyBackupVersionCalled shouldBe true
                keyStore.getSecrets().shouldBeEmpty()
            }
        }
    }
    context(KeyBackupServiceImpl::loadMegolmSession.name) {
        val roomId = RoomId("room", "server")
        val sessionId = "sessionId"
        val version = "1"
        val senderKey = Curve25519Key(null, "senderKey")
        beforeTest {
            currentSyncState.value = RUNNING
        }
        should("do nothing when version is null") {
            val job = launch {
                cut.loadMegolmSession(roomId, sessionId)
            }
            continually(500.milliseconds) {
                olmCryptoStore.getInboundMegolmSession(sessionId, roomId).first() shouldBe null
            }
            job.cancel()
        }
        context("megolm session on server") {
            lateinit var encryptedRoomKeyBackupV1SessionData: EncryptedRoomKeyBackupV1SessionData
            beforeTest {
                val sessionKey = freeAfter(OlmOutboundGroupSession.create()) { os ->
                    os.encrypt("bla")
                    freeAfter(OlmInboundGroupSession.create(os.sessionKey)) { it.export(1) }
                }
                val (keyBackupPrivateKey, keyBackupPublicKey) = freeAfter(OlmPkDecryption.create(null)) { it.privateKey to it.publicKey }
                encryptedRoomKeyBackupV1SessionData = freeAfter(OlmPkEncryption.create(keyBackupPublicKey)) {
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
                setVersion(keyBackupPrivateKey, keyBackupPublicKey, version)
            }
            context("without error") {
                beforeTest {
                    apiConfig.endpoints {
                        matrixJsonEndpoint(json, mappings, GetRoomKeyBackupData(roomId, sessionId, version)) {
                            RoomKeyBackupData(1, 0, false, encryptedRoomKeyBackupV1SessionData)
                        }
                    }
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
                    olmCryptoStore.updateInboundMegolmSession(sessionId, roomId) { currentSession }

                    cut.loadMegolmSession(roomId, sessionId)
                    assertSoftly(
                        olmCryptoStore.getInboundMegolmSession(sessionId, roomId)
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
                    olmCryptoStore.updateInboundMegolmSession(sessionId, roomId) { currentSession }

                    cut.loadMegolmSession(roomId, sessionId)
                    continually(300.milliseconds) {
                        olmCryptoStore.getInboundMegolmSession(sessionId, roomId).first() shouldBe currentSession
                    }
                }
            }
            context("with delay") {
                val allLoadMegolmSessionsCalled = MutableStateFlow(false)
                var getRoomKeyBackupDataCalled = false
                beforeTest {
                    allLoadMegolmSessionsCalled.value = false
                    apiConfig.endpoints {
                        matrixJsonEndpoint(json, mappings, GetRoomKeyBackupData(roomId, sessionId, version)) {
                            allLoadMegolmSessionsCalled.first { it }
                            getRoomKeyBackupDataCalled = true
                            RoomKeyBackupData(0, 0, false, encryptedRoomKeyBackupV1SessionData)
                        }
                    }
                }
                should("fetch one megolm session only once at a time") {
                    repeat(20) {
                        launch {
                            cut.loadMegolmSession(roomId, sessionId)
                        }
                    }
                    allLoadMegolmSessionsCalled.value = true
                    olmCryptoStore.getInboundMegolmSession(sessionId, roomId).first { it != null }
                    getRoomKeyBackupDataCalled shouldBe true
                }
            }
            context("with error") {
                var getRoomKeyBackupDataCalled = false
                beforeTest {
                    apiConfig.endpoints {
                        matrixJsonEndpoint(
                            json, mappings,
                            GetRoomKeyBackupData(roomId, sessionId, version)
                        ) {
                            throw MatrixServerException(HttpStatusCode.InternalServerError, ErrorResponse.Unknown())
                        }
                        matrixJsonEndpoint(json, mappings, GetRoomKeyBackupData(roomId, sessionId, version)) {
                            getRoomKeyBackupDataCalled = true
                            RoomKeyBackupData(0, 0, false, encryptedRoomKeyBackupV1SessionData)
                        }
                    }
                }
                should("retry fetch megolm session") {
                    cut.loadMegolmSession(roomId, sessionId)
                    assertSoftly(olmCryptoStore.getInboundMegolmSession(sessionId, roomId)
                        .first { it != null }) {
                        assertNotNull(this)
                        this.senderKey shouldBe senderKey
                        this.sessionId shouldBe sessionId
                        this.roomId shouldBe roomId
                        this.firstKnownIndex shouldBe 1
                        this.hasBeenBackedUp shouldBe true
                        this.pickled shouldNot beEmpty()
                    }
                    getRoomKeyBackupDataCalled shouldBe true
                }
            }
        }
    }
    context(KeyBackupServiceImpl::bootstrapRoomKeyBackup.name) {
        should("upload new room key version and add secret to account data") {
            val (masterSigningPrivateKey, masterSigningPublicKey) =
                freeAfter(OlmPkSigning.create(null)) { it.privateKey to it.publicKey }
            var setRoomKeyBackupVersionCalled = false
            var setGlobalAccountDataCalled = false
            apiConfig.endpoints {
                matrixJsonEndpoint(json, mappings, SetRoomKeyBackupVersion()) {
                    setRoomKeyBackupVersionCalled = true
                    it.shouldBeInstanceOf<SetRoomKeyBackupVersionRequest.V1>()
                    it.authData.publicKey.value shouldNot beEmpty()
                    it.authData.signatures[ownUserId]?.keys shouldBe setOf(
                        Ed25519Key(ownDeviceId, "s1"),
                        Ed25519Key(masterSigningPublicKey, "s2")
                    )
                    it.version shouldBe null
                    SetRoomKeyBackupVersion.Response("1")
                }
                matrixJsonEndpoint(json, mappings, GetRoomKeyBackupVersionByVersion("1")) {
                    GetRoomKeysBackupVersionResponse.V1(
                        authData = RoomKeyBackupV1AuthData(
                            publicKey = Curve25519Key(null, "keyBackupPublicKey"),
                        ),
                        count = 1,
                        etag = "etag",
                        version = "1"
                    )
                }
                matrixJsonEndpoint(
                    json, mappings,
                    SetGlobalAccountData(ownUserId, "m.megolm_backup.v1")
                ) {
                    setGlobalAccountDataCalled = true
                }
            }
            olmSignMock.returnSignatures = listOf(
                mapOf(
                    ownUserId to keysOf(Ed25519Key(ownDeviceId, "s1"))
                ),
                mapOf(
                    ownUserId to keysOf(Ed25519Key(masterSigningPublicKey, "s2"))
                )
            )

            cut.bootstrapRoomKeyBackup(
                Random.nextBytes(32),
                "defaultKeyId",
                masterSigningPrivateKey,
                masterSigningPublicKey
            ).getOrThrow()

            setRoomKeyBackupVersionCalled shouldBe true
            setGlobalAccountDataCalled shouldBe true
            keyStore.getSecrets().keys shouldContain M_MEGOLM_BACKUP_V1
        }
    }
    context(KeyBackupServiceImpl::uploadRoomKeyBackup.name) {
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
            currentSyncState.value = RUNNING
            session1.run { olmCryptoStore.updateInboundMegolmSession(sessionId, roomId) { this } }
            session2.run { olmCryptoStore.updateInboundMegolmSession(sessionId, roomId) { this } }
        }
        should("do nothing when version is null") {
            var setRoomKeyBackupVersionCalled = false
            apiConfig.endpoints {
                matrixJsonEndpoint(json, mappings, SetRoomKeyBackupVersion()) {
                    setRoomKeyBackupVersionCalled = true
                    SetRoomKeyBackupVersion.Response("1")
                }
            }
            continually(2.seconds) {
                setRoomKeyBackupVersionCalled shouldBe false
                olmCryptoStore.notBackedUpInboundMegolmSessions.value.size shouldBe 2
                session1.run {
                    olmCryptoStore.getInboundMegolmSession(sessionId, roomId).first()
                }?.hasBeenBackedUp shouldBe false
                session2.run {
                    olmCryptoStore.getInboundMegolmSession(sessionId, roomId).first()
                }?.hasBeenBackedUp shouldBe false
            }
        }
        should("do nothing when not backed up is empty") {
            session1.run {
                olmCryptoStore.updateInboundMegolmSession(sessionId, roomId) { this.copy(hasBeenBackedUp = true) }
            }
            session2.run {
                olmCryptoStore.updateInboundMegolmSession(sessionId, roomId) { this.copy(hasBeenBackedUp = true) }
            }
            val (keyBackupPrivateKey, keyBackupPublicKey) = freeAfter(OlmPkDecryption.create(null)) { it.privateKey to it.publicKey }
            setVersion(keyBackupPrivateKey, keyBackupPublicKey, "1")
            var setRoomKeyBackupVersionCalled = false
            apiConfig.endpoints {
                matrixJsonEndpoint(json, mappings, SetRoomKeyBackupVersion()) {
                    setRoomKeyBackupVersionCalled = true
                    SetRoomKeyBackupVersion.Response("2")
                }
            }
            continually(2.seconds) {
                setRoomKeyBackupVersionCalled shouldBe false
            }
        }
        should("upload key backup and set flag, that session has been backed up") {
            setVersion(validKeyBackupPrivateKey, validKeyBackupPublicKey, "1")

            var setRoomKeyBackupDataCalled = false
            apiConfig.endpoints {
                matrixJsonEndpoint(json, mappings, SetRoomsKeyBackup("1")) {
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
                    setRoomKeyBackupDataCalled = true
                    SetRoomKeysResponse(2, "etag")
                }
            }

            olmCryptoStore.notBackedUpInboundMegolmSessions.first { it.isEmpty() }
            setRoomKeyBackupDataCalled shouldBe true
            session1.run {
                olmCryptoStore.getInboundMegolmSession(sessionId, roomId)
                    .first { it?.hasBeenBackedUp == true }
            }?.hasBeenBackedUp shouldBe true
            session2.run {
                olmCryptoStore.getInboundMegolmSession(sessionId, roomId)
                    .first { it?.hasBeenBackedUp == true }
            }
        }
        should("update key backup version when error is M_WRONG_ROOM_KEYS_VERSION") {
            setVersion(validKeyBackupPrivateKey, validKeyBackupPublicKey, "1")

            var setRoomKeyBackupDataCalled = false
            apiConfig.endpoints {
                matrixJsonEndpoint(json, mappings, SetRoomsKeyBackup("1")) {
                    throw MatrixServerException(HttpStatusCode.Forbidden, ErrorResponse.WrongRoomKeysVersion())
                }
                matrixJsonEndpoint(json, mappings, GetRoomKeyBackupVersion()) {
                    GetRoomKeysBackupVersionResponse.V1(
                        authData = RoomKeyBackupV1AuthData(
                            publicKey = Curve25519Key(null, validKeyBackupPublicKey),
                            signatures = mapOf(ownUserId to keysOf(Ed25519Key("DEV", "s1"), Ed25519Key("MSK", "s2")))
                        ),
                        count = 1,
                        etag = "etag",
                        version = "2"
                    )
                }
                matrixJsonEndpoint(json, mappings, SetRoomsKeyBackup("2")) {
                    setRoomKeyBackupDataCalled = true
                    SetRoomKeysResponse(2, "etag")
                }
            }

            olmCryptoStore.notBackedUpInboundMegolmSessions.first { it.isEmpty() }
            setRoomKeyBackupDataCalled shouldBe true
        }
    }
    context(KeyBackupServiceImpl::keyBackupCanBeTrusted.name) {
        suspend fun roomKeyVersion() = GetRoomKeysBackupVersionResponse.V1(
            authData = RoomKeyBackupV1AuthData(
                publicKey = Curve25519Key(null, validKeyBackupPublicKey),
                signatures = mapOf(ownUserId to keysOf(Ed25519Key("DEVICE", "s1"), Ed25519Key("MSK", "s2")))
            ),
            count = 1,
            etag = "etag",
            version = "1"
        )

        suspend fun deviceKeyTrustLevel(level: KeySignatureTrustLevel) {
            keyStore.updateDeviceKeys(ownUserId) {
                mapOf(
                    "DEVICE" to StoredDeviceKeys(
                        SignedDeviceKeys(
                            DeviceKeys(ownUserId, ownDeviceId, setOf(), keysOf()),
                            null
                        ), level
                    )
                )
            }
        }

        suspend fun masterKeyTrustLevel(level: KeySignatureTrustLevel) {
            keyStore.updateCrossSigningKeys(ownUserId) {
                setOf(
                    StoredCrossSigningKeys(
                        SignedCrossSigningKeys(
                            CrossSigningKeys(
                                ownUserId, setOf(), keysOf(Ed25519Key("MSK", "msk_pub"))
                            ),
                            mapOf()
                        ),
                        level
                    )
                )
            }
        }
        should("return false, when private key is invalid") {
            deviceKeyTrustLevel(KeySignatureTrustLevel.Valid(true))
            cut.keyBackupCanBeTrusted(roomKeyVersion(), "dino") shouldBe false
        }
        should("return false, when key backup version not supported") {
            deviceKeyTrustLevel(KeySignatureTrustLevel.Valid(true))
            cut.keyBackupCanBeTrusted(
                GetRoomKeysBackupVersionResponse.Unknown(
                    JsonObject(mapOf()),
                    RoomKeyBackupAlgorithm.Unknown("")
                ), "dino"
            ) shouldBe false
        }
        should("return false, when public key does not match") {
            deviceKeyTrustLevel(KeySignatureTrustLevel.Valid(true))
            cut.keyBackupCanBeTrusted(
                roomKeyVersion(),
                freeAfter(OlmPkDecryption.create(null)) { it.privateKey },
            ) shouldBe false
        }
//        should("return false, when there is no signature we trust") {
//            deviceKeyTrustLevel(KeySignatureTrustLevel.Valid(false))
//            masterKeyTrustLevel(KeySignatureTrustLevel.Valid(false))
//            keyBackupCanBeTrusted(
//                roomKeyVersion,
//                privateKey,
//                ownUserId,
//                store
//            ) shouldBe false
//        }
        should("return true, when there is a device key is valid+verified") {
            deviceKeyTrustLevel(KeySignatureTrustLevel.Valid(true))
            masterKeyTrustLevel(KeySignatureTrustLevel.Valid(false))
            cut.keyBackupCanBeTrusted(roomKeyVersion(), validKeyBackupPrivateKey) shouldBe true
        }
        should("return true, when there is a device key is crossSigned+verified") {
            deviceKeyTrustLevel(KeySignatureTrustLevel.CrossSigned(true))
            masterKeyTrustLevel(KeySignatureTrustLevel.Valid(false))
            cut.keyBackupCanBeTrusted(roomKeyVersion(), validKeyBackupPrivateKey) shouldBe true
        }
        should("return true, when there is a master key we crossSigned+verified") {
            deviceKeyTrustLevel(KeySignatureTrustLevel.Valid(false))
            masterKeyTrustLevel(KeySignatureTrustLevel.CrossSigned(true))
            cut.keyBackupCanBeTrusted(roomKeyVersion(), validKeyBackupPrivateKey) shouldBe true
        }
        should("return true, when there is a master key we notFullyCrossSigned+verified") {
            deviceKeyTrustLevel(KeySignatureTrustLevel.Valid(false))
            masterKeyTrustLevel(KeySignatureTrustLevel.NotAllDeviceKeysCrossSigned(true))
            cut.keyBackupCanBeTrusted(roomKeyVersion(), validKeyBackupPrivateKey) shouldBe true
        }
    }
}