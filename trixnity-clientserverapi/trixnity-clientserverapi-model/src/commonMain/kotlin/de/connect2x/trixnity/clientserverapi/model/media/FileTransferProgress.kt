package de.connect2x.trixnity.clientserverapi.model.media

data class FileTransferProgress(
    val transferred: Long,
    val total: Long?,
)