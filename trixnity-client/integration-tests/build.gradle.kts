import org.jetbrains.kotlin.konan.target.HostManager
import de.connect2x.conventions.isCI

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    addJvmTarget(testEnabled = (isCI && HostManager.hostIsMac).not()) {
        maxHeapSize = "8g"
        maxParallelForks = if (isCI) 3 else 1
        jvmArgs(jolOpens)
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }
        commonMain {}
        commonTest {
            dependencies {
                implementation(projects.trixnityClient)
                implementation(projects.trixnityClient.trixnityClientRepositoryExposed)
                implementation(projects.trixnityClient.trixnityClientRepositoryRoom)
                implementation(projects.trixnityClient.trixnityClientCryptodriverVodozemac)
                implementation(libs.androidx.sqlite.bundled)
                implementation(kotlin("test"))
                implementation(libs.kotest.assertions.core)
                implementation(libs.oshai.logging)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.ktor.client.java)
                implementation(libs.ktor.client.logging)
                implementation(libs.testcontainers)
                implementation(libs.testcontainers.postgresql)
                implementation(libs.testcontainers.junitJupiter)
                implementation(libs.h2)
                implementation(libs.postgresql)
                implementation(libs.hikari)
                implementation(libs.logback.classic)

                // If/When this is removed, also remove [jolOpens]
                implementation(libs.openjdk.jol)
            }
        }
    }
}

private val jolOpens = listOf(
    "--add-opens=java.base/java.io=ALL-UNNAMED",
    "--add-opens=java.base/java.lang=ALL-UNNAMED",
    "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
    "--add-opens=java.base/java.lang.ref=ALL-UNNAMED",
    "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
    "--add-opens=java.base/java.math=ALL-UNNAMED",
    "--add-opens=java.base/java.net=ALL-UNNAMED",
    "--add-opens=java.base/java.nio=ALL-UNNAMED",
    "--add-opens=java.base/java.nio.channels=ALL-UNNAMED",
    "--add-opens=java.base/java.nio.channels.spi=ALL-UNNAMED",
    "--add-opens=java.base/java.nio.charset=ALL-UNNAMED",
    "--add-opens=java.base/java.nio.file.attribute=ALL-UNNAMED",
    "--add-opens=java.base/java.security=ALL-UNNAMED",
    "--add-opens=java.base/java.security.cert=ALL-UNNAMED",
    "--add-opens=java.base/java.security.spec=ALL-UNNAMED",
    "--add-opens=java.base/java.text=ALL-UNNAMED",
    "--add-opens=java.base/java.time=ALL-UNNAMED",
    "--add-opens=java.base/java.time.format=ALL-UNNAMED",
    "--add-opens=java.base/java.time.temporal=ALL-UNNAMED",
    "--add-opens=java.base/java.time.zone=ALL-UNNAMED",
    "--add-opens=java.base/java.util=ALL-UNNAMED",
    "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
    "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
    "--add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED",
    "--add-opens=java.base/java.util.jar=ALL-UNNAMED",
    "--add-opens=java.base/java.util.zip=ALL-UNNAMED",
    "--add-opens=java.base/javax.net.ssl=ALL-UNNAMED",
    "--add-opens=java.base/sun.invoke.util=ALL-UNNAMED",
    "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
    "--add-opens=java.base/sun.nio.cs=ALL-UNNAMED",
    "--add-opens=java.base/sun.nio.fs=ALL-UNNAMED",
    "--add-opens=java.base/sun.reflect.annotation=ALL-UNNAMED",
    "--add-opens=java.base/sun.reflect.generics.factory=ALL-UNNAMED",
    "--add-opens=java.base/sun.reflect.generics.reflectiveObjects=ALL-UNNAMED",
    "--add-opens=java.base/sun.reflect.generics.repository=ALL-UNNAMED",
    "--add-opens=java.base/sun.reflect.generics.scope=ALL-UNNAMED",
    "--add-opens=java.base/sun.reflect.generics.tree=ALL-UNNAMED",
    "--add-opens=java.base/sun.security.rsa=ALL-UNNAMED",
    "--add-opens=java.base/sun.security.ssl=ALL-UNNAMED",
    "--add-opens=java.base/sun.security.util=ALL-UNNAMED",
    "--add-opens=java.base/sun.security.x509=ALL-UNNAMED",
    "--add-opens=java.base/sun.util.calendar=ALL-UNNAMED",
    "--add-opens=java.base/sun.util.locale=ALL-UNNAMED",

    "--add-opens=java.logging/java.util.logging=ALL-UNNAMED",
    "--add-opens=java.sql/java.sql=ALL-UNNAMED",
    "--add-opens=java.xml/org.xml.sax.helpers=ALL-UNNAMED",
    "--add-opens=java.xml/com.sun.xml.internal.stream.util=ALL-UNNAMED",
    "--add-opens=jdk.crypto.ec/sun.security.ec=ALL-UNNAMED",
)