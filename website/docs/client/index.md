---
sidebar_position: 11
---

# Client

The client module provides a high level Matrix client implementation. It allows you to easily implement clients by just
rendering data from and passing user interactions to Trixnity.

## Features

- [x] exchangeable database
    - in memory (e. g. for tests)
    - [trixnity-client-repository-exposed](https://gitlab.com/trixnity/trixnity/-/tree/main/trixnity-client/trixnity-client-repository-exposed)
      implements a
      database for trixnity-client
      with [Exposed](https://github.com/JetBrains/Exposed). This supports
      JVM
      based platforms only.
    - [trixnity-client-repository-realm](https://gitlab.com/trixnity/trixnity/-/tree/main/trixnity-client/trixnity-client-repository-realm)
      implements a
      database for trixnity-client
      with [realm](https://github.com/realm/realm-kotlin). This supports
      JVM/Android/Native.
    - [trixnity-client-repository-indexeddb](https://gitlab.com/trixnity/trixnity/-/tree/main/trixnity-client/trixnity-client-repository-indexeddb)
      implements a
      database for trixnity-client
      with [indexeddb](https://github.com/JuulLabs/indexeddb). This supports
      JS (browser).
- [x] extremely fast reactive cache on top of the database using async
  transactions
- [x] exchangeable media store
    - in memory (e. g. for tests)
    - [trixnity-client-media-okio](https://gitlab.com/trixnity/trixnity/-/tree/main/trixnity-client/trixnity-client-media-okio)
      implements a file system based media
      store with [okio](https://github.com/square/okio). This supports
      JVM/Android/Native/NodeJs.
    - [trixnity-client-media-indexeddb](https://gitlab.com/trixnity/trixnity/-/tree/main/trixnity-client/trixnity-client-media-indexeddb)
      implements a store
      with [indexeddb](https://github.com/JuulLabs/indexeddb). This
      supports JS (browser).
- [x] very fast sync processing because of async transactions, so Trixnity
  doesn't need to wait until all events are saved to the database
- [x] media support (thumbnail generation, offline "upload", huge files,
  etc.)
- [x] E2E (olm, megolm)
- [x] verification
- [x] cross signing
- [x] fallback keys
- [x] room key backup
- [x] room key requests (only between own verified devices and when key
  backup is disabled)
- [x] room list
- [x] timelines
- [x] room upgrades (invisible due to merged timelines and auto-join)
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

## Create MatrixClient

With `MatrixClient` you have access to the whole library. It can be instantiated
by various static functions,
e.g. `MatrixClient.login(...)`. You always need to pass a `repositoriesModule`
for a Database and a `CouroutineScope`,
which will be used for the lifecycle of the client.

Secrets are also stored in the store. Therefore, you should encrypt the store!

```kotlin
val repositoriesModule = createRepositoriesModule() // e.g. createExposedRepositoriesModule(...)
val mediaStore = createMediaStore() // e.g. OkioMediaStore(...)
val scope = CoroutineScope(Dispatchers.Default) // should be managed by a lifecycle (e.g. Android Service)

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

## Read data

Most data in Trixnity is wrapped into Kotlins `Flow`. This means, that you
get the current value, but also every
future values. This is useful when e.g. the display name or the avatar of a user
changes, because you only need to
rerender that change and not your complete application.

There are some important data, which are described below:

### Rooms

To get the room list, call `matrixClient.room.getAll()`.
With `matrixClient.room.getById(...)` you can get one room.

### Users

To get all members of a room, call `matrixClient.user.getAll(...)`. Because room
members are loaded lazily, you should
also call `matrixClient.user.loadMembers(...)` as soon as you open a room.
With `matrixClient.user.getById(...)` you can
get one user.

### Timeline and TimelineEvents

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

### Outbox

Messages, that were sent with Trixnity can be accessed
with `matrixClient.room.getOutbox()` as long as they are not
received (also called "echo") from the matrix server.

## Other operations

Many operations can be done
with [trixnity-clientserverapi-client](https://gitlab.com/trixnity/trixnity/-/tree/main/trixnity-clientserverapi/trixnity-clientserverapi-client).
You have access to it
via `matrixClient.api`. There are also some high level operations, which are
managed by Trixnity. Some of them are
described below.

### Send messages

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

### Media

To upload media there is `MediaService` (can be accessed
with `matrixClient.media`).

### Verification

To verify own and other devices there is `VerificationService` (can be accessed
with `matrixClient.verification`).

### Cross Signing

To bootstrap cross signing there is `KeyService` (can be accessed
with `matrixClient.key`).

## Customizing

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