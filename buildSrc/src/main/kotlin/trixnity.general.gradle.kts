group = "net.folivo"
version = withVersionSuffix("4.10.1")

if (System.getenv("WITH_LOCK")?.toBoolean() == true) {
    dependencyLocking {
        lockAllConfigurations()
    }

    val dependenciesForAll by tasks.registering(DependencyReportTask::class) { }
}