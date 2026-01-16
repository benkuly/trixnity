package de.connect2x.trixnity.client.notification

internal fun hasGlobMatch(value: String, glob: String): Boolean {
    return buildString {
        glob.forEach { char ->
            when (char) {
                '*' -> append(".*")
                '?' -> append(".")
                else -> append(Regex.escape(char.toString()))
            }
        }
    }.toRegex().containsMatchIn(value)
}