package net.folivo.trixnity.client.store.repository.test

import io.kotest.core.spec.style.ShouldSpec
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.createDefaultEventContentSerializerMappingsModule
import net.folivo.trixnity.client.createDefaultMatrixJsonModule
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module

fun ShouldSpec.repositoryTestSuite(
    disabledRollbackTest: Boolean = false,
    customRepositoryTransactionManager: suspend () -> RepositoryTransactionManager? = { null },
    repositoriesModuleBuilder: suspend () -> Module
) {
    lateinit var di: Koin

    beforeTest {
        val repositoriesModule = repositoriesModuleBuilder()
        di = koinApplication {
            modules(
                listOf(
                    repositoriesModule,
                    module {
                        single { MatrixClientConfiguration(storeTimelineEventContentUnencrypted = true) }
                    },
                    createDefaultEventContentSerializerMappingsModule(),
                    createDefaultMatrixJsonModule()
                )
            )
        }.koin
    }

    repositoryTransactionManagerTest(disabledRollbackTest, customRepositoryTransactionManager) { di }

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
    roomUserReceiptsRepositoryTest { di }
    secretKeyRequestRepositoryTest { di }
    secretsRepositoryTest { di }
    timelineEventRelationRepositoryTest { di }
    timelineEventRepositoryTest { di }
}