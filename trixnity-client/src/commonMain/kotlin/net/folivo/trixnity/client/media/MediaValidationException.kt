package net.folivo.trixnity.client.media

data class MediaValidationException(
    val expectedHash: String?,
    val actualHash: String?,
) : IllegalStateException("could not validate media due to different or missing hashes(expectedHash=$expectedHash actualHash=$actualHash)")