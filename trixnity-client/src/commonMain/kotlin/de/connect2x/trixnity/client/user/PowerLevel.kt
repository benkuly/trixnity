package de.connect2x.trixnity.client.user

sealed interface PowerLevel : Comparable<PowerLevel> {
    object Creator : PowerLevel
    data class User(val level: Long) : PowerLevel

    override operator fun compareTo(other: PowerLevel): Int =
        when (this) {
            is Creator -> if (other is Creator) 0 else 1
            is User -> when (other) {
                is Creator -> -1
                is User -> this.level.compareTo(other.level)
            }
        }

    operator fun compareTo(other: Long): Int =
        when (this) {
            is Creator -> 1
            is User -> this.level.compareTo(other)
        }

    companion object {
        operator fun Long.compareTo(other: PowerLevel): Int =
            when (other) {
                is Creator -> -1
                is User -> this.compareTo(other.level)
            }
    }
}