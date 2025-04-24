---
sidebar_position: 2
---

# Getting Started

## Add Trixnity to you project

Select from [modules](/docs/modules) the dependency you need and add it to you project:

```kotlin
// get version from https://gitlab.com/trixnity/trixnity/-/releases
val trixnityVersion = "x.x.x"

fun trixnity(module: String, version: String = trixnityVersion) =
    "net.folivo:trixnity-$module:$version"

// for example:
implementation(trixnity("client"))
```

Alternatively, add the Trixnity BOM as a `platform` dependency. This then allows for version resolution of Trixnity
modules:

```kotlin
val trixnityVersion = "x.x.x"

dependencies {
    implementation(platform("net.folivo:trixnity-bom:$trixnityVersion"))

    // Trixnity dependency versions are covered by the Trixnity BOM
    implementation("net.folivo:trixnity-client")
}
```

### Ktor Client engine

Modules containing `-client` in the name (for example `trixnity-client` or `trixnity-clientserverapi-client`) also need
a Ktor client engine, that can be found [here](https://ktor.io/docs/http-client-engines.html).

### Close a Client

Many classes and functions in Trixnity have optional `httpClientEngine` and `httpClientConfig` parameters. These allow
to reuse a `HttpClientEngine` (which is highly recommended) and configure the underlying `HttpClient`.

Additionally, you should always `close()` a class or use `use`, when not needed anymore. For example

```kotlin
httpClient.close()
MatrixClientServerApiClientImpl().use {
    // do something
}
```

## Examples

If you want to see Trixnity in action, take a look into
the [trixnity-examples](https://gitlab.com/trixnity/trixnity-examples).
You may also take a look into
the [integration tests](https://gitlab.com/trixnity/trixnity/-/tree/main/trixnity-client/integration-tests).

For a messenger implementation you can take a look into the community
project [Smalk](https://gitlab.com/terrakok/smalk).

If you you just want to implement a messenger without having to dive too deep into trixnity-client and Matrix, consider
using [trixnity-messenger](https://gitlab.com/connect2x/trixnity-messenger).

## dev builds

Dev builds are published on each commit to main.
Add `https://gitlab.com/api/v4/projects/26519650/packages/maven` to your
maven repositories. You can find
the complete version names here (
containing `DEV`): https://gitlab.com/trixnity/trixnity/-/packages?search[]=trixnity-bom