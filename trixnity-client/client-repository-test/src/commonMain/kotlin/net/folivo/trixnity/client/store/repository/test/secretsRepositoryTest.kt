package net.folivo.trixnity.client.store.repository.test

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import net.folivo.trixnity.client.store.StoredSecret
import net.folivo.trixnity.client.store.repository.SecretsRepository
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import net.folivo.trixnity.crypto.SecretType
import org.koin.core.Koin


fun ShouldSpec.secretsRepositoryTest(diReceiver: () -> Koin) {
    lateinit var cut: SecretsRepository
    lateinit var rtm: RepositoryTransactionManager
    beforeTest {
        val di = diReceiver()
        cut = di.get()
        rtm = di.get()
    }
    should("secretsRepositoryTest: save, get and delete") {
        val secret1 = SecretType.M_CROSS_SIGNING_SELF_SIGNING to StoredSecret(
            Event.GlobalAccountDataEvent(SelfSigningKeyEventContent(mapOf("a" to JsonObject(mapOf())))),
            "priv1"
        )
        val secret2 = SecretType.M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
            Event.GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf("b" to JsonObject(mapOf())))),
            "priv2"
        )

        rtm.writeTransaction {
            cut.save(1, mapOf(secret1))
            cut.get(1) shouldBe mapOf(secret1)
            cut.save(1, mapOf(secret1, secret2))
            cut.get(1) shouldBe mapOf(secret1, secret2)
            cut.delete(1)
            cut.get(1) shouldBe null
        }
    }
}