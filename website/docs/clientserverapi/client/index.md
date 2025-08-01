---
sidebar_position: 21
---

# Client

## Create MatrixClientServerApiClient

Here is a typical example, how to create a `MatrixClientServerApiClient`:

```kotlin
val matrixRestClient = MatrixClientServerApiClientImpl(
    baseUrl = Url("http://host"),
    authProvider = MatrixAuthProvider.classicInMemory("accessToken"),
)
```

## Use Matrix Client-Server API

Example 1: You can send messages.

```kotlin
matrixRestClient.room.sendRoomEvent(
    RoomId("!awoun3w8fqo3bfq92a:your.home.server"),
    RoomMessageEventContent.TextBased.Text("hello from platform $Platform")
)
```

Example 2: You can receive different type of events from sync.

```kotlin
matrixRestClient.sync.subscribeContent<RoomMessageEventContent.TextBased.Text> { println(it.content.body) }
matrixRestClient.sync.subscribeContent<MemberEventContent> { println("${it.content.displayName} did ${it.content.membership}") }
matrixRestClient.sync.subscribeEachEvent { println(it) }

matrixRestClient.sync.start() // you need to start the sync to receive messages
delay(30.seconds) // wait some time
matrixRestClient.sync.stop() // stop the sync
```
