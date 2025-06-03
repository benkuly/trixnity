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
val mediaStoreModule = createMediaStoreModule() // e.g. createOkioMediaStoreModule(...)

val matrixClient = MatrixClient.fromStore(
    repositoriesModule = repositoriesModule,
    mediaStoreModule = mediaStoreModule,
).getOrThrow() ?: MatrixClient.login(
    baseUrl = Url("https://example.org"),
    identifier = User("username"),
    password = "password",
    repositoriesModule = repositoriesModule,
    mediaStoreModule = mediaStoreModule,
).getOrThrow()

matrixClient.startSync() // important to fully start the client!
// do something
matrixClient.stop() // important to fully stop the client!
```

To get the `baseUrl` via server discovery you can use the `.serverDiscovery()`
extension on `UserId`s or `String`s.

### Add HttpClientEngine

Although Ktors `HttpClient`s used by Trixnity automatically use a `HttpClientEngine` defined in the
classpath, it is highly recommended to explicitly set a shared(!) `HttpClientEngine` in the configuration. Only that
way, it can be shared between all `MatrixClient` instances. Otherwise, each `MatrixClient` creates a new
`HttpClientEngine`, which can lead to performance issues on heavy usage of the SDK.