group = "net.folivo"
version = withVersionSuffix("4.7.0")

if (System.getenv("WITH_LOCK")?.toBoolean() == true) {
    dependencyLocking {
        lockAllConfigurations()
    }

    val dependenciesForAll by tasks.registering(DependencyReportTask::class) { }
}