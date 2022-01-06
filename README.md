![Version](https://maven-badges.herokuapp.com/maven-central/net.folivo/trixnity-core/badge.svg)

# Trixnity - Multiplatform Matrix SDK

Trixnity is a multiplatform [Matrix](matrix.org) SDK written in Kotlin. You can write clients, bots and appservices with
it. This SDK supports JVM as targets (native and JS will follow soon). [Ktor](https://github.com/ktorio/ktor) is used
for the HTTP client/server and
[kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) for the serialization/deserialization.

Trixnity aims to be strongly typed, customizable and easy to use. You can register custom events and Trixnity will take
care, that you can send and receive that type.

If you want to use Trixnity in combination with Spring Boot, have a look
at [matrix-spring-boot-sdk](https://gitlab.com/benkuly/matrix-spring-boot-sdk)

**You need help? Ask your questions in [#trixnity:imbitbu.de](https://matrix.to/#/#trixnity:imbitbu.de).**

## Overview

This project contains the following sub-projects, which can be used independently:

- [trixnity-core](/trixnity-core) contains the model and serialization stuff for Matrix.
- [trixnity-olm](/trixnity-olm) implements the wrappers of the
  E2E-olm-library [libolm](https://gitlab.matrix.org/matrix-org/olm) for Kotlin JVM/JS/Native.
- [trixnity-client-api](/trixnity-client-api) implements
  the [Client-Server API](https://spec.matrix.org/latest/client-server-api/).
- [trixnity-client-api-model](/trixnity-client-api-model) provides
  [Client-Server API](https://spec.matrix.org/latest/client-server-api/) model classes.
- [trixnity-appservice](/trixnity-appservice) implements
  the [Application Service API](https://spec.matrix.org/latest/application-service-api/).
- [trixnity-client](/trixnity-client) provides a high level client implementation. It allows you to easily implement
  clients by just rendering data from and passing user interactions to Trixnity. The key features are:
    - [x] exchangeable database
    - [x] fast cache on top of the database
    - [x] E2E (olm, megolm)
    - [x] verification
    - [x] cross signing
        - [x] trust level calculation
        - [x] SSSS
        - [ ] signing of other keys
    - [x] room list
    - [x] timelines
    - [x] user and room display name calculation
    - [x] asynchronous message sending without caring about E2E stuff or online status
    - [x] media support (thumbnail generation, offline "upload", etc.)
    - [x] redactions
- [trixnity-client-store-exposed](/trixnity-client-store-exposed) implements a database for trixnity-client
  with [Exposed](https://github.com/JetBrains/Exposed). This only supports JVM as platform.
- [trixnity-client-store-sqldelight](/trixnity-client-store-sqldelight) implements a database for trixnity-client
  with [sqldelight](https://github.com/cashapp/sqldelight/). This is not actively maintained at the moment.

If you want to see Trixnity in action, take a look into the [examples](/examples).

We plan to add something like `trixnity-client-store-indexeddb` as a database backend for web in the future.

### Add Trixnity to you project

Just add the following to your dependencies and fill `<sub-project>` (with e.g. `client`) and `<version>` (
with the current version):

```yml
implementation("net.folivo:trixnity-<sub-project>:<version>")
```

For Trixnity-Client and Trixnity-Client-API you also need to add a client engine to your project, that you can
find [here](https://ktor.io/docs/http-client-engines.html).

#### Olm-Library

If you are using `trixnity-client` or `trixnity-olm` with JVM, you will need to install olm. You can
[Download or build it yourself](https://gitlab.matrix.org/matrix-org/olm) and then make it available by your JVM (e.g.
with `-Djna.library.path="build/olm/3.2.8/build"`)

## Trixnity-Client

### Create MatrixClient

With `MatrixClient` you have access to the whole library. It can be instantiated by various static functions,
e.g. `MatrixClient.login(...)`. You always need to pass a `StoreFactory` for a Database and a `CouroutineScope`, which
will be used for the lifecycle of the client.

Secrets are stored in the store. Therefore you should encrypt the store!

```kotlin
val storeFactory = createStoreFactory()
val scope = CoroutineScope(Dispatchers.Default)

val matrixClient = MatrixClient.fromStore(
    storeFactory = storeFactory,
    scope = scope,
) ?: MatrixClient.login(
    baseUrl = Url("https://example.org"),
    identifier = User("username"),
    password = "password",
    storeFactory = storeFactory,
    scope = scope,
)
```

It is important, that you call `matrixClient.startSync()` to fully start the client.

### Read data

Most data in Trixnity is wrapped into Kotlins `StateFlow`. This means, that you get the current value, but also every
future values. This is useful when e.g. the display name or the avatar of a user changes, because you only need to
render that change and not your complete application.

There are some important data, which are described below:

#### Rooms

To get the room list, call `matrixClient.room.getAll()`. With `matrixClient.room.getById(...)` you can get one room.

#### Users

To get all members of a room, call `matrixClient.user.getAll(...)`. Because room members are loaded lazily, you should
also call `matrixClient.user.loadMembers(...)` before. With `matrixClient.user.getById(...)` you can get one user. If
you do that to e.g. show the sender of a message, you don't need to call `loadMembers(...)`, because this information is
already delivered by the matrix server.

#### TimelineEvents

`TimelineEvent`s represent Events in a room timeline. Each `TimelineEvent` points to its previous and
next `TimelineEvent`, so they form a linear graph: `... <-> TimelineEvent <-> TimelineEvent <-> TimelineEvent <-> ...`.
You can use this to navigate threw the graph and e.g. form a list out of it.

A `TimelineEvent` also contains a `Gap` which can be used to determine, if there are missing events, which can be loaded
from the server: `GabBefore <-> TimelineEvent <-> TimelineEvent <-> GapAfter`. If a `TimelineEvent` has a `Gap`, you can
fetch is neighbours by calling `matrixClient.room.fetchMissingEvents(...)`.

You can always get the last known `TimelineEvent` of a room with `matrixClient.room.getLastTimelineEvent(...)`.

The following example will always print the last 20 events of a room:

```kotlin
matrixClient.room.getLastTimelineEvent(roomId, scope).filterNotNull().collect { lastEvent ->
    flow {
        var currentTimelineEvent: StateFlow<TimelineEvent?>? = lastEvent
        emit(lastEvent)
        while (currentTimelineEvent?.value != null) {
            val currentTimelineEventValue = currentTimelineEvent.value
            if (currentTimelineEventValue?.gap is GapBefore) {
                matrixClient.room.fetchMissingEvents(currentTimelineEventValue)
            }
            currentTimelineEvent = currentTimelineEvent.value?.let {
                matrixClient.room.getPreviousTimelineEvent(it, scope)
            }
            emit(currentTimelineEvent)
        }
    }.filterNotNull().take(20).toList().reversed().forEach { timelineEvent ->
        val event = timelineEvent.value?.event
        val content = event?.content
        val sender = event?.sender?.let { matrixClient.user.getById(it, roomId, scope).value?.name }
        when {
            content is RoomMessageEventContent ->
                println("${sender}: ${content.body}")
            content is MegolmEncryptedEventContent -> {
                val decryptedEvent = timelineEvent.value?.decryptedEvent
                val decryptedEventContent = decryptedEvent?.getOrNull()?.content
                val decryptionException = decryptedEvent?.exceptionOrNull()
                when {
                    decryptedEventContent is RoomMessageEventContent -> println("${sender}: ${decryptedEventContent.body}")
                    decryptedEvent == null -> println("${sender}: not yet decrypted")
                    decryptionException != null -> println("${sender}: cannot decrypt (${decryptionException.message})")
                }
            }
            event is MessageEvent -> println("${sender}: $event")
            event is StateEvent -> println("${sender}: $event")
            else -> {
            }
        }
    }
}
```

#### Outbox

Messages, that were sent with Trixnity can be accessed with `matrixClient.room.getOutbox()` as long as they are not
received (also called "echo") from the matrix server.

### Send data

Many operations can be done with [trixnity-client-api](/trixnity-client-api). You have access to it
via `matrixClient.api`. There are also some high level operations, which are managed by Trixnity. Some of them are
described below.

#### Send messages

With `matrixClient.room.sendMessage(...)` you get access to an extensible DSL to send messages. This messages will be
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

## Trixnity-Client-API

### Usage

#### Create MatrixApiClient

Here is a typical example, how to create a `MatrixApiClient`:

```kotlin
val matrixRestClient = MatrixApiClient(
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
coroutineScope {
    matrixRestClient.sync.subscribe<TextMessageEventContent> { println(it.content.body) }
    matrixRestClient.sync.subscribe<MemberEventContent> { println("${it.content.displayName} did ${it.content.membership}") }
    matrixRestClient.sync.subscribeAllEvents { // this is a shortcut for .subscribe<EventContent> { }
        println(it)
    }

    matrixRestClient.sync.start() // you need to start the sync to receive messages
    delay(30000) // wait some time
    matrixRestClient.sync.stop() // stop the client
}
```

## Trixnity-Appservice

The appservice module of Trixnity contains a webserver, which hosts the Matrix Application-Service API.

You also need to add a server engine to your project, that you can find [here](https://ktor.io/docs/engines.html)

### Usage

#### Register module in Ktor

The ktor Application extension `matrixAppserviceModule` is used to register the appservice endpoints within a Ktor
server. [See here](https://ktor.io/docs/create-server.html) for more informations how to create a Ktor server. The
extension needs some parameters:

- `MatrixAppserviceProperties`, which contains some configuration.
- `AppserviceService` is needed to process incoming appservice requests. You can use `DefaultAppserviceService` for a
  more abstract way.

```kotlin
val engine: ApplicationEngine = embeddedServer(CIO, port = properties.port) {
    matrixAppserviceModule(MatrixAppserviceProperties("asToken"), appserviceService)
}
engine.start(wait = true)
```

#### Use `DefaultAppserviceService`

The `DefaultAppserviceService` implements `AppserviceService`. It makes the implementation of a Matrix Appservice more
abstract and easier. For that it uses `AppserviceEventService`, `AppserviceUserService` and `AppserviceRoomService`,
which you need to implement.

It also allows you to retrieve events in the same way as described [here](#use-matrix-client-server-api).

## Logging

This project uses [kotlin-logging](https://github.com/MicroUtils/kotlin-logging). On JVM this needs a logging backend.
You can use for example ` implementation("ch.qos.logback:logback-classic:<version>")`.

## Build this project

### Android SDK

Install the Android SDK with the following packages:

- platforms;android-30
- build-tools
- ndk

Add a file named `local.properties` with the following content in the project root:

```properties
sdk.dir=/path/to/android/sdk
```

### Libraries for c-bindings

Linux:

- cmake `3.22.1` (e.g. by running `sudo ./cmake-3.22.1-linux-x86_64.sh --skip-license --exclude-subdir --prefix=/usr`).
- libncurses5
- ninja-build
- mingw-w64

Windows: Install msys2. Add cmake and run in msys2 mingw64
shell `pacman -S clang mingw-w64-x86_64-cmake mingw-w64-x86_64-ninja mingw-w64-x86_64-toolchain`
. **Important:** Run this command and all gradle tasks within the msys2 **mingw64** shell!

