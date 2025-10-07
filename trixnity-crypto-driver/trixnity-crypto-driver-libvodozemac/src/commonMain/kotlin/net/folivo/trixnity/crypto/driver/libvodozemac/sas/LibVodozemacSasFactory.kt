package net.folivo.trixnity.crypto.driver.libvodozemac.sas

import net.folivo.trixnity.crypto.driver.sas.SasFactory
import net.folivo.trixnity.vodozemac.sas.Sas

object LibVodozemacSasFactory : SasFactory {
    override fun invoke(): LibVodozemacSas = LibVodozemacSas(Sas())
}