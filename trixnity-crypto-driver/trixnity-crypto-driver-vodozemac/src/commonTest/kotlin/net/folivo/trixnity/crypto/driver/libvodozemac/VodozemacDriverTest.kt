package net.folivo.trixnity.crypto.driver.vodozemac

import net.folivo.trixnity.crypto.driver.test.AccountTest
import net.folivo.trixnity.crypto.driver.test.PkDecryptionTest
import net.folivo.trixnity.crypto.driver.test.PkEncryptionTest
import net.folivo.trixnity.crypto.driver.test.PkSigningTest
import net.folivo.trixnity.crypto.driver.test.SasTest
import net.folivo.trixnity.crypto.driver.test.SessionTest
import net.folivo.trixnity.crypto.driver.test.UtilityTest

class VodozemacAccountTest : AccountTest(VodozemacCryptoDriver)
class VodozemacPkEncryptionTest : PkEncryptionTest(VodozemacCryptoDriver)
class VodozemacPkDecryptionTest : PkDecryptionTest(VodozemacCryptoDriver)
class VodozemacPkSigningTest : PkSigningTest(VodozemacCryptoDriver)
class VodozemacSasTest : SasTest(VodozemacCryptoDriver)
class VodozemacSessionsTest : SessionTest(VodozemacCryptoDriver)
class VodozemacUtilityTest : UtilityTest(VodozemacCryptoDriver)