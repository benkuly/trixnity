package net.folivo.trixnity.client.store.repository.realm

import com.benasher44.uuid.uuid4
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import net.folivo.trixnity.client.store.KeyVerificationState.Blocked
import net.folivo.trixnity.client.store.KeyVerificationState.Verified
import net.folivo.trixnity.client.store.repository.KeyVerificationStateKey
import net.folivo.trixnity.core.model.keys.KeyAlgorithm
import net.folivo.trixnity.core.serialization.createMatrixEventJson


class RealmKeyVerificationStateRepositoryTest : ShouldSpec({
    timeout = 10_000
    lateinit var realm: Realm
    lateinit var cut: RealmKeyVerificationStateRepository

    beforeTest {
        val realmDbPath = "build/test-db/${uuid4()}"
        realm = Realm.open(
            RealmConfiguration.Builder(
                schema = setOf(
                    RealmKeyVerificationState::class,
                )
            ).apply { directory(realmDbPath) }.build()
        )

        cut = RealmKeyVerificationStateRepository(createMatrixEventJson())
    }
    should("save, get and delete") {
        val verifiedKey1Key = KeyVerificationStateKey(
            keyId = "key1",
            keyAlgorithm = KeyAlgorithm.Ed25519
        )
        val verifiedKey2Key = KeyVerificationStateKey(
            keyId = "key2",
            keyAlgorithm = KeyAlgorithm.Ed25519
        )

        writeTransaction(realm) {
            cut.save(verifiedKey1Key, Verified("keyValue1"))
            cut.save(verifiedKey2Key, Blocked("keyValue2"))
            cut.get(verifiedKey1Key) shouldBe Verified("keyValue1")
            cut.get(verifiedKey2Key) shouldBe Blocked("keyValue2")
            cut.save(verifiedKey1Key, Verified("keyValueChanged"))
            cut.get(verifiedKey1Key) shouldBe Verified("keyValueChanged")
            cut.delete(verifiedKey1Key)
            cut.get(verifiedKey1Key) shouldBe null
        }
    }
})