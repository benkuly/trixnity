![Version](https://maven-badges.herokuapp.com/maven-central/net.folivo/trixnity-core/badge.svg)

# Trixnity

Trixnity is a cross-plattform [Matrix](matrix.org) client SDK written in Kotlin. This SDK supports JVM as targets (
native and JS not working yet due to a bug in kotlinx.serialization). [Ktor](https://github.com/ktorio/ktor) is used for
the HTTP client/server and
[kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) for the serialization/deserialization.

If you want to use Trixnity in combination with Spring Boot, have a look
at [matrix-spring-boot-sdk](https://github.com/benkuly/matrix-spring-boot-sdk)

You need help? Ask your questions in [#trixnity:imbitbu.de](https://matrix.to/#/#trixnity:imbitbu.de).

## Client

TODO

## Client-API

The client api module of Trixnity gives you access to the plain Matrix Client-Server API.

There is a working example in the `trixnity-examples` directory.

### Installation

Add `net.folivo:trixnity-client-api` to your project.

You also need to add an engine to your project, that you can find [here](https://ktor.io/docs/http-client-engines.html).

### Usage

#### Create `MatrixApiClient`

The most important class of this library is `MatrixApiClient`.

Here is a typical example, how to create a `MatrixRestClient`:

```kotlin
private val matrixRestClient = MatrixApiClient(hostname = "example.org").apply { accessToken = "your_token" }
```

#### Use Matrix Client-Server API

Example 1: You can send messages.

```kotlin
matrixRestClient.room.sendRoomEvent(
    RoomId("awoun3w8fqo3bfq92a", "your.home.server"),
    TextMessageEventContent("hello from platform $Platform")
)
```

Example 2: You can receive different type of events.

```kotlin
coroutineScope {
    // first register your event handlers
    val textMessageEventFlow = matrixRestClient.sync.events<TextMessageEventContent>()
    val memberEventFlow = matrixRestClient.sync.events<MemberEventContent>()
    val allEventsFlow = matrixRestClient.sync.allEvents() // this is a shortcut for .events<EventContent>()

    // wait for events in separate coroutines and print to console
    launch {
        textMessageEventFlow.collect { println(it.content.body) }
    }
    launch {
        memberEventFlow.collect { println("${it.content.displayName} did ${it.content.membership}") }
    }
    launch {
        allEventsFlow.collect { println(it) }
    }

    matrixRestClient.sync.start() // you need to start the sync to receive messages
    delay(30000) // wait some time
    matrixRestClient.sync.stop() // stop the client
}
```

## Appservice

The appservice module of Trixnity contains a webserver, which hosts the Matrix Application Service API.

### Installation

Add `net.folivo:trixnity-appservice` to your project.

You also need to add an engine to your project, that you can find [here](https://ktor.io/docs/engines.html)

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

It also allows you to retrieve events in the same way as described [here](#use-matrix-client-server-api). For example:

```kotlin
val textMessageEventFlow = defaultAppserviceService.events<TextMessageEventContent>()
launch {
    textMessageEventFlow.collect { println(it.content.body) }
}
```

## Build this project

### Android SDK

Install the Android SDK with the following packages:

- platforms;android-30
- build-tools
- ndk
- cmake

Add a file named `local.properties` with the following content in the project root:

```properties
sdk.dir=/path/to/android/sdk
```

### Libraries for c-bindings

Linux: You may need to install `libncurses5`.

Windows: Install msys2. Add cmake and run in msys2 mingw64
shell `pacman -S clang mingw-w64-x86_64-cmake mingw-w64-x86_64-ninja mingw-w64-x86_64-toolchain`
. Run all gradle tasks within the msys2 mingw64 shell!