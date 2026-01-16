package de.connect2x.trixnity.serverserverapi.model

fun requestAuthenticationBody(
    content: String? = null,
    destination: String,
    method: String,
    origin: String,
    uri: String,
): String =
    if (content.isNullOrBlank()) {
        """{"destination":"$destination","method":"$method","origin":"$origin","uri":"$uri"}"""
    } else {
        """{"content":$content,"destination":"$destination","method":"$method","origin":"$origin","uri":"$uri"}"""
    }