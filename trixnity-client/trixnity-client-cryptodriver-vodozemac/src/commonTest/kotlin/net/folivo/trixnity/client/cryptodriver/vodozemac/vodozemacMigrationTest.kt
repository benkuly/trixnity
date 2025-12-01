package net.folivo.trixnity.client.cryptodriver.vodozemac

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.MediaStoreModule
import net.folivo.trixnity.client.createDefaultEventContentSerializerMappingsModule
import net.folivo.trixnity.client.createDefaultMatrixJsonModule
import net.folivo.trixnity.client.media.inMemory
import net.folivo.trixnity.client.store.repository.*
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmSession
import net.folivo.trixnity.crypto.olm.StoredOlmSession
import net.folivo.trixnity.crypto.olm.StoredOutboundMegolmSession
import net.folivo.trixnity.vodozemac.megolm.GroupSession
import net.folivo.trixnity.vodozemac.megolm.InboundGroupSession
import net.folivo.trixnity.vodozemac.olm.Account
import net.folivo.trixnity.vodozemac.olm.Session
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import net.folivo.trixnity.client.store.Account as StoredAccount

@Test
fun testVodozemacMigration() = runTest {
    val repositoriesModule = MediaStoreModule.inMemory().create()
    val app = koinApplication {
        modules(
            listOf(
                repositoriesModule,
                module {
                    single { MatrixClientConfiguration(storeTimelineEventContentUnencrypted = true) }
                    single { backgroundScope }
                    single<RepositoryMigration> {
                        VodozemacRepositoryMigration(get(), get(), get(), get(), get(), get(), get())
                    }
                },
                createDefaultEventContentSerializerMappingsModule(),
                createDefaultMatrixJsonModule()
            )
        )
    }

    val json = app.koin.get<Json>()

    val oldDb = json.decodeFromString<Database>(oldDatabase)
    val newDb = json.decodeFromString<Database>(newDatabase)

    app.applyDatabase(oldDb)
    app.koin.get<RepositoryMigration>().run()
    val db = app.getDatabase()

    db shouldBe newDb

    shouldNotThrowAny { Account.fromPickle(db.olmAccount) }
    shouldNotThrowAny { Session.fromPickle(db.olmSession.pickled) }
    shouldNotThrowAny { GroupSession.fromPickle(db.groupSession.pickled) }
    shouldNotThrowAny { InboundGroupSession.fromPickle(db.inboundGroupSession.pickled) }
}

private suspend fun KoinApplication.applyDatabase(db: Database) {
    val members = koin.members

    members.transaction.writeTransaction {
        members.accountRepository.save(key = 1, value = db.account)
        members.olmAccountRepository.save(key = 1, value = db.olmAccount)
        members.olmSessionRepository.save(key = db.olmSession.senderKey, value = setOf(db.olmSession))
        members.outboundMegolmSessionRepository.save(key = db.groupSession.roomId, value = db.groupSession)
        members.inboundMegolmSessionRepository.save(
            key = InboundMegolmSessionRepositoryKey(
                roomId = db.inboundGroupSession.roomId,
                sessionId = db.inboundGroupSession.sessionId,
            ),
            value = db.inboundGroupSession,
        )
    }
}

private suspend fun KoinApplication.getDatabase(): Database {
    val members = koin.members

    val db = members.transaction.readTransaction {
        val account = members.accountRepository.get(key = 1).shouldNotBeNull()
        val olmAccount = members.olmAccountRepository.get(key = 1).shouldNotBeNull()
        val olmSession = members.olmSessionRepository.getAll().flatten().apply { size shouldBe 1 }.first()
        val groupSession = members.outboundMegolmSessionRepository.getAll().apply { size shouldBe 1 }.first()
        val inboundGroupSession = members.inboundMegolmSessionRepository.getAll().apply { size shouldBe 1 }.first()

        Database(
            account = account,
            olmAccount = olmAccount,
            olmSession = olmSession,
            groupSession = groupSession,
            inboundGroupSession = inboundGroupSession,
        )
    }

    return db
}

