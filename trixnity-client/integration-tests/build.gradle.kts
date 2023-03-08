import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain()
    val jvmTarget = addDefaultJvmTargetWhenEnabled()?.apply {
        val integrationTest by compilations.creating
        testRuns.create(integrationTest.name).apply {
            setExecutionSourceFrom(integrationTest)
        }.executionTask.configure {
            enabled = (isCI && HostManager.hostIsMac).not()
            useJUnitPlatform()
        }
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        val jvmIntegrationTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":trixnity-client"))
                implementation(project(":trixnity-client:trixnity-client-repository-exposed"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}")
                implementation("io.kotest:kotest-common:${Versions.kotest}")
                implementation("io.kotest:kotest-assertions-core:${Versions.kotest}")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:${Versions.kotlinxDatetime}")
                implementation("com.benasher44:uuid:${Versions.uuid}")
                implementation("io.ktor:ktor-client-java:${Versions.ktor}")
                implementation("io.ktor:ktor-client-logging:${Versions.ktor}")
                implementation("org.testcontainers:testcontainers:${Versions.testContainers}")
                implementation("org.testcontainers:junit-jupiter:${Versions.testContainers}")
                implementation("ch.qos.logback:logback-classic:${Versions.logback}")
                implementation("com.h2database:h2:${Versions.h2}")
            }
        }
    }
}