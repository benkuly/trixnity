package net.folivo.trixnity.olm

data class OlmMessage(
    val cipherText: String,
    val type: OlmMessageType
) {
    enum class OlmMessageType(val value: Int) { // TODO use it from trixnity-core!
        INITIAL_PRE_KEY(0),
        ORDINARY(1);

        companion object {
            fun of(value: Int): OlmMessageType {
                return when (value) {
                    0 -> INITIAL_PRE_KEY
                    else -> ORDINARY
                }
            }
        }
    }
}