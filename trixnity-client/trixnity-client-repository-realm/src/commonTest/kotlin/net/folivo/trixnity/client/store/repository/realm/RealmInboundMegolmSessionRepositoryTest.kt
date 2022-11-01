package net.folivo.trixnity.client.store.repository.realm

import com.benasher44.uuid.uuid4
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import net.folivo.trixnity.client.store.repository.InboundMegolmSessionRepositoryKey
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmSession


class RealmInboundMegolmSessionRepositoryTest : ShouldSpec({
    timeout = 10_000
    lateinit var realm: Realm
    lateinit var cut: RealmInboundMegolmSessionRepository

    beforeTest {
        val realmDbPath = "build/test-db/${uuid4()}"
        realm = Realm.open(
            RealmConfiguration.Builder(
                schema = setOf(
                    RealmInboundMegolmSession::class,
                )
            ).apply { directory(realmDbPath) }.build()
        )

        cut = RealmInboundMegolmSessionRepository(createMatrixEventJson())
    }
    should("save, get and delete") {
        val roomId = RoomId("room", "server")
        val inboundSessionKey1 = InboundMegolmSessionRepositoryKey("session1", roomId)
        val inboundSessionKey2 = InboundMegolmSessionRepositoryKey("session2", roomId)
        val inboundSession1 =
            StoredInboundMegolmSession(
                senderKey = Curve25519Key(null, "curve1"),
                sessionId = "session1",
                roomId = roomId,
                firstKnownIndex = 1,
                hasBeenBackedUp = false,
                isTrusted = false,
                senderSigningKey = Key.Ed25519Key(null, "ed1"),
                forwardingCurve25519KeyChain = listOf(
                    Curve25519Key(null, "curveExt1"),
                    Curve25519Key(null, "curveExt2")
                ),
                pickled = "pickle1"
            )
        val inboundSession2 =
            StoredInboundMegolmSession(
                senderKey = Curve25519Key(null, "curve2"),
                sessionId = "session2",
                roomId = roomId,
                firstKnownIndex = 1,
                hasBeenBackedUp = true,
                isTrusted = false,
                senderSigningKey = Key.Ed25519Key(null, "ed2"),
                forwardingCurve25519KeyChain = listOf(),
                pickled = "pickle2"
            )
        val inboundSession2Copy = inboundSession2.copy(pickled = "pickle2Copy")

        writeTransaction(realm) {
            cut.save(inboundSessionKey1, inboundSession1)
            cut.save(inboundSessionKey2, inboundSession2)
            cut.get(inboundSessionKey1) shouldBe inboundSession1
            cut.get(inboundSessionKey2) shouldBe inboundSession2
            cut.getByNotBackedUp() shouldBe setOf(inboundSession1)
            cut.save(inboundSessionKey2, inboundSession2Copy)
            cut.get(inboundSessionKey2) shouldBe inboundSession2Copy
            cut.delete(inboundSessionKey1)
            cut.get(inboundSessionKey1) shouldBe null
        }
    }
})