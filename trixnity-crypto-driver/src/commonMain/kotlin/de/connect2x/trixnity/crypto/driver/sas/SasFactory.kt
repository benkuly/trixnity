package de.connect2x.trixnity.crypto.driver.sas

interface SasFactory {
    operator fun invoke(): Sas
}