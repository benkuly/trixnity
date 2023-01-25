![Version](https://maven-badges.herokuapp.com/maven-central/net.folivo/trixnity-core/badge.svg)

# Trixnity - Multiplatform Matrix SDK

Trixnity is a multiplatform [Matrix](matrix.org) SDK written in Kotlin.
You can write clients, bots, appservices and servers with
it. This SDK supports JVM (also Android), JS and Native as targets for most
modules.
[Ktor](https://github.com/ktorio/ktor) is used for the HTTP client/server and
[kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) for the
serialization/deserialization.

Trixnity aims to be strongly typed, customizable and easy to use. You can
register custom events and Trixnity will take
care, that you can send and receive that type.

**You need help? Ask your questions
in [#trixnity:imbitbu.de](https://matrix.to/#/#trixnity:imbitbu.de).**

## Overview

This project contains the following modules, which can be used independently:

- [trixnity-core](/trixnity-core) contains the model and serialization stuff for
  Matrix.
- [trixnity-olm](/trixnity-olm) implements the wrappers of the
  E2E-olm-library [libolm](https://gitlab.matrix.org/matrix-org/olm) for Kotlin
  JVM/Android/JS/Native. It also ships the
  olm binaries for most Android, JVM and Native targets.
- [trixnity-crypto](/trixnity-crypto) contains various cryptographic algorithms
  used in Matrix.
- [trixnity-api-client](/trixnity-api-client) provides tools for api client
  modules.
- [trixnity-api-server](/trixnity-api-server) provides tools for api server
  modules.
- [trixnity-clientserverapi-*](/trixnity-clientserverapi) provides modules to
  use
  the [Client-Server API](https://spec.matrix.org/latest/client-server-api/).
    - [trixnity-clientserverapi-model](/trixnity-clientserverapi/trixnity-clientserverapi-model)
      provides shared model
      classes.
    - [trixnity-clientserverapi-client](/trixnity-clientserverapi/trixnity-clientserverapi-client)
      is the client
      implementation without logic.
    - [trixnity-clientserverapi-server](/trixnity-clientserverapi/trixnity-clientserverapi-server)
      is the server
      implementation without logic.
- [trixnity-serverserverapi-*](/trixnity-serverserverapi) provides modules to
  use
  the [Server-Server API](https://spec.matrix.org/latest/server-server-api/).
    - [trixnity-serverserverapi-model](/trixnity-serverserverapi/trixnity-serverserverapi-model)
      provides shared model
      classes.
    - [trixnity-serverserverapi-client](/trixnity-serverserverapi/trixnity-serverserverapi-client)
      is the client
      implementation without logic.
    - [trixnity-serverserverapi-server](/trixnity-serverserverapi/trixnity-serverserverapi-server)
      is the server
      implementation without logic.
- [trixnity-applicationserviceapi-*](/trixnity-applicationserviceapi) provides
  modules to use
  the [Application Service API](https://spec.matrix.org/latest/application-service-api/).
    - [trixnity-applicationserviceapi-model](/trixnity-applicationserviceapi/trixnity-applicationserviceapi-model)
      provides shared model classes.
    - [trixnity-applicationserviceapi-server](/trixnity-applicationserviceapi/trixnity-applicationserviceapi-server)
      is
      the server implementation without logic.
- [trixnity-client](/trixnity-client) provides a high level client
  implementation. It allows you to easily implement
  clients by just rendering data from and passing user interactions to Trixnity.
  The key features are:
    - [x] exchangeable database
        - in memory (e. g. for tests)
        - [trixnity-client-repository-exposed](/trixnity-client/trixnity-client-repository-exposed)
          implements a
          database for trixnity-client
          with [Exposed](https://github.com/JetBrains/Exposed). This supports
          JVM
          based platforms only.
        - [trixnity-client-repository-realm](/trixnity-client/trixnity-client-repository-realm)
          implements a
          database for trixnity-client
          with [realm](https://github.com/realm/realm-kotlin). This supports
          JVM/Android/Native.
        - [trixnity-client-repository-indexeddb](/trixnity-client/trixnity-client-repository-indexeddb)
          implements a
          database for trixnity-client
          with [indexeddb](https://github.com/JuulLabs/indexeddb). This supports
          JS (browser).
        - [trixnity-client-repository-sqldelight](/trixnity-client/trixnity-client-repository-sqldelight)
          implements a
          database for trixnity-client
          with [sqldelight](https://github.com/cashapp/sqldelight/). This is not
          actively
          maintained at the moment!
    - [x] extremely fast reactive cache on top of the database
    - [x] exchangeable media store
        - in memory (e. g. for tests)
        - [trixnity-client-media-okio](/trixnity-client/trixnity-client-media-okio)
          implements a file system based media
          store with [okio](https://github.com/square/okio). This supports
          JVM/Android/Native/NodeJs.
        - [trixnity-client-media-indexeddb](/trixnity-client/trixnity-client-media-indexeddb)
          implements a file system based media
          store with [indexeddb](https://github.com/JuulLabs/indexeddb). This
          supports JS (browser).
    - [x] media support (thumbnail generation, offline "upload", huge files,
      etc.)
    - [x] E2E (olm, megolm)
    - [x] verification
    - [x] cross signing
    - [x] room key backup
    - [x] room key requests (only between own verified devices)
    - [x] room list
    - [x] timelines
    - [x] user and room display name calculation
    - [x] asynchronous message sending without caring about E2E stuff or online
      status
    - [x] redactions
    - [x] relations:
        - [x] reply (without fallback)
        - [x] replace
        - [x] thread (basic support, no separate timelines or client
          aggregations yet)
    - [x] notifications
    - [x] server discovery

- [trixnity-applicationservice](/trixnity-applicationservice) provides a basic
  high level application service
  implementation. It does not support advanced features like E2E or persistence
  at the moment.

If you want to see Trixnity in action, take a look into
the [trixnity-examples](https://gitlab.com/trixnity/trixnity-examples).
You may also take a look into
the [integration tests](./trixnity-client/integration-tests).

### Add Trixnity to you project

Select from the modules above, which dependency you need and add it to you
project:

```kotlin
val trixnityVersion =
    "x.x.x" // get version from https://gitlab.com/benkuly/trixnity/-/releases

fun trixnity(module: String, version: String = trixnityVersion) =
    "net.folivo:trixnity-$module:$version"

// for example:
implementation(trixnity("client"))
```

For Trixnity-Client and Trixnity-ClientServerAPI-Client you also need to add a
client engine to your project, that you
can find [here](https://ktor.io/docs/http-client-engines.html).

#### Olm-Library

We build [libolm](https://gitlab.matrix.org/matrix-org/olm) for various targets.
The currently supported targets
can be
found [here](https://gitlab.com/trixnity/olm-binaries/-/blob/main/build.sh). If
your platform is not supported,
feel free to open a merge request or issue.

If you are using a module, which depends on `trixnity-olm` you may need to do
some extra steps:

- JS:
    - You need to add the olm npm registry with a file called `.npmrc` and the
      content `@matrix-org:registry=https://gitlab.matrix.org/api/v4/packages/npm/`.
    - You need to provide the url path `/olm.wasm`. The file can be found in the
      official olm npm package.
      You can do this with webpack
      like [here](https://gitlab.com/trixnity/trixnity-examples/-/blob/main/webpack.config.d/webpack-olm.js).
- JVM: If your platform is not supported yet, you can
  [download or build libolm yourself](https://gitlab.matrix.org/matrix-org/olm)
  and make it available to your JVM (e.g.
  with `-Djna.library.path="build/olm"`).

## Trixnity-Client

### Create MatrixClient

With `MatrixClient` you have access to the whole library. It can be instantiated
by various static functions,
e.g. `MatrixClient.login(...)`. You always need to pass a `repositoriesModule`
for a Database and a `CouroutineScope`,
which will be used for the lifecycle of the client.

Secrets are also stored in the store. Therefore, you should encrypt the store!

```kotlin
val repositoriesModule =
    createRepositoriesModule() // e.g. createExposedRepositoriesModule(...)
val mediaStore = createMediaStore() // e.g. OkioMediaStore(...)
val scope =
    CoroutineScope(Dispatchers.Default) // should be managed by a lifecycle (e.g. Android Service)

val matrixClient = MatrixClient.fromStore(
    repositoriesModule = repositoriesModule,
    mediaStore = mediaStore,
    scope = scope,
).getOrThrow() ?: MatrixClient.login(
    baseUrl = Url("https://example.org"),
    identifier = User("username"),
    password = "password",
    repositoriesModule = repositoriesModule,
    mediaStore = mediaStore,
    scope = scope,
).getOrThrow()

matrixClient.startSync() // important to fully start the client!
```

To get the `baseUrl` via server discovery you can use the `.serverDiscovery()`
extension on `UserId`s or `String`s.

### Read data

Most data in Trixnity is wrapped into Kotlins `Flow`. This means, that you
get the current value, but also every
future values. This is useful when e.g. the display name or the avatar of a user
changes, because you only need to
rerender that change and not your complete application.

There are some important data, which are described below:

#### Rooms

To get the room list, call `matrixClient.room.getAll()`.
With `matrixClient.room.getById(...)` you can get one room.

#### Users

To get all members of a room, call `matrixClient.user.getAll(...)`. Because room
members are loaded lazily, you should
also call `matrixClient.user.loadMembers(...)` as soon as you open a room.
With `matrixClient.user.getById(...)` you can
get one user.

#### Timeline and TimelineEvents

The easiest way to compose a timeline is to
call `matrixClient.room.getTimeline(...)`.

`TimelineEvent`s represent Events in a room timeline. Each `TimelineEvent`
points to its previous and
next `TimelineEvent`, so they form a linear
graph: `... <-> TimelineEvent <-> TimelineEvent <-> TimelineEvent <-> ...`.
You can use this to navigate threw the graph and e.g. form a list out of it.

A `TimelineEvent` also contains a `Gap` which can be used to determine, if there
are missing events, which can be loaded
from the server: `GabBefore <-> TimelineEvent <-> TimelineEvent <-> GapAfter`.
If a `TimelineEvent` has a `Gap`, you can
fetch its neighbours by calling `matrixClient.room.fetchMissingEvents(...)`.

You can always get the last known `TimelineEvent` of a room
with `matrixClient.room.getLastTimelineEvents(...)`.

The following example will always print the last 20 events of a room. Note, that
this doesn't have to be the best way to compose a timeline. It is just a nice
example.

```kotlin
matrixClient.room.getLastTimelineEvents(roomId)
    .toFlowList(MutableStateFlow(20)) // we always get max. 20 TimelineEvents
    .collectLatest { timelineEvents ->
        timelineEvents.forEach { timelineEvent ->
            val event = timelineEvent.first()?.event
            val content = timelineEvent.first()?.content?.getOrNull()
            val sender = event?.sender?.let {
                matrixClient.user.getById(it, roomId).first()?.name
            }
            when {
                content is RoomMessageEventContent -> println("${sender}: ${content.body}")
                content == null -> println("${sender}: not yet decrypted")
                event is MessageEvent -> println("${sender}: $event")
                event is StateEvent -> println("${sender}: $event")
                else -> {
                }
            }
        }
    }
```

#### Outbox

Messages, that were sent with Trixnity can be accessed
with `matrixClient.room.getOutbox()` as long as they are not
received (also called "echo") from the matrix server.

### Other operations

Many operations can be done
with [trixnity-clientserverapi-client](/trixnity-clientserverapi/trixnity-clientserverapi-client).
You have access to it
via `matrixClient.api`. There are also some high level operations, which are
managed by Trixnity. Some of them are
described below.

#### Send messages

With `matrixClient.room.sendMessage(...)` you get access to an extensible DSL to
send messages. This messages will be
saved locally and sent as soon as you are online.

```kotlin
// send a text message
matrixClient.room.sendMessage(roomId) {
    text("Hi!")
}
// send an image
matrixClient.room.sendMessage(roomId) {
    image("dino.png", image, ContentType.Image.PNG)
}
```

#### Media

To upload media there is `MediaService` (can be accessed
with `matrixClient.media`).

#### Verification

To verify own and other devices there is `VerificationService` (can be accessed
with `matrixClient.verification`).

#### Cross Signing

To bootstrap cross signing there is `KeyService` (can be accessed
with `matrixClient.key`).

### Customizing

The client can be customized via dependency injection:

```kotlin
matrixClient.login(...){
    modules = createDefaultModules() + customModule
}
```

For example a module for custom events would look like this:

```kotlin
val customMappings = object : BaseEventContentSerializerMappings {
    override val message: Set<SerializerMapping<out MessageEventContent>> =
        setOf(
            of("net.folivo.dino", DinoEventContentSerializer),
        )
}
val customModule = module {
    single<EventContentSerializerMappings> {
        DefaultEventContentSerializerMappings + customMappings
    }
}
```

## Trixnity-ClientServerAPI-Client

### Usage

#### Create MatrixClientServerApiClient

Here is a typical example, how to create a `MatrixClientServerApiClient`:

```kotlin
val matrixRestClient = MatrixClientServerApiClient(
    baseUrl = Url("http://host"),
).apply { accessToken.value = "token" }
```

#### Use Matrix Client-Server API

Example 1: You can send messages.

```kotlin
matrixRestClient.room.sendRoomEvent(
    RoomId("awoun3w8fqo3bfq92a", "your.home.server"),
    TextMessageEventContent("hello from platform $Platform")
)
```

Example 2: You can receive different type of events from sync.

```kotlin
matrixRestClient.sync.subscribe<TextMessageEventContent> { println(it.content.body) }
matrixRestClient.sync.subscribe<MemberEventContent> { println("${it.content.displayName} did ${it.content.membership}") }
matrixRestClient.sync.subscribeAllEvents { println(it) }

matrixRestClient.sync.start() // you need to start the sync to receive messages
delay(30.seconds) // wait some time
matrixRestClient.sync.stop() // stop the client
```

## Trixnity-Appservice

The appservice module of Trixnity contains a webserver, which hosts the Matrix
Application-Service API.

You also need to add a server engine to your project, that you can
find [here](https://ktor.io/docs/engines.html).

### Usage

#### Register module in Ktor

The ktor Application extension `matrixApplicationServiceApiServer` is used to
register the appservice endpoints within a
Ktor server. [See here](https://ktor.io/docs/create-server.html) for more
information on how to create a Ktor server.

```kotlin
val engine: ApplicationEngine = embeddedServer(CIO, port = 443) {
    matrixAppserviceModule("asToken", handler)
}
engine.start(wait = true)
```

#### Use `DefaultApplicationServiceApiServerHandler` as default handler

The `DefaultApplicationServiceApiServerHandler`
implements `ApplicationServiceApiServerHandler`. It makes the
implementation of a Matrix Appservice more abstract and easier. For that it
uses `ApplicationServiceEventTxnService`
, `ApplicationServiceUserService` and `ApplicationServiceRoomService`, which you
need to implement.

It also allows you to retrieve events with `subscribe` in the same way as
described [here](#use-matrix-client-server-api).

## Logging

This project
uses [kotlin-logging](https://github.com/MicroUtils/kotlin-logging). On JVM this
needs a logging backend.
You can use for
example `implementation("ch.qos.logback:logback-classic:<version>")`.

## Snapshot builds

Snapshot are published on each commit to main.
Add `https://oss.sonatype.org/content/repositories/snapshots` to your
maven repositories and append `-SNAPSHOT` to the current Trixnity version.

## Build this project

### Android SDK

Install the Android SDK and add a file named `local.properties` with the
following content in the project root:

```properties
sdk.dir=/path/to/android/sdk
```
