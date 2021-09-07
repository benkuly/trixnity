package net.folivo.trixnity.olm

actual val olmVersion: OlmVersion = js("Olm.get_library_version()").unsafeCast<Array<Number>>()
    .let { OlmVersion(it[0].toInt(), it[1].toInt(), it[2].toInt()) }