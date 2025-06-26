package net.folivo.trixnity.olm

actual suspend fun getOlmVersion(): OlmVersion {
    initOlm()
    return js("Olm.get_library_version()").unsafeCast<Array<Number>>()
        .let { OlmVersion(it[0].toInt(), it[1].toInt(), it[2].toInt()) }
}