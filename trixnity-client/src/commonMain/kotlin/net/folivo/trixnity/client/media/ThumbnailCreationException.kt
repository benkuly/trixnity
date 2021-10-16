package net.folivo.trixnity.client.media

data class ThumbnailCreationException(override val cause: Throwable) : Exception(cause)