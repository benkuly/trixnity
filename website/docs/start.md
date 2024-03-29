---
sidebar_position: 2
---

# Getting Started

## Add Trixnity to you project

Select from [modules](/docs/modules) the dependency you need and add it to you project:

```kotlin
// get version from https://gitlab.com/benkuly/trixnity/-/releases
val trixnityVersion = "x.x.x"

fun trixnity(module: String, version: String = trixnityVersion) =
    "net.folivo:trixnity-$module:$version"

// for example:
implementation(trixnity("client"))
```

Alternatively, add the trixnity BOM as a `platform` dependency. This then allows for version resolution of trixnity
modules, in addition to Ktor dependencies:

```kotlin
val trixnityVersion = "x.x.x"

dependencies {
    implementation(platform("net.folivo:trixnity-bom:$trixnityVersion"))

    // trixnity dependency versions are covered by the trixnity BOM
    implementation("net.folivo:trixnity-client")

    // ktor dependencies are also covered by the trixnity BOM
    implementation("io.ktor:ktor-client-java")
}
```

Modules containing `-client` in the name (for example `trixnity-client` or `trixnity-clientserverapi-client`) also need
a Ktor client engine, that can be found [here](https://ktor.io/docs/http-client-engines.html).

## Examples

If you want to see Trixnity in action, take a look into
the [trixnity-examples](https://gitlab.com/trixnity/trixnity-examples).
You may also take a look into
the [integration tests](https://gitlab.com/trixnity/trixnity/-/tree/main/trixnity-client/integration-tests).

For a messenger implementation you can take a look into the community
project [Smalk](https://gitlab.com/terrakok/smalk).

If you you just want to implement a messenger without having to dive to deep into trixnity-client and Matrix, consider
using [trixnity-messenger](https://gitlab.com/connect2x/trixnity-messenger).

## Snapshot builds

Snapshot are published on each commit to main.
Add `https://gitlab.com/api/v4/projects/26519650/packages/maven` to your
maven repositories and append `-SNAPSHOT-COMMIT_SHORT_SHA` to the current Trixnity version. You can find
the `COMMIT_SHORT_SHA` [here](https://gitlab.com/trixnity/trixnity/-/commits/main).