package net.folivo.trixnity.clientserverapi.model.media

data class FileTransferProgress(
    val transferred: Long,
    val total: Long?,
)