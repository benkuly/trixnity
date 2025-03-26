---
sidebar_position: 15
---

# Customizing

The client can be customized via dependency injection:

```kotlin
matrixClient.login(...){
    modulesFactories += ::createCustomModule
}
```

Be aware to always create new modules because a module saves your class instances and therefore is reused, which we
don't want!

## Custom events

One goal in developing Trixnity was the ability to add custom events. This is achieved by mapping event types to serializers. In this example, 
we added a new message event: `net.folivo.dino`. As a developer, you can then receive events of this Kotlin type like all other default events.

```kotlin
@Serializable
data class DinoEventContent(
    val dinoCount: Long,
) : MessageEventContent {
    // just ignore these properties when you don't need them
    override val relatesTo: RelatesTo? = null
    override val mentions: Mentions? = null
    override val externalUrl: String? = null
}

val customMappings = createEventContentSerializerMappings {
    messageOf<DinoEventContent>("net.folivo.dino")
    // or if you have a custom serializer
    // messageOf("net.folivo.dino", DingoEventContentSerializer)
}

// add the module to MatrixClient as shown above
fun createCustomModule() = module {
    single<EventContentSerializerMappings> {
        DefaultEventContentSerializerMappings + customMappings
    }
}
```