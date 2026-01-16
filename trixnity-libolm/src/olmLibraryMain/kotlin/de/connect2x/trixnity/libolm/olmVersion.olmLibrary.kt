package de.connect2x.trixnity.libolm

import de.connect2x.trixnity.libolm.OlmLibrary.get_library_version

actual fun getOlmVersion(): OlmVersion = generateSequence { MutableWrapper(0u) }.take(3).toList()
    .also {
        get_library_version(it[0], it[1], it[2])
    }.let {
        OlmVersion(it[0].value.toInt(), it[1].value.toInt(), it[2].value.toInt())
    }
