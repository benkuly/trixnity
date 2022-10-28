package net.folivo.trixnity.client.store.repository.realm

import com.benasher44.uuid.uuid4
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import kotlinx.serialization.json.JsonObject
import net.folivo.trixnity.client.store.StoredSecret
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.SecretType
import java.io.File

class RealmSecretsRepositoryTest : ShouldSpec({
    timeout = 10_000
    val realmDbPath = "build/${uuid4()}"
    lateinit var realm: Realm
    lateinit var cut: RealmSecretsRepository

    beforeTest {
        File(realmDbPath).deleteRecursively()
        realm = Realm.open(
            RealmConfiguration.Builder(
                schema = setOf(
                    RealmSecrets::class,
                )
            ).apply { directory(realmDbPath) }.build()
        )

        cut = RealmSecretsRepository(createMatrixEventJson())
    }
    afterTest {
        File(realmDbPath).deleteRecursively()
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

        writeTransaction(realm) {
            cut.save(1, mapOf(secret1))
            cut.get(1) shouldBe mapOf(secret1)
            cut.save(1, mapOf(secret1, secret2))
            cut.get(1) shouldBe mapOf(secret1, secret2)
            cut.delete(1)
            cut.get(1) shouldBe null
        }
    }
})