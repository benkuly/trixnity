rootProject.name = "trixnity"
include("trixnity-core")
include("trixnity-olm")
include("trixnity-client")
include("trixnity-client-api")
include("trixnity-appservice")
include("examples")
include("examples:api-client-multiplatform-ping")
include("examples:client-multiplatform-ping")

pluginManagement { // FIXME can be removed in kotlin version > 1.5.21
    repositories {
        mavenCentral()
        maven("https://plugins.gradle.org/m2/")
    }
}