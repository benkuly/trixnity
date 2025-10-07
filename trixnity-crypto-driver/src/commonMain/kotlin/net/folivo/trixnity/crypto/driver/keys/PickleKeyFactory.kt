package net.folivo.trixnity.crypto.driver.keys

interface PickleKeyFactory {
    operator fun invoke(): PickleKey?
    operator fun invoke(value: ByteArray?): PickleKey?
    operator fun invoke(value: String?): PickleKey?
}