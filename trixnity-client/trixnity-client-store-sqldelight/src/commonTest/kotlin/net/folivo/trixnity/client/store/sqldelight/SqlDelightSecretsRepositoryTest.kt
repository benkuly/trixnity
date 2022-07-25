package net.folivo.trixnity.client.store.sqldelight

import com.squareup.sqldelight.db.SqlDriver
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonObject
import net.folivo.trixnity.client.store.StoredSecret
import net.folivo.trixnity.client.store.sqldelight.db.Database
import net.folivo.trixnity.client.store.sqldelight.testutils.createDriverWithSchema
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.SecretType

class SqlDelightSecretsRepositoryTest : ShouldSpec({
    timeout = 60_000
    lateinit var cut: SqlDelightSecretsRepository
    lateinit var driver: SqlDriver
    beforeTest {
        driver = createDriverWithSchema()
        cut = SqlDelightSecretsRepository(
            Database(driver).keysQueries,
            createMatrixEventJson(),
            Dispatchers.Default
        )
    }
    afterTest {
        driver.close()
    }
    should("save, get and delete") {
        val secret1 = SecretType.M_CROSS_SIGNING_SELF_SIGNING to StoredSecret(
            Event.GlobalAccountDataEvent(SelfSigningKeyEventContent(mapOf("a" to JsonObject(mapOf())))),
            "priv1"
        )
        val secret2 = SecretType.M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
            Event.GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf("b" to JsonObject(mapOf())))),
            "priv2"
        )

        cut.save(1, mapOf(secret1))
        cut.get(1) shouldBe mapOf(secret1)
        cut.save(1, mapOf(secret1, secret2))
        cut.get(1) shouldBe mapOf(secret1, secret2)
        cut.delete(1)
        cut.get(1) shouldBe null
    }
})