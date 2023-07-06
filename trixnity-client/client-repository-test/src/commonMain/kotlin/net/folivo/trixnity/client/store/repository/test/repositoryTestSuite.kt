package net.folivo.trixnity.client.store.repository.test

import io.kotest.core.spec.style.ShouldSpec
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module

fun ShouldSpec.repositoryTestSuite(
    disabledRollbackTest: Boolean = false,
    repositoriesModuleBuilder: suspend () -> Module
) {
    val mappings = DefaultEventContentSerializerMappings
    val json = createMatrixEventJson()
    val jsonModule = module {
        single<EventContentSerializerMappings> { mappings }
        single<Json> { json }
    }
    lateinit var di: Koin

    beforeTest {
        val repositoriesModule = repositoriesModuleBuilder()
        di = koinApplication {
            modules(
                listOf(
                    repositoriesModule,
                    jsonModule
                )
            )
        }.koin
    }

    repositoryTransactionManagerTest(disabledRollbackTest) { di }

    accountRepositoryTest { di }
    crossSigningKeysRepositoryTest { di }
    deviceKeysKeysRepositoryTest { di }
    globalAccountDataRepositoryTest { di }
    inboundMegolmMessageIndexRepositoryTest { di }
    inboundMegolmSessionRepositoryTest { di }
    keyChainLinkRepositoryTest { di }
    keyVerificationStatekRepositoryTest { di }
    mediaCacheMappingRepositoryTest { di }
    olmAccountRepositoryTest { di }
    olmForgetFallbackKeyAfterRepositoryTest { di }
    olmSessionRepositoryTest { di }
    outboundMegolmSessionRepositoryTest { di }
    outdatedKeysRepositoryTest { di }
    roomAccountDataRepositoryTest { di }
    roomKeyRequestRepositoryTest { di }
    roomOutboxMessageRepositoryTest { di }
    roomRepositoryTest { di }
    roomStateRepositoryTest { di }
    roomUserRepositoryTest { di }
    secretKeyRequestRepositoryTest { di }
    secretsRepositoryTest { di }
    timelineEventRelationRepositoryTest { di }
    timelineEventRepositoryTest { di }
}