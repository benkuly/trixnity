package de.connect2x.trixnity.crypto.driver.vodozemac

import de.connect2x.trixnity.crypto.driver.test.AccountTest
import de.connect2x.trixnity.crypto.driver.test.PkDecryptionTest
import de.connect2x.trixnity.crypto.driver.test.PkEncryptionTest
import de.connect2x.trixnity.crypto.driver.test.PkSigningTest
import de.connect2x.trixnity.crypto.driver.test.SasTest
import de.connect2x.trixnity.crypto.driver.test.SessionTest
import de.connect2x.trixnity.crypto.driver.test.UtilityTest

class VodozemacAccountTest : AccountTest(VodozemacCryptoDriver)
class VodozemacPkEncryptionTest : PkEncryptionTest(VodozemacCryptoDriver)
class VodozemacPkDecryptionTest : PkDecryptionTest(VodozemacCryptoDriver)
class VodozemacPkSigningTest : PkSigningTest(VodozemacCryptoDriver)
class VodozemacSasTest : SasTest(VodozemacCryptoDriver)
class VodozemacSessionsTest : SessionTest(VodozemacCryptoDriver)
class VodozemacUtilityTest : UtilityTest(VodozemacCryptoDriver)