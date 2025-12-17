package net.folivo.trixnity.crypto.driver.libolm.sas

import net.folivo.trixnity.crypto.driver.sas.SasFactory
import net.folivo.trixnity.libolm.OlmSAS

object LibOlmSasFactory : SasFactory {
    override fun invoke(): LibOlmSas = LibOlmSas(OlmSAS.create())
}