package net.folivo.trixnity.clientserverapi.model.authentication.oauth2.client

import kotlinx.serialization.Serializable

@Serializable
data class LocalizedField<T>(val default: T, val translations: Map<String, T> = emptyMap()) {
    fun forEach(consumer: (String?, T) -> Unit) {
        consumer(null, default)
        translations.forEach { (key, value) -> consumer(key, value) }
    }

    companion object {
        fun <T> defaultOnly(value: T): LocalizedField<T> = LocalizedField(value)
    }
}
