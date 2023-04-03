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

Modules containing `-client` in the name (for example `trixnity-client` or `trixnity-clientserverapi-client`) also need
a Ktor client engine, that can be found [here](https://ktor.io/docs/http-client-engines.html).

## Examples

If you want to see Trixnity in action, take a look into
the [trixnity-examples](https://gitlab.com/trixnity/trixnity-examples).
You may also take a look into
the [integration tests](https://gitlab.com/trixnity/trixnity/-/tree/main/trixnity-client/integration-tests).

## Snapshot builds

Snapshot are published on each commit to main.
Add `https://oss.sonatype.org/content/repositories/snapshots` to your
maven repositories and append `-SNAPSHOT` to the current Trixnity version.