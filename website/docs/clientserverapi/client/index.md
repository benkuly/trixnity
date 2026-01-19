---
sidebar_position: 21
---

# Client

## Create MatrixClientServerApiClient

Here is a typical example, how to create a `MatrixClientServerApiClient`:

```kotlin
val matrixApiClient = MatrixClientServerApiClientImpl(
    authProvider = MatrixClientAuthProviderData.classic(baseUrl, accessToken).createAuthProvider(
        MatrixClientAuthProviderDataStore.inMemory(),
    ),
)
```

## Use Matrix Client-Server API

Example 1: You can send messages.

```kotlin
matrixApiClient.room.sendRoomEvent(
    RoomId("!awoun3w8fqo3bfq92a:your.home.server"),
    RoomMessageEventContent.TextBased.Text("hello from platform $Platform")
)
```

Example 2: You can receive different type of events from sync.

```kotlin
matrixApiClient.sync.subscribeContent<RoomMessageEventContent.TextBased.Text> { println(it.content.body) }
matrixApiClient.sync.subscribeContent<MemberEventContent> { println("${it.content.displayName} did ${it.content.membership}") }
matrixApiClient.sync.subscribeEachEvent { println(it) }

matrixApiClient.sync.start() // you need to start the sync to receive messages
delay(30.seconds) // wait some time
matrixApiClient.sync.stop() // stop the sync
```
