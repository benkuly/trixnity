package net.folivo.trixnity.client.room

sealed interface TimelineEventDecryptionFailed {
    object Timeout : TimelineEventDecryptionFailed, RuntimeException("timeout while decrypting TimelineEvent")
    object AlgorithmNotSupported : TimelineEventDecryptionFailed,
        RuntimeException("algorithm for decrypting TimelineEvent not supported")
}