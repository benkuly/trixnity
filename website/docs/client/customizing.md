---
sidebar_position: 15
---

# Customizing

The client can be customized via dependency injection:

```kotlin
matrixClient.login(...){
    modules = createDefaultModules() + customModule
}
```

## Custom events

One goal on developing Trixnity was the ability to add custom events. This is achieved by a mapping event types to
Serializers. In this example we added a new message event `net.folivo.dino`. As a developer you than can receive events
of this Kotlin type like all other default events.

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