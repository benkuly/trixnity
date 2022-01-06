package net.folivo.trixnity.client.store.exposed

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import net.folivo.trixnity.client.store.AllowedSecretType
import net.folivo.trixnity.client.store.StoredSecret
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import net.folivo.trixnity.core.serialization.createMatrixJson
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedSecretsRepositoryTest : ShouldSpec({
    lateinit var cut: ExposedSecretsRepository
    beforeTest {
        createDatabase()
        newSuspendedTransaction {
            SchemaUtils.create(ExposedSecrets)
        }
        cut = ExposedSecretsRepository(createMatrixJson())
    }
    should("save, get and delete") {
        val secret1 = AllowedSecretType.M_CROSS_SIGNING_SELF_SIGNING to StoredSecret(
            Event.GlobalAccountDataEvent(SelfSigningKeyEventContent(mapOf("a" to JsonObject(mapOf())))),
            "priv1"
        )
        val secret2 = AllowedSecretType.M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
            Event.GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf("b" to JsonObject(mapOf())))),
            "priv2"
        )

        newSuspendedTransaction {
            cut.save(1, mapOf(secret1))
            cut.get(1) shouldBe mapOf(secret1)
            cut.save(1, mapOf(secret1, secret2))
            cut.get(1) shouldBe mapOf(secret1, secret2)
            cut.delete(1)
            cut.get(1) shouldBe null
        }
    }
})