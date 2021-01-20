# trixnity

Trixnity is a cross-plattform (JVM, JS, Native) matrix-client sdk written in kotlin.

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
    makeHttpClient( // this will change in future and is only needed because of a bug in ktor
        MatrixClientProperties(
            MatrixHomeServerProperties("you.home.server"),
            "superSecretToken"
        )
    )
)
```

You can now use `MatrixClient` to e. g. send messages:

```kotlin
suspend fun sendMessage() {
    matrixClient.room.sendRoomEvent<MessageEvent, MessageEventContent>(
        RoomId("awoun3w8fqo3bfq92a", "you.home.server"),
        TextMessageEventContent("hello from platform $Platform")
    )
}
```

or retrieve messages:

The `MatrixClient` also allows you to register custom event types.

### Customize

Coming soon (allows to register custom event types).