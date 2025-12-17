package net.folivo.trixnity.libolm

actual fun getOlmVersion(): OlmVersion {
    return get_library_version().unsafeCast<Array<Number>>()
        .let { OlmVersion(it[0].toInt(), it[1].toInt(), it[2].toInt()) }
}