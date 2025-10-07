rootProject.name = "trixnity"
include("trixnity-bom")
include("trixnity-utils")
include("trixnity-core")
include("trixnity-crypto-core")
include("trixnity-crypto")
include("trixnity-olm")
include(
    "trixnity-vodozemac",
    "trixnity-vodozemac:trixnity-vodozemac-binaries"
)
include(
    "trixnity-crypto-driver",
    "trixnity-crypto-driver:driver-test",
    "trixnity-crypto-driver:trixnity-crypto-driver-libolm",
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
)
include("trixnity-applicationservice")
include("trixnity-test-utils")
include("ktor-test-utils")

buildCache {
    val buildCacheUrl = System.getenv("GRADLE_BUILD_CACHE_URL")
    local {
        isEnabled = buildCacheUrl == null
        directory = File(rootDir, ".gradle").resolve("build-cache")
    }
    remote<HttpBuildCache> {
        isEnabled = buildCacheUrl != null
        if (buildCacheUrl != null) {
            url = uri(buildCacheUrl)
            isPush = true
            credentials {
                username = System.getenv("GRADLE_BUILD_CACHE_USERNAME")
                password = System.getenv("GRADLE_BUILD_CACHE_PASSWORD")
            }
        }
    }
}

pluginManagement {
    repositories {
        val dependencyCacheUrl = System.getenv("GRADLE_DEPENDENCY_CACHE_URL")
        if (dependencyCacheUrl != null)
            maven {
                url = uri(dependencyCacheUrl)
                authentication {
                    credentials {
                        username = System.getenv("GRADLE_DEPENDENCY_CACHE_USERNAME")
                        password = System.getenv("GRADLE_DEPENDENCY_CACHE_PASSWORD")
                    }
                }
            }
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        val dependencyCacheUrl = System.getenv("GRADLE_DEPENDENCY_CACHE_URL")
        if (dependencyCacheUrl != null)
            maven {
                url = uri(dependencyCacheUrl)
                authentication {
                    credentials {
                        username = System.getenv("GRADLE_DEPENDENCY_CACHE_USERNAME")
                        password = System.getenv("GRADLE_DEPENDENCY_CACHE_PASSWORD")
                    }
                }
            }
        mavenCentral()
        google()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version ("1.0.0") // https://github.com/gradle/foojay-toolchains/tags
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")