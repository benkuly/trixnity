package net.folivo.trixnity.client.mocks

import net.folivo.trixnity.client.store.TransactionManager
import net.folivo.trixnity.client.store.TransactionManagerImpl

class TransactionManagerMock(
    val repositoryTransactionManagerMock: RepositoryTransactionManagerMock = RepositoryTransactionManagerMock(),
    private val delegate: TransactionManager = TransactionManagerImpl(repositoryTransactionManagerMock),
) : TransactionManager by delegate