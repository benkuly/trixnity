rootProject.name = "trixnity"
include("trixnity-bom")
include("trixnity-utils")
include("trixnity-core")
include("trixnity-crypto-core")
include("trixnity-crypto")
include("trixnity-libolm")
include(
    "trixnity-vodozemac",
    "trixnity-vodozemac:trixnity-vodozemac-binaries"
)
include(
    "trixnity-crypto-driver",
    "trixnity-crypto-driver:driver-test",
    "trixnity-crypto-driver:trixnity-crypto-driver-libolm",
    "trixnity-crypto-driver:trixnity-crypto-driver-vodozemac",
)
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
    "trixnity-client:trixnity-client-media-opfs",
    "trixnity-client:client-repository-test",
    "trixnity-client:trixnity-client-repository-exposed",
    "trixnity-client:trixnity-client-repository-indexeddb",
    "trixnity-client:trixnity-client-repository-room",
    "trixnity-client:trixnity-client-cryptodriver-libolm",
    "trixnity-client:trixnity-client-cryptodriver-vodozemac",
)
include("trixnity-test-utils")
include("trixnity-idb-utils")
include("ktor-test-utils")
include("idb-schemaexporter")

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://gitlab.com/api/v4/projects/68438621/packages/maven") // c2x Conventions
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://gitlab.com/api/v4/projects/68438621/packages/maven") // c2x Conventions
        maven("https://gitlab.com/api/v4/projects/72301746/packages/maven") // Lognity
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version ("1.0.0") // https://github.com/gradle/foojay-toolchains/tags
    id("de.connect2x.conventions.c2x-settings-plugin") version "20260129.102940"
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
