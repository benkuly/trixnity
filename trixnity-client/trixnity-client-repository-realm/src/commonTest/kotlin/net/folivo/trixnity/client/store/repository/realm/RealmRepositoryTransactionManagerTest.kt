package net.folivo.trixnity.client.store.repository.realm

import com.benasher44.uuid.uuid4
import io.kotest.core.spec.style.ShouldSpec
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class TestEntity : RealmObject {
    @PrimaryKey
    var id: Long = 0
    var value: String = ""
}

private suspend fun testWrite(id: Long) = withRealmWrite {
    copyToRealm(TestEntity().apply {
        this.id = id
        value = "test"
    })
}

private suspend fun testRead() = withRealmRead {
    query<TestEntity>().count().find()
}

class RealmRepositoryTransactionManagerTest : ShouldSpec({
    timeout = 5_000

    lateinit var tm: RealmRepositoryTransactionManager
    lateinit var realm: Realm
    beforeTest {
        val realmDbPath = "build/test-db/${uuid4()}"
        realm = Realm.open(
            RealmConfiguration.Builder(
                schema = setOf(
                    TestEntity::class,
                )
            ).apply { directory(realmDbPath) }.build()
        )
        tm = RealmRepositoryTransactionManager(realm)
    }
    context("writeTransaction") {
        should("not lock") {
            tm.writeTransaction {
                testWrite(0)
                testRead()
                tm.writeTransaction {
                    testWrite(1)
                    testRead()
                }
            }
        }
    }

    context("readTransaction") {
        should("not lock") {
            tm.readTransaction {
                testRead()
                tm.readTransaction {
                    testRead()
                }
            }
        }
    }
})