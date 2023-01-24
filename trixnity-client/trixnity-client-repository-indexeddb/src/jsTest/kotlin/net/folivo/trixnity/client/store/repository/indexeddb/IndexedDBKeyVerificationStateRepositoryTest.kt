package net.folivo.trixnity.client.store.repository.indexeddb

import com.benasher44.uuid.uuid4
import com.juul.indexeddb.openDatabase
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.store.KeyVerificationState.Blocked
import net.folivo.trixnity.client.store.KeyVerificationState.Verified
import net.folivo.trixnity.client.store.repository.KeyVerificationStateKey
import net.folivo.trixnity.core.model.keys.KeyAlgorithm
import net.folivo.trixnity.core.serialization.createMatrixEventJson

class IndexedDBKeyVerificationStateRepositoryTest : ShouldSpec({
    timeout = 10_000
    lateinit var cut: IndexedDBKeyVerificationStateRepository
    lateinit var rtm: IndexedDBRepositoryTransactionManager

    beforeTest {
        cut = IndexedDBKeyVerificationStateRepository(createMatrixEventJson())
        val db = openDatabase(uuid4().toString(), 1) { database, oldVersion, _ ->
            IndexedDBKeyVerificationStateRepository.apply { migrate(database, oldVersion) }
        }
        rtm = IndexedDBRepositoryTransactionManager(db, arrayOf(cut.objectStoreName))
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

        rtm.writeTransaction {
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