package net.folivo.trixnity.crypto.driver.sas

interface SasFactory {
    operator fun invoke(): Sas
}