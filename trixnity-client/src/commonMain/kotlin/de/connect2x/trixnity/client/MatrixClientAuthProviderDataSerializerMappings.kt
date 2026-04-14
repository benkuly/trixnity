package de.connect2x.trixnity.client

import de.connect2x.trixnity.clientserverapi.client.ClassicMatrixClientAuthProviderData
import de.connect2x.trixnity.clientserverapi.client.MatrixClientAuthProviderData
import de.connect2x.trixnity.clientserverapi.client.oauth2.OAuth2MatrixClientAuthProviderData
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

interface MatrixClientAuthProviderDataSerializerMappings : Set<MatrixClientAuthProviderDataSerializerMapping<*>> {
    companion object
}

data class MatrixClientAuthProviderDataSerializerMapping<T : MatrixClientAuthProviderData>(
    val id: String,
    val kClass: KClass<out T>,
    val serializer: KSerializer<T>
)

class MatrixClientAuthProviderDataSerializerMappingsBuilder {
    val mappings = mutableSetOf<MatrixClientAuthProviderDataSerializerMapping<*>>()
    fun build(): MatrixClientAuthProviderDataSerializerMappings =
        object : MatrixClientAuthProviderDataSerializerMappings,
            Set<MatrixClientAuthProviderDataSerializerMapping<*>> by mappings {}
}


operator fun MatrixClientAuthProviderDataSerializerMappings.Companion.invoke(builder: MatrixClientAuthProviderDataSerializerMappingsBuilder.() -> Unit): MatrixClientAuthProviderDataSerializerMappings =
    MatrixClientAuthProviderDataSerializerMappingsBuilder().apply(builder).build()

@Deprecated("Use invoke instead", ReplaceWith("invoke(builder)"))
fun createMatrixClientAuthProviderSerializerMappings(builder: MatrixClientAuthProviderDataSerializerMappingsBuilder.() -> Unit) =
    MatrixClientAuthProviderDataSerializerMappingsBuilder().apply(builder).build()

inline fun <reified C : MatrixClientAuthProviderData> MatrixClientAuthProviderDataSerializerMappingsBuilder.of(
    id: String,
) {
    mappings.add(MatrixClientAuthProviderDataSerializerMapping(id, C::class, serializer<C>()))
}

private val defaultMatrixClientAuthProviderDataSerializerMappings =
    MatrixClientAuthProviderDataSerializerMappings {
        of<ClassicMatrixClientAuthProviderData>("classic")
        of<OAuth2MatrixClientAuthProviderData>("oAuth2")
    }

val MatrixClientAuthProviderDataSerializerMappings.Companion.default: MatrixClientAuthProviderDataSerializerMappings
    get() = defaultMatrixClientAuthProviderDataSerializerMappings
