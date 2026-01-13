---
sidebar_position: 13
---

# Create MatrixClient

With `MatrixClient` you have access to the whole library. It can be instantiated
by using `MatrixClient.create(...)`. There are some mandatory paramters:

- `repositoriesModule` which can be created by adding a `trixnity-client-respository-*` dependency and calling
  e.g. `RepositoriesModule.room(...)`. Secrets are also stored in the repository. Therefore, you should encrypt it.
- `mediaStoreModule` which can be created by adding a `trixnity-client-media-*` dependency and calling
  e.g. `MediaStoreModule.okio(...)`
- `cryptoDriverModule` which can be created by adding a `trixnity-cryptodriver-*` dependency and calling
  e.g. `CryptoDriverModule.vodozemac(...)`
- `authProviderData` which should be passed when an authentication is required (e.g. on login). It can be derived by
  calling e.g. `MatrixClientAuthProviderData.oAuth2Login(...)`.
- `configuration` for changing the default behavior of the SDK

You always need to pass a `repositoriesModule`
for a Database and `mediaStore` for media.

To get the `baseUrl` via server discovery you can use the `.serverDiscovery()` extension on `UserId`s or `String`s.

### Add HttpClientEngine

Although Ktors `HttpClient`s used by Trixnity automatically use a `HttpClientEngine` defined in the
classpath, it is highly recommended to explicitly set a shared(!) `HttpClientEngine` in the configuration. Only that
way, it can be shared between all `MatrixClient` instances. Otherwise, each `MatrixClient` creates a new
`HttpClientEngine`, which can lead to performance issues on heavy resource usage of Ktor.