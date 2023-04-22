---
sidebar_position: 13
---

# Create MatrixClient

With `MatrixClient` you have access to the whole library. It can be instantiated
by various static functions,
e.g. `MatrixClient.login(...)`. You always need to pass a `repositoriesModule`
for a Database, `mediaStore` for media and a `CouroutineScope`,
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