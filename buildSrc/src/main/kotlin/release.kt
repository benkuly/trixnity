val isCI = System.getenv("CI") != null
val isRelease = System.getenv("CI_COMMIT_TAG")?.matches("^v\\d+.\\d+.\\d+.*".toRegex()) ?: false
fun withVersionSuffix(version: String) = when {
    isRelease -> {
        val commitTagVersion = System.getenv("CI_COMMIT_TAG").removePrefix("v")
        check(version == commitTagVersion.substringBefore("-")) {
            "version from code ($version) does not match commit tag version ($commitTagVersion)"
        }
        commitTagVersion
    }

    isCI -> "$version-SNAPSHOT-" + System.getenv("CI_COMMIT_SHORT_SHA")
    else -> "$version-LOCAL"
}
