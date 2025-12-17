package net.folivo.trixnity.crypto.driver.vodozemac.sas

import net.folivo.trixnity.crypto.driver.sas.SasFactory
import net.folivo.trixnity.vodozemac.sas.Sas

object VodozemacSasFactory : SasFactory {
    override fun invoke(): VodozemacSas = VodozemacSas(Sas())
}