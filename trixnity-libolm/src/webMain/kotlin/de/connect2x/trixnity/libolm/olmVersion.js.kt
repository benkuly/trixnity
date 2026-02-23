package de.connect2x.trixnity.libolm

import kotlin.js.get
import kotlin.js.toInt

actual fun getOlmVersion(): OlmVersion {
    return get_library_version()
        .let { OlmVersion(
            checkNotNull(it[0]).toInt(),
            checkNotNull(it[1]).toInt(),
            checkNotNull(it[2]).toInt(),
        ) }
}