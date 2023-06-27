package net.folivo.trixnity.client.store.repository.room

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.client.store.KeyVerificationState.Blocked
import net.folivo.trixnity.client.store.KeyVerificationState.Verified
import net.folivo.trixnity.client.store.repository.KeyVerificationStateKey
import net.folivo.trixnity.core.model.keys.KeyAlgorithm
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomKeyVerificationStateRepositoryTest {
    private lateinit var db: TrixnityRoomDatabase
    private lateinit var repo: RoomKeyVerificationStateRepository

    @Before
    fun before() {
        db = buildTestDatabase()
        repo = RoomKeyVerificationStateRepository(db, createMatrixEventJson())
    }

    @Test
    fun `Save, get and delete`() = runTest {
        val verifiedKey1Key = KeyVerificationStateKey(
            keyId = "key1",
            keyAlgorithm = KeyAlgorithm.Ed25519
        )
        val verifiedKey2Key = KeyVerificationStateKey(
            keyId = "key2",
            keyAlgorithm = KeyAlgorithm.Ed25519
        )

        repo.save(verifiedKey1Key, Verified("keyValue1"))
        repo.save(verifiedKey2Key, Blocked("keyValue2"))
        repo.get(verifiedKey1Key) shouldBe Verified("keyValue1")
        repo.get(verifiedKey2Key) shouldBe Blocked("keyValue2")
        repo.save(verifiedKey1Key, Verified("keyValueChanged"))
        repo.get(verifiedKey1Key) shouldBe Verified("keyValueChanged")
        repo.delete(verifiedKey1Key)
        repo.get(verifiedKey1Key) shouldBe null
    }
}
