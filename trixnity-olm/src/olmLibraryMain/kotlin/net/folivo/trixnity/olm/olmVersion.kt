package net.folivo.trixnity.olm

import net.folivo.trixnity.olm.OlmLibrary.get_library_version

actual suspend fun getOlmVersion(): OlmVersion = generateSequence { MutableWrapper(0u) }.take(3).toList()
    .also {
        get_library_version(it[0], it[1], it[2])
    }.let {
        OlmVersion(it[0].value.toInt(), it[1].value.toInt(), it[2].value.toInt())
    }
