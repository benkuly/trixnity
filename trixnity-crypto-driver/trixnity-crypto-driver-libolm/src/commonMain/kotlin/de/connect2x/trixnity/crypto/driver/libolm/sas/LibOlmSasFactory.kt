package de.connect2x.trixnity.crypto.driver.libolm.sas

import de.connect2x.trixnity.crypto.driver.sas.SasFactory
import de.connect2x.trixnity.libolm.OlmSAS

object LibOlmSasFactory : SasFactory {
    override fun invoke(): LibOlmSas = LibOlmSas(OlmSAS.create())
}