package net.folivo.trixnity.api.client

// setting a user agent may break server discovery, because CORS does not work anymore
internal actual val platformUserAgent: String? = null