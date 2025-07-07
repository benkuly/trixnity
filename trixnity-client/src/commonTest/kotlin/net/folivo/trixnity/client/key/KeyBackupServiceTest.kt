package net.folivo.trixnity.client.key

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.beEmpty
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.mocks.SignServiceMock
import net.folivo.trixnity.client.store.KeySignatureTrustLevel
import net.folivo.trixnity.client.store.StoredCrossSigningKeys
import net.folivo.trixnity.client.store.StoredDeviceKeys
import net.folivo.trixnity.client.store.StoredSecret
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.client.SyncState.RUNNING
import net.folivo.trixnity.clientserverapi.model.keys.*
import net.folivo.trixnity.clientserverapi.model.users.SetGlobalAccountData
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.m.MegolmBackupV1EventContent
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import net.folivo.trixnity.core.model.keys.KeyValue.Ed25519KeyValue
import net.folivo.trixnity.core.model.keys.RoomKeyBackupAuthData.RoomKeyBackupV1AuthData
import net.folivo.trixnity.core.model.keys.RoomKeyBackupSessionData.EncryptedRoomKeyBackupV1SessionData.RoomKeyBackupV1SessionData
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.SecretType.M_MEGOLM_BACKUP_V1
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmSession
import net.folivo.trixnity.olm.*
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.getValue
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.test.utils.suspendLazy
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

class KeyBackupServiceTest : TrixnityBaseTest() {

    private val ownUserId = UserId("alice", "server")
    private val ownDeviceId = "DEV"

    private val accountStore =
        getInMemoryAccountStore { updateAccount { it?.copy(olmPickleKey = "", syncBatchToken = "batch") } }
    private val olmCryptoStore = getInMemoryOlmStore()
    private val keyStore = getInMemoryKeyStore()

    private val json = createMatrixEventJson()
    private val apiConfig = PortableMockEngineConfig()
    private val api = mockMatrixClientServerApiClient(apiConfig, json)

    private val olmSignMock = SignServiceMock()

    private val _validKeyBackup =
        suspendLazy { freeAfter(OlmPkDecryption.create(null)) { it.privateKey to it.publicKey } }
    private val validKeyBackupPrivateKey by _validKeyBackup.map { it.first }
    private val validKeyBackupPublicKey by _validKeyBackup.map { it.second }

    private val _keyBackup = suspendLazy { freeAfter(OlmPkDecryption.create(null)) { it.privateKey to it.publicKey } }
    private val keyBackupPrivateKey by _keyBackup.map { it.first }
    private val keyBackupPublicKey by _keyBackup.map { it.second }

    private val currentSyncState = MutableStateFlow(SyncState.STOPPED)

    private val cut = KeyBackupServiceImpl(
        UserInfo(ownUserId, ownDeviceId, Ed25519Key(null, ""), Curve25519Key(null, "")),
        accountStore,
        olmCryptoStore,
        keyStore,
        api,
        olmSignMock,
        CurrentSyncState(currentSyncState),
        testScope.backgroundScope,
    ).apply {
        startInCoroutineScope(testScope.backgroundScope)
    }

    private val keyVersion by suspendLazy {
        GetRoomKeysBackupVersionResponse.V1(
            authData = RoomKeyBackupV1AuthData(
                publicKey = Curve25519KeyValue(validKeyBackupPublicKey),
                signatures = mapOf(ownUserId to keysOf(Ed25519Key("DEV", "s1"), Ed25519Key("MSK", "s2")))
            ),
            count = 1,
            etag = "etag",
            version = "1"
        )
    }


    @Test
    fun `setAndSignNewKeyBackupVersion » set version to null when algorithm not supported`() = runTest {
        currentSyncState.value = RUNNING
        var call = 0
        apiConfig.endpoints {
            matrixJsonEndpoint(GetRoomKeyBackupVersion()) {
                call++
                when (call) {
                    1 -> keyVersion
                    else -> GetRoomKeysBackupVersionResponse.Unknown(
                        JsonObject(mapOf()),
                        RoomKeyBackupAlgorithm.Unknown("")
                    )
                }
            }
        }
        olmSignMock.returnSignatures = listOf(mapOf(ownUserId to keysOf(Ed25519Key("DEV", "s1"))))
        cut.version.value shouldBe null
        keyStore.updateSecrets {
            mapOf(
                M_MEGOLM_BACKUP_V1 to StoredSecret(
                    GlobalAccountDataEvent(MegolmBackupV1EventContent(mapOf())),
                    validKeyBackupPrivateKey
                )
            )
        }
        eventually(10.seconds) {
            cut.version.value shouldBe keyVersion
        }
        keyStore.updateSecrets {
            mapOf(
                M_MEGOLM_BACKUP_V1 to StoredSecret(
                    GlobalAccountDataEvent(MegolmBackupV1EventContent(mapOf("" to JsonPrimitive("something")))),
                    validKeyBackupPrivateKey
                )
            )
        }
        eventually(10.seconds) {
            cut.version.value shouldBe null
        }
    }

