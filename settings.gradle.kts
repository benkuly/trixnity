rootProject.name = "trixnity"
include("trixnity-core")
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
    "trixnity-client:trixnity-client-store-sqldelight",
    "trixnity-client:trixnity-client-store-exposed",
    "trixnity-client:trixnity-client-store-realm"
)
include("trixnity-applicationservice")
include("test-utils")
include(
    "examples",
    "examples:clientserverapi-client-ping",
    "examples:client-ping"
)

buildCache {
    local {
        directory = File(rootDir, ".gradle").resolve("build-cache")
        removeUnusedEntriesAfterDays = 30
    }
}