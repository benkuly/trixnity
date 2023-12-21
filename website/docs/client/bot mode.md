---
sidebar_position: 16
---

# Bot mode

The bot mode disables many features of the client and speeds it up. You don't have access to usual services like
`RoomService` or `UserService`. Instead, you need to manually listen to the sync events via `SyncApiClient` and send
events via `RoomsApiClient` (both can be received via `MatrixClient.api`).
You can encrypt and decrypt events by using `MatrixClient.roomEventEncryptionServices`.

## Usage

Override the `modules` in `MatrixClientConfiguration`. For example:

```kotlin
matrixClient.login(...){
    modules = createTrixnityBotModules()
}
```