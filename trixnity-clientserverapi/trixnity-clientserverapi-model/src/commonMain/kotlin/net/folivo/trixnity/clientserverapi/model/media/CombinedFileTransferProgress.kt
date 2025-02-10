package net.folivo.trixnity.clientserverapi.model.media

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

class CombinedFileTransferProgress : Flow<FileTransferProgress?> {
    private val allProgress = MutableStateFlow<Set<MutableStateFlow<FileTransferProgress?>>>(setOf())

    fun acquire(): MutableStateFlow<FileTransferProgress?> {
        val new = MutableStateFlow<FileTransferProgress?>(null)
        allProgress.update { it + new }
        return new
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun collect(collector: FlowCollector<FileTransferProgress?>) {
        allProgress.flatMapLatest { allProgress ->
            combine(allProgress) {
                it.filterNotNull().reduceOrNull { acc, fileTransferProgress ->
                    val transferred = acc.transferred + fileTransferProgress.transferred
                    val total = if (acc.total != null || fileTransferProgress.total != null) {
                        (acc.total ?: 0) + (fileTransferProgress.total ?: 0)
                    } else null
                    FileTransferProgress(transferred, total)
                }
            }
        }.collect(collector)
    }
}