    @Test
    fun `setAndSignNewKeyBackupVersion » key backup can be trusted » just set version when already signed`() =
        runTest {
            currentSyncState.value = RUNNING
            apiConfig.endpoints {
                matrixJsonEndpoint(GetRoomKeyBackupVersion()) {
                    keyVersion
                }
            }
            olmSignMock.returnSignatures = listOf(mapOf(ownUserId to keysOf(Ed25519Key("DEV", "s1"))))
            keyStore.updateSecrets {
                mapOf(
                    M_MEGOLM_BACKUP_V1 to StoredSecret(
                        GlobalAccountDataEvent(
                            MegolmBackupV1EventContent(mapOf())
                        ), validKeyBackupPrivateKey
                    )
                )
            }
            eventually(10.seconds) {
                cut.version.value shouldBe keyVersion
            }
        }

    @Test
    fun `setAndSignNewKeyBackupVersion » key backup can be trusted » set version and sign when not signed by own device`() =
        runTest {
            currentSyncState.value = RUNNING
            var setRoomKeyBackupVersionCalled = false

            apiConfig.endpoints {
                matrixJsonEndpoint(GetRoomKeyBackupVersion()) {
                    keyVersion
                }
                matrixJsonEndpoint(SetRoomKeyBackupVersionByVersion("1")) {
                    setRoomKeyBackupVersionCalled = true
                    it.shouldBeInstanceOf<SetRoomKeyBackupVersionRequest.V1>()
                    it.version shouldBe "1"
                    it.authData.publicKey shouldBe Curve25519KeyValue(validKeyBackupPublicKey)
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
                        GlobalAccountDataEvent(
                            MegolmBackupV1EventContent(mapOf())
                        ), validKeyBackupPrivateKey
                    )
                )
            }
            eventually(10.seconds) {
                cut.version.value shouldBe keyVersion
            }
            setRoomKeyBackupVersionCalled shouldBe true
        }

    @Test
    fun `setAndSignNewKeyBackupVersion » key backup cannot be trusted » set version to null remove secret and remove signatures when signed by own device`() =
        runTest {
            currentSyncState.value = RUNNING
            var setRoomKeyBackupVersionCalled = false
            apiConfig.endpoints {
                matrixJsonEndpoint(GetRoomKeyBackupVersion()) {
                    keyVersion
                }
                matrixJsonEndpoint(SetRoomKeyBackupVersionByVersion("1")) {
                    setRoomKeyBackupVersionCalled = true
                    it.shouldBeInstanceOf<SetRoomKeyBackupVersionRequest.V1>()
                    it.version shouldBe "1"
                    it.authData.publicKey shouldBe Curve25519KeyValue(validKeyBackupPublicKey)
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
                        GlobalAccountDataEvent(
                            MegolmBackupV1EventContent(mapOf())
                        ), validKeyBackupPrivateKey
                    )
                )
            }
            eventually(10.seconds) {
                cut.version.value shouldBe keyVersion
            }
            keyStore.updateSecrets {
                mapOf(
                    M_MEGOLM_BACKUP_V1 to StoredSecret(
                        GlobalAccountDataEvent(
                            MegolmBackupV1EventContent(mapOf())
                        ), "invalidPri"
                    )
                )
            }
            eventually(10.seconds) {
                cut.version.value shouldBe null
            }

            setRoomKeyBackupVersionCalled shouldBe true
            keyStore.getSecrets().shouldBeEmpty()
        }


    private val roomId = RoomId("room", "server")
    private val sessionId = "sessionId"
    private val version = "1"
    private val senderKey = Curve25519KeyValue("senderKey")

    @Test
    fun `loadMegolmSession » do nothing when version is null`() = runTest {
        currentSyncState.value = RUNNING
        backgroundScope.launch {
            cut.loadMegolmSession(roomId, sessionId)
        }
        continually(1.seconds) {
            olmCryptoStore.getInboundMegolmSession(sessionId, roomId).first() shouldBe null
        }
    }

    private val encryptedRoomKeyBackupV1SessionData by suspendLazy {
        val sessionKey = freeAfter(OlmOutboundGroupSession.create()) { os ->
            os.encrypt("bla")
            freeAfter(OlmInboundGroupSession.create(os.sessionKey)) { it.export(1) }
        }
        freeAfter(OlmPkEncryption.create(keyBackupPublicKey)) {
            val e = it.encrypt(
                json.encodeToString(
                    RoomKeyBackupV1SessionData(
                        senderKey, listOf(), Keys(Ed25519Key(null, "edKey")), sessionKey
                    )
                )
            )
            RoomKeyBackupSessionData.EncryptedRoomKeyBackupV1SessionData(
                ciphertext = e.cipherText,
                mac = e.mac,
                ephemeral = e.ephemeralKey
            )
        }
    }

    private suspend fun megolmSessionOnServerSetup() {
        currentSyncState.value = RUNNING
        setVersion(keyBackupPrivateKey, keyBackupPublicKey, version)
    }

    private suspend fun megolmSessionOnServerWithoutErrorSetup() {
        megolmSessionOnServerSetup()
        apiConfig.endpoints {
            matrixJsonEndpoint(GetRoomKeyBackupData(roomId, sessionId, version)) {
                RoomKeyBackupData(1, 0, false, encryptedRoomKeyBackupV1SessionData)
            }
        }
    }

    private val allLoadMegolmSessionsCalled = MutableStateFlow(false)
    private var getRoomKeyBackupDataCalled = false
    private var call = 0

    private suspend fun megolmSessionOnServerWithDelaySetup() {
        megolmSessionOnServerSetup()
        apiConfig.endpoints {
            matrixJsonEndpoint(GetRoomKeyBackupData(roomId, sessionId, version)) {
                allLoadMegolmSessionsCalled.first { it }
                getRoomKeyBackupDataCalled = true
                RoomKeyBackupData(0, 0, false, encryptedRoomKeyBackupV1SessionData)
            }
        }
    }


    private suspend fun megolmSessionOnServerWithError() {
        megolmSessionOnServerSetup()
        apiConfig.endpoints {
            matrixJsonEndpoint(GetRoomKeyBackupData(roomId, sessionId, version)) {
                call++
                when (call) {
                    1 -> throw MatrixServerException(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse.Unknown("")
                    )

                    else -> {
                        getRoomKeyBackupDataCalled = true
                        RoomKeyBackupData(0, 0, false, encryptedRoomKeyBackupV1SessionData)
                    }
                }
            }
        }
    }

    @Test
    fun `loadMegolmSession » megolm session on server » without error » fetch megolm session and save when index is older than known index`() =
        runTest {
            megolmSessionOnServerWithoutErrorSetup()
            val currentSession = StoredInboundMegolmSession(
                senderKey = senderKey,
                sessionId = sessionId,
                roomId = roomId,
                firstKnownIndex = 24,
                hasBeenBackedUp = true,
                isTrusted = true,
                senderSigningKey = Ed25519KeyValue("edKey"),
                forwardingCurve25519KeyChain = listOf(),
                pickled = "pickle"
            )
            olmCryptoStore.updateInboundMegolmSession(sessionId, roomId) { currentSession }

            cut.loadMegolmSession(roomId, sessionId)
            eventually(10.seconds) {
                olmCryptoStore.getInboundMegolmSession(sessionId, roomId).first()
                    .shouldNotBeNull().firstKnownIndex shouldBe 1
            }
            assertSoftly(
                olmCryptoStore.getInboundMegolmSession(sessionId, roomId).first()
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

    @Test
    fun `loadMegolmSession » megolm session on server » without error » fetch megolm session but keep old session when index is older than known index`() =
        runTest {
            megolmSessionOnServerWithoutErrorSetup()
            val currentSession = StoredInboundMegolmSession(
                senderKey = senderKey,
                sessionId = sessionId,
                roomId = roomId,
                firstKnownIndex = 0,
                hasBeenBackedUp = true,
                isTrusted = true,
                senderSigningKey = Ed25519KeyValue("key"),
                forwardingCurve25519KeyChain = listOf(),
                pickled = "pickle"
            )
            olmCryptoStore.updateInboundMegolmSession(sessionId, roomId) { currentSession }

            cut.loadMegolmSession(roomId, sessionId)
            continually(1.seconds) {
                olmCryptoStore.getInboundMegolmSession(sessionId, roomId).first() shouldBe currentSession
            }
        }

    @Test
    fun `loadMegolmSession » megolm session on server » with delay » fetch one megolm session only once at a time`() =
        runTest {
            megolmSessionOnServerWithDelaySetup()
            repeat(20) {
                launch {
                    cut.loadMegolmSession(roomId, sessionId)
                }
            }
            allLoadMegolmSessionsCalled.value = true
            eventually(10.seconds) {
                olmCryptoStore.getInboundMegolmSession(sessionId, roomId).first().shouldNotBeNull()
            }
            getRoomKeyBackupDataCalled shouldBe true
        }

    @Test
    fun `loadMegolmSession » megolm session on server » with error » retry fetch megolm session`() = runTest {
        megolmSessionOnServerWithError()
        cut.loadMegolmSession(roomId, sessionId)
        val session = eventually(10.seconds) {
            olmCryptoStore.getInboundMegolmSession(sessionId, roomId)
                .first().shouldNotBeNull()
        }
        assertSoftly(session) {
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

    @Test
    fun `bootstrapRoomKeyBackup » upload new room key version and add secret to account data`() = runTest {
        val (masterSigningPrivateKey, masterSigningPublicKey) =
            freeAfter(OlmPkSigning.create(null)) { it.privateKey to it.publicKey }
        var setRoomKeyBackupVersionCalled = false
        var setGlobalAccountDataCalled = false
        apiConfig.endpoints {
            matrixJsonEndpoint(SetRoomKeyBackupVersion()) {
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
            matrixJsonEndpoint(GetRoomKeyBackupVersionByVersion("1")) {
                GetRoomKeysBackupVersionResponse.V1(
                    authData = RoomKeyBackupV1AuthData(
                        publicKey = Curve25519KeyValue("keyBackupPublicKey"),
                    ),
                    count = 1,
                    etag = "etag",
                    version = "1"
                )
            }
            matrixJsonEndpoint(SetGlobalAccountData(ownUserId, "m.megolm_backup.v1")) {
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

    private val room1 = RoomId("room1", "server")
    private val room2 = RoomId("room2", "server")
    private val sessionId1 = "session1"
    private val sessionId2 = "session2"
    private val pickle1 by suspendLazy {
        freeAfter(OlmOutboundGroupSession.create()) { os ->
            freeAfter(OlmInboundGroupSession.create(os.sessionKey)) { it.pickle("") }
        }
    }
    private val pickle2 by suspendLazy {
        freeAfter(OlmOutboundGroupSession.create()) { os ->
            freeAfter(OlmInboundGroupSession.create(os.sessionKey)) { it.pickle("") }
        }
    }
    private val session1 by suspendLazy {
        StoredInboundMegolmSession(
            senderKey = Curve25519KeyValue("curve1"),
            sessionId = sessionId1,
            roomId = room1,
            firstKnownIndex = 2,
            hasBeenBackedUp = false,
            isTrusted = true,
            senderSigningKey = Ed25519KeyValue("ed1"),
            forwardingCurve25519KeyChain = listOf(),
            pickled = pickle1
        )
    }
    private val session2 by suspendLazy {
        StoredInboundMegolmSession(
            senderKey = Curve25519KeyValue("curve2"),
            sessionId = sessionId2,
            roomId = room2,
            firstKnownIndex = 4,
            hasBeenBackedUp = false,
            isTrusted = true,
            senderSigningKey = Ed25519KeyValue("ed2"),
            forwardingCurve25519KeyChain = listOf(Curve25519KeyValue("curve2")),
            pickled = pickle2
        )
    }

    private suspend fun uploadRoomKeyBackupSetup() {
        currentSyncState.value = RUNNING
        session1.run { olmCryptoStore.updateInboundMegolmSession(sessionId, roomId) { this } }
        session2.run { olmCryptoStore.updateInboundMegolmSession(sessionId, roomId) { this } }
    }

    @Test
    fun `uploadRoomKeyBackup » do nothing when version is null`() = runTest {
        uploadRoomKeyBackupSetup()
        var setRoomKeyBackupVersionCalled = false
        apiConfig.endpoints {
            matrixJsonEndpoint(SetRoomKeyBackupVersion()) {
                setRoomKeyBackupVersionCalled = true
                SetRoomKeyBackupVersion.Response("1")
            }
        }
        continually(1.seconds) {
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

    @Test
    fun `uploadRoomKeyBackup » do nothing when not backed up is empty`() = runTest {
        uploadRoomKeyBackupSetup()
        session1.run {
            olmCryptoStore.updateInboundMegolmSession(sessionId, roomId) { this.copy(hasBeenBackedUp = true) }
        }
        session2.run {
            olmCryptoStore.updateInboundMegolmSession(sessionId, roomId) { this.copy(hasBeenBackedUp = true) }
        }
        var setRoomKeyBackupVersionCalled = false
        apiConfig.endpoints {
            matrixJsonEndpoint(SetRoomKeyBackupVersion()) {
                setRoomKeyBackupVersionCalled = true
                SetRoomKeyBackupVersion.Response("2")
            }
        }
        val (keyBackupPrivateKey, keyBackupPublicKey) = freeAfter(OlmPkDecryption.create(null)) { it.privateKey to it.publicKey }
        setVersion(keyBackupPrivateKey, keyBackupPublicKey, "1")

        continually(1.seconds) {
            setRoomKeyBackupVersionCalled shouldBe false
        }
    }

    @Test
    fun `uploadRoomKeyBackup » upload key backup and set flag that session has been backed up`() = runTest {
        uploadRoomKeyBackupSetup()
        var setRoomKeyBackupDataCalled = false
        apiConfig.endpoints {
            matrixJsonEndpoint(SetRoomsKeyBackup("1")) {
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
        setVersion(validKeyBackupPrivateKey, validKeyBackupPublicKey, "1")

        eventually(10.seconds) {
            olmCryptoStore.notBackedUpInboundMegolmSessions.value.shouldBeEmpty()
        }
        setRoomKeyBackupDataCalled shouldBe true
        session1.run {
            eventually(10.seconds) {
                olmCryptoStore.getInboundMegolmSession(sessionId, roomId).first()?.hasBeenBackedUp shouldBe true
            }
        }
        session2.run {
            eventually(10.seconds) {
                olmCryptoStore.getInboundMegolmSession(sessionId, roomId).first()?.hasBeenBackedUp shouldBe true
            }
        }
    }

    @Test
    fun `uploadRoomKeyBackup » update key backup version when error is M_WRONG_ROOM_KEYS_VERSION`() = runTest {
        uploadRoomKeyBackupSetup()
        val setRoomKeyBackupDataCalled1 = MutableStateFlow(false)
        val setRoomKeyBackupDataCalled2 = MutableStateFlow(false)
        apiConfig.endpoints {
            matrixJsonEndpoint(SetRoomsKeyBackup("1")) {
                setRoomKeyBackupDataCalled1.value = true
                throw MatrixServerException(HttpStatusCode.Forbidden, ErrorResponse.WrongRoomKeysVersion(""))
            }
            matrixJsonEndpoint(SetRoomsKeyBackup("2")) {
                setRoomKeyBackupDataCalled2.value = true
                SetRoomKeysResponse(2, "etag")
            }
        }
        setVersion(validKeyBackupPrivateKey, validKeyBackupPublicKey, "1") {
            if (setRoomKeyBackupDataCalled1.value)
                GetRoomKeysBackupVersionResponse.V1(
                    authData = RoomKeyBackupV1AuthData(
                        publicKey = Curve25519KeyValue(validKeyBackupPublicKey),
                        signatures = mapOf(ownUserId to keysOf(Ed25519Key("DEV", "s1"), Ed25519Key("MSK", "s2")))
                    ),
                    count = 1,
                    etag = "etag",
                    version = "2"
                )
            else it
        }

        eventually(10.seconds) {
            olmCryptoStore.notBackedUpInboundMegolmSessions.first().shouldBeEmpty()
        }
        setRoomKeyBackupDataCalled2.value shouldBe true
    }

    @Test
    fun `return false when private key is invalid`() = runTest {
        deviceKeyTrustLevel(KeySignatureTrustLevel.Valid(true))
        cut.keyBackupCanBeTrusted(roomKeyVersion(), "dino") shouldBe false
    }

    @Test
    fun `return false when key backup version not supported`() = runTest {
        deviceKeyTrustLevel(KeySignatureTrustLevel.Valid(true))
        cut.keyBackupCanBeTrusted(
            GetRoomKeysBackupVersionResponse.Unknown(
                JsonObject(mapOf()),
                RoomKeyBackupAlgorithm.Unknown("")
            ), "dino"
        ) shouldBe false
    }

    @Test
    fun `return false when public key does not match`() = runTest {
        deviceKeyTrustLevel(KeySignatureTrustLevel.Valid(true))
        cut.keyBackupCanBeTrusted(
            roomKeyVersion(),
            freeAfter(OlmPkDecryption.create(null)) { it.privateKey },
        ) shouldBe false
    }

    /*
       @Test
       fun `return false, when there is no signature we trust`() = runTest {
            deviceKeyTrustLevel(KeySignatureTrustLevel.Valid(false))
            masterKeyTrustLevel(KeySignatureTrustLevel.Valid(false))
            keyBackupCanBeTrusted(
                roomKeyVersion,
                privateKey,
                ownUserId,
                store
            ) shouldBe false
        }
     */

    @Test
    fun `return true when there is a device key is valid+verified`() = runTest {
        deviceKeyTrustLevel(KeySignatureTrustLevel.Valid(true))
        masterKeyTrustLevel(KeySignatureTrustLevel.Valid(false))
        cut.keyBackupCanBeTrusted(roomKeyVersion(), validKeyBackupPrivateKey) shouldBe true
    }

    @Test
    fun `return true when there is a device key is crossSigned+verified`() = runTest {
        deviceKeyTrustLevel(KeySignatureTrustLevel.CrossSigned(true))
        masterKeyTrustLevel(KeySignatureTrustLevel.Valid(false))
        cut.keyBackupCanBeTrusted(roomKeyVersion(), validKeyBackupPrivateKey) shouldBe true
    }

    @Test
    fun `return true when there is a master key we crossSigned+verified`() = runTest {
        deviceKeyTrustLevel(KeySignatureTrustLevel.Valid(false))
        masterKeyTrustLevel(KeySignatureTrustLevel.CrossSigned(true))
        cut.keyBackupCanBeTrusted(roomKeyVersion(), validKeyBackupPrivateKey) shouldBe true
    }

    @Test
    fun `return true when there is a master key we notFullyCrossSigned+verified`() = runTest {
        deviceKeyTrustLevel(KeySignatureTrustLevel.Valid(false))
        masterKeyTrustLevel(KeySignatureTrustLevel.NotAllDeviceKeysCrossSigned(true))
        cut.keyBackupCanBeTrusted(roomKeyVersion(), validKeyBackupPrivateKey) shouldBe true
    }

    private fun roomKeyVersion() = GetRoomKeysBackupVersionResponse.V1(
        authData = RoomKeyBackupV1AuthData(
            publicKey = Curve25519KeyValue(validKeyBackupPublicKey),
            signatures = mapOf(ownUserId to keysOf(Ed25519Key("DEVICE", "s1"), Ed25519Key("MSK", "s2")))
        ),
        count = 1,
        etag = "etag",
        version = "1"
    )

    private suspend fun deviceKeyTrustLevel(level: KeySignatureTrustLevel) {
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

    private suspend fun masterKeyTrustLevel(level: KeySignatureTrustLevel) {
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

    private suspend fun setVersion(
        keyBackupPrivateKey: String,
        keyBackupPublicKey: String,
        version: String,
        modifyVersion: suspend ((GetRoomKeysBackupVersionResponse.V1) -> GetRoomKeysBackupVersionResponse.V1) = { it }
    ) {
        olmSignMock.returnSignatures = listOf(mapOf(ownUserId to keysOf(Ed25519Key("DEV", "s1"))))
        val defaultVersion = GetRoomKeysBackupVersionResponse.V1(
            authData = RoomKeyBackupV1AuthData(
                publicKey = Curve25519KeyValue(keyBackupPublicKey),
                signatures = mapOf(ownUserId to keysOf(Ed25519Key("DEV", "s1"), Ed25519Key("MSK", "s2")))
            ),
            count = 1,
            etag = "etag",
            version = version
        )
        apiConfig.endpoints {
            matrixJsonEndpoint(GetRoomKeyBackupVersion()) {
                modifyVersion(defaultVersion)
            }
        }
        keyStore.updateSecrets {
            mapOf(
                M_MEGOLM_BACKUP_V1 to StoredSecret(
                    GlobalAccountDataEvent(MegolmBackupV1EventContent(mapOf())),
                    keyBackupPrivateKey
                )
            )
        }
        eventually(10.seconds) {
            cut.version.value shouldBe defaultVersion
        }
    }
}