private data class KoinMembers(
    val transaction: RepositoryTransactionManager,
    val accountRepository: AccountRepository,
    val olmAccountRepository: OlmAccountRepository,
    val olmSessionRepository: OlmSessionRepository,
    val outboundMegolmSessionRepository: OutboundMegolmSessionRepository,
    val inboundMegolmSessionRepository: InboundMegolmSessionRepository,
)

private val Koin.members: KoinMembers
    get() =
        KoinMembers(
            transaction = get(),
            accountRepository = get(),
            olmAccountRepository = get(),
            olmSessionRepository = get(),
            outboundMegolmSessionRepository = get(),
            inboundMegolmSessionRepository = get(),
        )

@Serializable
private data class Database(
    val account: StoredAccount,
    val olmAccount: String,
    val olmSession: StoredOlmSession,
    val groupSession: StoredOutboundMegolmSession,
    val inboundGroupSession: StoredInboundMegolmSession,
)

//  // Generated with
//
//  val alice = OlmAccount.create()
//  val bob = OlmAccount.create()
//
//  bob.generateOneTimeKeys(1)
//  val otk = bob.oneTimeKeys.curve25519.values.first()
//  bob.markKeysAsPublished()
//
//  val aliceSession = OlmSession.createOutbound(
//      account = alice,
//      theirIdentityKey = bob.identityKeys.curve25519,
//      theirOneTimeKey = otk
//  )
//
//  val groupSession = OlmOutboundGroupSession.create()
//  val inboundGroupSession = OlmInboundGroupSession.create(groupSession.sessionKey)
//
//  val pickles = Database(
//      account = Account(
//          olmPickleKey = "",
//          baseUrl = "baseUrl",
//          userId = UserId("userId", "matrix.org"),
//          deviceId = "deviceId",
//          accessToken = null,
//          refreshToken = null,
//          syncBatchToken = null,
//          filterId = null,
//          backgroundFilterId = null,
//          displayName = null,
//          avatarUrl = null,
//          isLocked = false,
//          version = @Suppress("DEPRECATION_ERROR") Account.Version.V1,
//      ),
//      olmAccount = alice.pickle(""),
//      olmSession = StoredOlmSession(
//          senderKey = Curve25519KeyValue(bob.identityKeys.curve25519),
//          sessionId = aliceSession.sessionId,
//          lastUsedAt = testClock.now(),
//          createdAt = testClock.now(),
//          pickled = aliceSession.pickle(""),
//          initiatedByThisDevice = true,
//      ),
//      groupSession = StoredOutboundMegolmSession(
//          roomId = RoomId("room", "server"),
//          createdAt = testClock.now(),
//          encryptedMessageCount = 0,
//          newDevices = emptyMap(),
//          pickled = groupSession.pickle("")
//      ),
//      inboundGroupSession = StoredInboundMegolmSession(
//          senderKey = Curve25519KeyValue(bob.identityKeys.curve25519),
//          senderSigningKey = Ed25519KeyValue(bob.identityKeys.ed25519),
//          sessionId = inboundGroupSession.sessionId,
//          roomId = RoomId("room", "server"),
//          firstKnownIndex = inboundGroupSession.firstKnownIndex,
//          hasBeenBackedUp = true,
//          isTrusted = true,
//          forwardingCurve25519KeyChain = emptyList(),
//          pickled = inboundGroupSession.pickle("")
//      )
//  )
//
//  println(json.encodeToString(pickles))
private val oldDatabase = """
    {
      "account": {
        "olmPickleKey": "",
        "baseUrl": "baseUrl",
        "userId": "@userId:matrix.org",
        "deviceId": "deviceId",
        "isLocked": false
      },
      "olmAccount": "MfRWkFTbgfFH4HLqlC9hRDCT1w9zm5y6U8Fo3TRiIueuAS8dAO1r43Q2CyuUYRY0q69Y8hl1Ru0bbbQHr8ix0FcVnTu2Ehn1A0ZBkBf41vNvC6OrneSWQy5S1uXZ5fyU62zDBPyKd5Z6aRLp3VL1rqgsAA5BJw4mS2I6a+IMLmW+GX1iiNjd9FrkG7F+XBmBs4a26mF1VHNUdwwONeKdWOGrd9yvoxO4yPuJwnNu0heNh7hMQH2YSA",
      "olmSession": {
        "senderKey": "rFXDk4304H+ApP4CL4F+6+3YZzUDSOYnq/tIz3q8E3g",
        "sessionId": "k5s3IXXAj4z3GvBjqtsllAdq5rbIyjUpA3UbybJoEmY",
        "lastUsedAt": "1970-01-01T00:00:00Z",
        "createdAt": "1970-01-01T00:00:00Z",
        "pickled": "SUGPI5X81LtXnSUJMjabLtLkoRdN9Y7xZDNVQoxIxrq3hqZ0V1kaFTDIsOvTW+gJ6KX5iv41TsLqx0Ji3r4WeGUfCpSz4MpcciqA1Tgw881hsWBByNpeXxmloh+PGVdDBZcmcL6a27eHAEtaSDfNY62pN8FNGkNoO+yIJjyEDuwNnFF02Ash9RyYcCRPXOPJoMe9Buj34LuPCwWmam0FYzO7Rw58NBMDuDCJEazG9ag3d4pY0ERl3LITwClbm/PFhbWpEvvktUmSiTToRPor4SOl28uiC5ExXZVNMwboaQlfRSHppL+4jNulx4aeZcAyB1jgIWnLGVz9GZCIB5gSTa/hqDi6KTVT",
        "initiatedByThisDevice": true
      },
      "groupSession": {
        "roomId": "!room:server",
        "createdAt": "1970-01-01T00:00:00Z",
        "encryptedMessageCount": 0,
        "newDevices": {},
        "pickled": "lO38aUPdIIx0uNQ+tmPaBGtAdQ5+ZTJftSDhlovMftQ3S3l1d/br84UGH9C5wPyGuIvnAvDBEqeSuBHauvM4e+dxrD8lJ+FRWCkDGpL4vSQzrno135S/rMRsL+M/IIZficInvcy1dqSOnIHaqiHOm6QaV2AKh43gny4SHeS/wCqk4CCJbkxH7AkuWfBsmA+vM3Hbybrn5ipu30QWdp6eodmRlWzBITy5rQaS/EZyIaHoIXFUqInFSSVPSf460upjd3aWHGtFKEwlWPyxjBwUZwKqSZPeIs9tKef03wWmzSsXK7idVAMLGjQ5jKTkLtUt3xlpYtTslJ0"
      },
      "inboundGroupSession": {
        "senderKey": "rFXDk4304H+ApP4CL4F+6+3YZzUDSOYnq/tIz3q8E3g",
        "senderSigningKey": "G5h7e0d1vgFFISW7lRlj7xQuvE2yUAWrbhDmPcz2HK0",
        "sessionId": "jD15HM7cJ3a1dfm8XQ2DJ6/nYWwhgIs4hu091cUdYPQ",
        "roomId": "!room:server",
        "firstKnownIndex": 0,
        "hasBeenBackedUp": true,
        "isTrusted": true,
        "forwardingCurve25519KeyChain": [],
        "pickled": "IYKTgCkfZhU2fA3esXesQhXJixXoeTK3dY0hG2vVfJnBibvHWqnq+wzG0goxSUMN+Y1ABxqyjFW+q1wFvGEXFsAQ4Pn5hEab930KaoZyM+E3cgSx7aWPNQ9gCZDHTxSfYaGBbKdhQRMqFmmJojy0pGfGiJpL5jcDFEtuxXDI344f8/FHY7rbZTQL2bpFTmb9PFAOrobNYvkoYofzhzTFIoLZTRmdxvf08+r3pA0C9UF1gHnCFBJVL6MHz/Jl2nV2gAQMBsxs1mB/zBIqX8irRMWXUpHSsFapJ4xoSOEHBWQwavbY6iwrbVyBdB6tPAEfrVj71ahudCK1WvxURsrLCPOw9mjSrY3ijDZJBIHAJDb8epoU8RGROnWiNkLhhWm77QuaJWhY3UL2ahgkNlntBw/dS1fTzcJs"
      }
    }
""".trimIndent()

