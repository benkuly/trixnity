package de.connect2x.trixnity.crypto.driver.vodozemac.sas

import de.connect2x.trixnity.crypto.driver.sas.SasFactory
import de.connect2x.trixnity.vodozemac.sas.Sas

object VodozemacSasFactory : SasFactory {
    override fun invoke(): VodozemacSas = VodozemacSas(Sas())
}