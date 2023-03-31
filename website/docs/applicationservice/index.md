---
sidebar_position: 31
---

# Applicationservice

The appservice module of Trixnity contains a webserver, which hosts the Matrix
Application-Service API.

You also need to add a server engine to your project, that you can
find [here](https://ktor.io/docs/engines.html).

## Usage

### Register module in Ktor

The ktor Application extension `matrixApplicationServiceApiServer` is used to
register the appservice endpoints within a
Ktor server. [See here](https://ktor.io/docs/create-server.html) for more
information on how to create a Ktor server.

```kotlin
val engine: ApplicationEngine = embeddedServer(CIO, port = 443) {
    matrixAppserviceModule("asToken", handler)
}
engine.start(wait = true)
```

### Use `DefaultApplicationServiceApiServerHandler` as default handler

The `DefaultApplicationServiceApiServerHandler`
implements `ApplicationServiceApiServerHandler`. It makes the
implementation of a Matrix Appservice more abstract and easier. For that it
uses `ApplicationServiceEventTxnService`
, `ApplicationServiceUserService` and `ApplicationServiceRoomService`, which you
need to implement.

It also allows you to retrieve events with `subscribe` in the same way as
described [here](#use-matrix-client-server-api).