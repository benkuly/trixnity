![Version](https://maven-badges.herokuapp.com/maven-central/net.folivo/trixnity-core/badge.svg)

# Trixnity

Trixnity is a cross-plattform [Matrix](matrix.org) client SDK written in Kotlin. This SDK supports JS and JVM (native
not working yet) as targets. [Ktor](https://github.com/ktorio/ktor) is used for the HTTP client/server and
[kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) for the serialization/deserialization.

If you want to use Trixnity in combination with Spring Boot, have a look
at [matrix-spring-boot-sdk](https://github.com/benkuly/matrix-spring-boot-sdk)

You need help? Ask your questions in [#trixnity:imbitbu.de](https://matrix.to/#/#trixnity:imbitbu.de).

## Client

The client module of Trixnity gives you access to the Matrix Client-Server API.

There is a working example, which runs on JVM and NodeJS in the `trixnity-examples` directory.

### Installation

#### Java/Kotlin

Add `net.folivo:triynity-rest-client` to your project.

You also need to add an engine to your project, that you can find [here](https://ktor.io/docs/http-client-engines.html).

#### Javascript

Coming soon.

### Usage

#### Create `MatrixClient`

The most important class of this library is `MatrixClient`. It's constructor needs some parameters:

- `HttpClient` with an engine.
- `MatrixClientProperties`, which contains some information to connect to the homeserver.
- (optional) `SyncBatchTokenService`, which saves the sync token. You should implement it with a database backend for
  example, so that the client knows, which was the last sync batch token.
- (optional) `Set<EventContentSerializerMapping<out RoomEventContent>>` allows you to add custom room events.
- (optional) `Set<EventContentSerializerMapping<out StateEventContent>>` allows you to add custom state events.

Here is a typical example, how to create a `MatrixClient`:

```kotlin
private val matrixClient = MatrixClient(
    HttpClient(Java),
    MatrixClientProperties(
        MatrixHomeServerProperties("you.home.server"),
        "superSecretToken"
    )
)
```

#### Use Matrix Client-Server API

You have access to the Matrix Client-Server API via `MatrixClient`. Currently not all endpoints are implemented. If you
need more, feel free to contribute or open an issue.

Example 1: You can send messages.

```kotlin
matrixClient.room.sendRoomEvent(
    RoomId("awoun3w8fqo3bfq92a", "your.home.server"),
    TextMessageEventContent("hello from platform $Platform")
)
```

Example 2: You can receive different type of events.

```kotlin
// first register your event handlers
val textMessageEventFlow = matrixClient.sync.events<TextMessageEventContent>()
val memberEventFlow = matrixClient.sync.events<MemberEventContent>()
val allEventsFlow = matrixClient.sync.allEvents() // this is a shortcut for .events<EventContent>()

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

matrixClient.sync.start() // you need to start the sync to receive messages

delay(30000) // wait some time

matrixClient.sync.stop() // stop the client
```

## Appservice

The appservice module of Trixnity contains a webserver, which hosts the Matrix Application Service API.

### Installation

#### Java/Kotlin

Add `net.folivo:triynity-rest-appservice` to your project.

You also need to add an engine to your project, that you can find [here](https://ktor.io/docs/engines.html)

#### Javascript

Not supported by Ktor yet.

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