private val newDatabase = """
    {
      "account": {
        "baseUrl": "baseUrl",
        "userId": "@userId:matrix.org",
        "deviceId": "deviceId",
        "isLocked": false,
        "version": "V2"
      },
      "olmAccount": "eyJzaWduaW5nX2tleSI6eyJFeHBhbmRlZCI6WzE0NCwyMjQsNjEsMjIzLDk2LDE3NCwxMTUsMzYsMjE3LDIzMCwyMTIsNTgsMTI1LDE2NywxMDUsMjksMTMwLDEwOCw4MSwyMjIsMTY4LDM2LDE0NCw4LDE0OSwxMTUsMzksMTg2LDIxMCwzMyw1OSwxMDMsMzAsNzAsNDQsMTkzLDUsMTU2LDYwLDM3LDExMSwxNzQsMTk5LDkwLDU4LDIxMywxMyw3Niw2Niw4LDIwNywxMzcsMTAwLDIzMiw3MywyOSw1LDIwLDE4MCwxMTAsMjQ2LDIzOSw0MiwzXX0sImRpZmZpZV9oZWxsbWFuX2tleSI6Wzk1LDIzMSw5MiwxNDIsNTksODAsMTc5LDIsNjQsMjQ5LDEwMiwxMjIsMjU0LDIyNCwxNCw4Nyw3MCwyMjYsODEsMTE5LDk2LDE3NiwyMTEsMTc1LDE3NCwyMzUsMTgsOTgsMjQzLDgwLDcyLDIwOV0sIm9uZV90aW1lX2tleXMiOnsibmV4dF9rZXlfaWQiOjAsInB1YmxpY19rZXlzIjp7fSwicHJpdmF0ZV9rZXlzIjp7fX0sImZhbGxiYWNrX2tleXMiOnsia2V5X2lkIjowLCJmYWxsYmFja19rZXkiOm51bGwsInByZXZpb3VzX2ZhbGxiYWNrX2tleSI6bnVsbH19",
      "olmSession": {
        "senderKey": "rFXDk4304H+ApP4CL4F+6+3YZzUDSOYnq/tIz3q8E3g",
        "sessionId": "k5s3IXXAj4z3GvBjqtsllAdq5rbIyjUpA3UbybJoEmY",
        "lastUsedAt": "1970-01-01T00:00:00Z",
        "createdAt": "1970-01-01T00:00:00Z",
        "pickled": "eyJzZXNzaW9uX2tleXMiOnsiaWRlbnRpdHlfa2V5IjpbNDAsMTQzLDIwMCwyMjQsMTUzLDE1NywxODgsMTI2LDkzLDE5MCwxNSwxOTIsMTI0LDgyLDcsMTI5LDIzNSwxODUsMTYwLDEzOSwxODksNzksMTEwLDUsNjYsMSwxNzEsNjgsMTQ2LDE5NSwxMTAsMjNdLCJiYXNlX2tleSI6WzE4OCw4NCw3LDkyLDI1NCwzOSwxODMsMTE1LDkyLDExNywxMzEsOCw4NSwyMCwyMjMsMTgsODUsMzYsMTk2LDIxNSwyMTUsMzgsMjA1LDg2LDE5NCwxNTEsNDMsMTY2LDEzMiw1MiwyMTIsNTddLCJvbmVfdGltZV9rZXkiOlsyMDIsMTMzLDEyMSw1Myw2OSwxOTEsMTg1LDk5LDI3LDE0LDIwNywyNDAsMjQ1LDE4MSw4NSwyMDMsNzAsNTAsMTgyLDI0MiwxMjksMjQxLDE3Niw0OCwyNSwxMjQsMTc4LDg4LDE3MCwzNCwxNzIsMTE2XX0sInNlbmRpbmdfcmF0Y2hldCI6eyJ0eXBlIjoiYWN0aXZlIiwicGFyZW50X3JhdGNoZXRfa2V5IjpudWxsLCJyYXRjaGV0X2NvdW50Ijp7IlVua25vd24iOm51bGx9LCJhY3RpdmVfcmF0Y2hldCI6eyJyb290X2tleSI6Wzg1LDk1LDQwLDEyMCwxMTEsMTU3LDExLDE4MiwyNDQsMTc4LDIyMCwyNDgsMTQ2LDE2NywxODMsODAsNDAsMjQwLDExNCw3OCwxMjQsMTMsMTUyLDQwLDg3LDIzMCwxNDgsMTEzLDEwMywyNTMsNTIsMjI2XSwicmF0Y2hldF9rZXkiOlsxMDcsMjAsMTM1LDIyLDI0NSwxOTUsNDgsMTA4LDIzMiwxODAsOTAsMTA2LDEyOSwyMjAsNyw2NiwyNDIsMTUwLDExNCwyNTQsMjEwLDEzNywxMDcsMTMwLDE4MSwyMjMsMTQsMjAzLDIwNywxNzksMjQsMjUxXX0sInN5bW1ldHJpY19rZXlfcmF0Y2hldCI6eyJrZXkiOlsyMTQsOTEsMTc5LDI0Myw1Myw3OSwxOTMsMjI5LDIsMTIxLDE0MiwyMjksNDgsMTEyLDI1NCwxNzAsNzUsMTQ5LDcyLDE0MCwyNDEsMTE3LDE2NiwxNjgsMTc0LDEwNywyMDYsMjAyLDIxMSwyNDYsMTEsODRdLCJpbmRleCI6MH19LCJyZWNlaXZpbmdfY2hhaW5zIjp7ImlubmVyIjpbXX0sImNvbmZpZyI6eyJ2ZXJzaW9uIjoiVjEifX0",
        "initiatedByThisDevice": true
      },
      "groupSession": {
        "roomId": "!room:server",
        "createdAt": "1970-01-01T00:00:00Z",
        "encryptedMessageCount": 0,
        "newDevices": {},
        "pickled": "eyJyYXRjaGV0Ijp7ImlubmVyIjpbMzQsMTczLDIyMCw0NSwyMzgsMjM4LDMwLDY3LDEzOSwyMiwyNTIsMjQzLDI0NywxMSwxMzcsMywxODcsNTksMTU0LDk0LDIwOCwxMTUsMTksODYsMTQyLDY1LDI4LDIwMCw1OSwxMjAsMTA1LDIzNiwxMTgsMjUxLDM1LDkyLDg3LDExNCw3OSwxOTUsMzQsMTc1LDg4LDIxOCwxODgsMjAsMTAxLDExMCwxNTgsMTk1LDIxOSwxMTYsODMsMTExLDYwLDI1MiwxNTgsOSwyMTcsODcsMjI2LDIyNSwxNiwxNDIsMTEwLDEzMCwxMDEsMjI5LDE4OSwxNjMsODUsNzIsMTgsMjI4LDEwNSwyMTgsNDcsMjUzLDg1LDE1NSwxOCwxOSwzNSwxLDE1MiwyNDYsMjQ0LDE3MCw5OSw5LDIzNywyMzgsMTQyLDI2LDcyLDEyLDE0MCw1MCwyMzUsMjI4LDI2LDE1NSw1MCw1OCw3OSw3Niw2MSwxMywxMjAsOTUsMTY4LDE0NCwyNDcsMTA5LDIxOCwyNDksMzksMTI4LDIwNCwzNCw0NiwxMTksOTQsMjAxLDQsMTcwLDIyOCw0Ml0sImNvdW50ZXIiOjB9LCJzaWduaW5nX2tleSI6eyJFeHBhbmRlZCI6Wzk2LDEzNSwyNDAsOTYsMjExLDE3LDE1MywxNTIsMjI2LDUyLDE1Niw2OCw1MywxOTUsMTc2LDIzMiw0LDIyLDE0OCwyMSwxNTcsMTIwLDIzNSwyMjksOCwxNDYsMjE4LDE2MSwyMSwxNDIsMjE2LDExMywxOTEsMTIzLDE2MCwxOTYsMjMzLDE0OSwxNSwxMjEsMTQxLDIxNCwyNSwxLDE4MiwxNzAsNjYsNzQsMjExLDIzNywxNDgsMTI4LDE0LDE5MSwyMjgsMTMsMTcsMjUzLDIxOCwyNTQsMTUzLDE3NCwxMzYsNDRdfSwiY29uZmlnIjp7InZlcnNpb24iOiJWMSJ9fQ"
      },
      "inboundGroupSession": {
        "senderKey": "rFXDk4304H+ApP4CL4F+6+3YZzUDSOYnq/tIz3q8E3g",
        "senderSigningKey": "G5h7e0d1vgFFISW7lRlj7xQuvE2yUAWrbhDmPcz2HK0",
        "sessionId": "jD15HM7cJ3a1dfm8XQ2DJ6/nYWwhgIs4hu091cUdYPQ",
        "roomId": "!room:server",
        "firstKnownIndex": 0,
        "hasBeenBackedUp": true,
        "isTrusted": true,
        "forwardingCurve25519KeyChain": [],
        "pickled": "eyJpbml0aWFsX3JhdGNoZXQiOnsiaW5uZXIiOlszNCwxNzMsMjIwLDQ1LDIzOCwyMzgsMzAsNjcsMTM5LDIyLDI1MiwyNDMsMjQ3LDExLDEzNywzLDE4Nyw1OSwxNTQsOTQsMjA4LDExNSwxOSw4NiwxNDIsNjUsMjgsMjAwLDU5LDEyMCwxMDUsMjM2LDExOCwyNTEsMzUsOTIsODcsMTE0LDc5LDE5NSwzNCwxNzUsODgsMjE4LDE4OCwyMCwxMDEsMTEwLDE1OCwxOTUsMjE5LDExNiw4MywxMTEsNjAsMjUyLDE1OCw5LDIxNyw4NywyMjYsMjI1LDE2LDE0MiwxMTAsMTMwLDEwMSwyMjksMTg5LDE2Myw4NSw3MiwxOCwyMjgsMTA1LDIxOCw0NywyNTMsODUsMTU1LDE4LDE5LDM1LDEsMTUyLDI0NiwyNDQsMTcwLDk5LDksMjM3LDIzOCwxNDIsMjYsNzIsMTIsMTQwLDUwLDIzNSwyMjgsMjYsMTU1LDUwLDU4LDc5LDc2LDYxLDEzLDEyMCw5NSwxNjgsMTQ0LDI0NywxMDksMjE4LDI0OSwzOSwxMjgsMjA0LDM0LDQ2LDExOSw5NCwyMDEsNCwxNzAsMjI4LDQyXSwiY291bnRlciI6MH0sInNpZ25pbmdfa2V5IjpbMTQwLDYxLDEyMSwyOCwyMDYsMjIwLDM5LDExOCwxODEsMTE3LDI0OSwxODgsOTMsMTMsMTMxLDM5LDE3NSwyMzEsOTcsMTA4LDMzLDEyOCwxMzksNTYsMTM0LDIzNyw2MSwyMTMsMTk3LDI5LDk2LDI0NF0sInNpZ25pbmdfa2V5X3ZlcmlmaWVkIjp0cnVlLCJjb25maWciOnsidmVyc2lvbiI6IlYxIn19"
      }
    }
""".trimIndent()