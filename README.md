# Trixnity

Trixnity is a cross-plattform (JVM, JS, Native) matrix-client sdk written in Kotlin.

## Installation

### Kotlin or Java

Add `net.folivo:triynity-rest-client` to your project.

### Javascript

Coming soon.

## Usage

### MatrixClient

The most important class of this library is `MatrixClient`. It's constructor needs `MatrixClientProperties`, which
contains some information to connect to the homeserver.

```kotlin
private val matrixClient = MatrixClient(
    MatrixClientProperties(
        MatrixHomeServerProperties("you.home.server"),
        "superSecretToken"
    ),
    CIO // choose an engine from here https://ktor.io/docs/http-client-engines.html
)
```

The `MatrixClient` also allows you to register custom event types in its constructor.

#### send messages

You can use `MatrixClient` to send messages:

```kotlin
suspend fun sendMessage() {
    matrixClient.room.sendRoomEvent(
        RoomId("awoun3w8fqo3bfq92a", "your.home.server"),
        TextMessageEventContent("hello from platform $Platform")
    )
}
```

#### retrieve messages

You can use `MatrixClient` to retrieve messages:

TODO