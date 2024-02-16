rootProject.name = "trixnity"
include("trixnity-bom")
include("trixnity-utils")
include("trixnity-core")
include("trixnity-crypto-core")
include("trixnity-crypto")
include("trixnity-olm")
include("trixnity-api-client")
include("trixnity-api-server")
include(
    "trixnity-clientserverapi:trixnity-clientserverapi-model",
    "trixnity-clientserverapi:trixnity-clientserverapi-client",
    "trixnity-clientserverapi:trixnity-clientserverapi-server"
)
include(
    "trixnity-serverserverapi:trixnity-serverserverapi-model",
    "trixnity-serverserverapi:trixnity-serverserverapi-client",
    "trixnity-serverserverapi:trixnity-serverserverapi-server"
)
include(
    "trixnity-applicationserviceapi:trixnity-applicationserviceapi-model",
    "trixnity-applicationserviceapi:trixnity-applicationserviceapi-server"
)
include(
    "trixnity-client",
    "trixnity-client:integration-tests",
    "trixnity-client:trixnity-client-media-indexeddb",
    "trixnity-client:trixnity-client-media-okio",
    "trixnity-client:client-repository-test",
    "trixnity-client:trixnity-client-repository-exposed",
    "trixnity-client:trixnity-client-repository-indexeddb",
    "trixnity-client:trixnity-client-repository-realm",
    "trixnity-client:trixnity-client-repository-room",
)
include("trixnity-applicationservice")
include("test-utils")

buildCache {
    local {
        directory = File(rootDir, ".gradle").resolve("build-cache")
        removeUnusedEntriesAfterDays = 30
    }
}

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.8.0") // https://github.com/gradle/foojay-toolchains/tags
}