import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

    isCI -> {
        val formatter = DateTimeFormatter.ofPattern("yyMMddHHmmss")
            .withZone(ZoneId.systemDefault())
        val instant = Instant.parse(System.getenv("CI_COMMIT_TIMESTAMP"))
        val formattedInstant = formatter.format(instant)
        "$version-SNAPSHOT-$formattedInstant"
    }

    else -> "$version-LOCAL"
}
