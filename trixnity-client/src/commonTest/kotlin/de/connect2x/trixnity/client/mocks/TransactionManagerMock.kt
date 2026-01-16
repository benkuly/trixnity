package de.connect2x.trixnity.client.mocks

import de.connect2x.trixnity.client.store.TransactionManager
import de.connect2x.trixnity.client.store.TransactionManagerImpl

class TransactionManagerMock(
    val repositoryTransactionManagerMock: RepositoryTransactionManagerMock = RepositoryTransactionManagerMock(),
    private val delegate: TransactionManager = TransactionManagerImpl(repositoryTransactionManagerMock),
) : TransactionManager by delegate