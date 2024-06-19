package net.folivo.trixnity.client.store.repository.test

import io.kotest.core.spec.style.ShouldSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
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
    disabledSimultaneousReadWriteTests: Boolean = false,
    customRepositoryTransactionManager: suspend () -> RepositoryTransactionManager? = { null },
    repositoriesModuleBuilder: suspend () -> Module
) {
    lateinit var di: Koin
    lateinit var coroutineScope: CoroutineScope

    beforeTest {
        val repositoriesModule = repositoriesModuleBuilder()
        coroutineScope = CoroutineScope(Dispatchers.Default)
        di = koinApplication {
            modules(
                listOf(
                    repositoriesModule,
                    module {
                        single { MatrixClientConfiguration(storeTimelineEventContentUnencrypted = true) }
                        single { coroutineScope }
                    },
                    createDefaultEventContentSerializerMappingsModule(),
                    createDefaultMatrixJsonModule()
                )
            )
        }.koin
    }
    afterTest { coroutineScope.cancel() }

    repositoryTransactionManagerTest(
        disabledRollbackTest = disabledRollbackTest,
        disabledSimultaneousReadWriteTests = disabledSimultaneousReadWriteTests,
        customRepositoryTransactionManager = customRepositoryTransactionManager
    ) { di }

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