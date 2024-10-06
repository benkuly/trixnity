package net.folivo.trixnity.client.media

object MediaTooLargeException : IllegalStateException("your file is larger then the servers max upload size")