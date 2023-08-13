package net.folivo.trixnity.client.room.outbox

import net.folivo.trixnity.core.model.events.MessageEventContent
import kotlin.reflect.KClass

data class OutboxMessageMediaUploaderMapping<T : MessageEventContent>(
    val kClass: KClass<T>,
    val uploader: UploadAndTransformMessageEventContent
) {
    companion object {
        inline fun <reified C : MessageEventContent> of(
            noinline uploader: UploadAndTransformMessageEventContent
        ): OutboxMessageMediaUploaderMapping<C> {
            return OutboxMessageMediaUploaderMapping(C::class, uploader)
        }
    }
}