---
sidebar_position: 13
---

# Create MatrixClient

With `MatrixClient` you have access to the whole library. It can be instantiated
by various static functions,
e.g. `MatrixClient.login(...)`. You always need to pass a `repositoriesModule`
for a Database and `mediaStore` for media.

Secrets are also stored in the store. Therefore, you should encrypt the store!

```kotlin
val repositoriesModule = createRepositoriesModule() // e.g. createExposedRepositoriesModule(...)
val mediaStore = createMediaStore() // e.g. OkioMediaStore(...)

val matrixClient = MatrixClient.fromStore(
    repositoriesModule = repositoriesModule,
    mediaStore = mediaStore,
).getOrThrow() ?: MatrixClient.login(
    baseUrl = Url("https://example.org"),
    identifier = User("username"),
    password = "password",
    repositoriesModule = repositoriesModule,
    mediaStore = mediaStore,
).getOrThrow()

matrixClient.startSync() // important to fully start the client!
// do something
matrixClient.stop() // important to fully stop the client!
```

To get the `baseUrl` via server discovery you can use the `.serverDiscovery()`
extension on `UserId`s or `String`s.