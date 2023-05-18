package net.folivo.trixnity.client.store.repository.room

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import net.folivo.trixnity.client.store.StoredSecret
import net.folivo.trixnity.client.store.repository.test.buildTestDatabase
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.SecretType
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomSecretsRepositoryTest {
    private lateinit var db: TrixnityRoomDatabase
    private lateinit var repo: RoomSecretsRepository

    @Before
    fun before() {
        db = buildTestDatabase()
        repo = RoomSecretsRepository(db, createMatrixEventJson())
    }

    @Test
    fun `Save, get and delete`() = runTest {
        val secret1 = SecretType.M_CROSS_SIGNING_SELF_SIGNING to StoredSecret(
            Event.GlobalAccountDataEvent(
                SelfSigningKeyEventContent(mapOf("a" to JsonObject(mapOf())))
            ),
            "priv1"
        )
        val secret2 = SecretType.M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
            Event.GlobalAccountDataEvent(
                UserSigningKeyEventContent(mapOf("b" to JsonObject(mapOf())))
            ),
            "priv2"
        )

        repo.save(1, mapOf(secret1))
        repo.get(1) shouldBe mapOf(secret1)
        repo.save(1, mapOf(secret1, secret2))
        repo.get(1) shouldBe mapOf(secret1, secret2)
        repo.delete(1)
        repo.get(1) shouldBe null
    }
}
