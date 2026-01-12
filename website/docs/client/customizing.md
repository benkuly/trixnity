---
sidebar_position: 15
---

# Customizing

The client can be customized via dependency injection:

```kotlin
matrixClient.create(...){
    modulesFactories += ::createCustomModule
}

fun createCustomModule() = module {
    single<EventContentSerializerMappings> {
        DefaultEventContentSerializerMappings + customMappings
    }
}
```

Be aware to always create new modules because a module saves your class instances and therefore is reused, which usually
is not wanted!

## Custom events

One goal in developing Trixnity was the ability to add custom events. This is achieved by mapping event types to
serializers. In this example,
we added a new message event: `net.folivo.dino`. As a developer, you can then receive events of this Kotlin type like
all other default events.

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

val customMappings = createEventContentSerializerMappings { // add this to the modulesFactories (see example above)
    messageOf<DinoEventContent>("net.folivo.dino")
}
```

It is also possible to add custom extensible events and content blocks. They also provide backwards compatibility via
`legecy`. For simplicity the latter is ignored in this example. If you want to support legacy events, take a look at
`TopicEventContent`.

```kotlin
val customMappings = createEventContentSerializerMappings {
    blockOf(DinoContentBlock)
    messageOf<DinoEventContent>("net.folivo.dino")
}

@Serializable
@JvmInline
value class DinoContentBlock(
    private val dinoCount: Long,
) : EventContentBlock.Default {
    override val type: EventContentBlock.Type<*> get() = Type

    companion object Type : EventContentBlock.Type<DinoContentBlock> {
        override val value: String = "net.folivo..dino"
    }
}

@Serializable(DinoEventContent.Serializer::class)
data class DinoEventContent(
    override val blocks: EventContentBlocks,
) : StateEventContent, ExtensibleEventContent<ExtensibleEventContent.Legacy.None> {
    // this is just a convenience constructor
    constructor(dinoCount: Long) : this(
        EventContentBlocks(DinoContentBlock(dinoCount)),
    )

    val dinoCount: Long? = blocks[DinoContentBlock]?.dinoCount
    override val legacy: ExtensibleEventContent.Legacy.None? = null
    override val externalUrl: String? = null

    object Serializer : ExtensibleEventContentSerializer<ExtensibleEventContent.Legacy.None, DinoEventContent>(
        "DinoEventContent",
        { blocks: EventContentBlocks, _ -> DinoEventContent(blocks) },
        ExtensibleEventContent.Legacy.None.serializer()
    )
}
```