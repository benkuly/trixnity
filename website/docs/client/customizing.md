---
sidebar_position: 15
---

# Customizing

The client can be customized via dependency injection:

```kotlin
matrixClient.login(...){
    modules = createDefaultTrixnityModules() + customModule
}
```

## Custom events

One goal on developing Trixnity was the ability to add custom events. This is achieved by a mapping event types to
Serializers. In this example we added a new message event `net.folivo.dino`. As a developer you than can receive events
of this Kotlin type like all other default events.

```kotlin
val customMappings = createEventContentSerializerMappings {
    messageOf<DingoEventContent>("net.folivo.dino")
    // or
    messageOf("net.folivo.dino", DingoEventContentSerializer)
}
val customModule = module {
    single<EventContentSerializerMappings> {
        DefaultEventContentSerializerMappings + customMappings
    }
}
```