package de.connect2x.trixnity.idb.utils

sealed interface KeyPath {

    data class Single(val value: String) : KeyPath {
        companion object
    }

    data class Multiple(val values: List<String>) : KeyPath {
        constructor(vararg values: String) : this(values.toList())

        companion object
    }

    companion object
}