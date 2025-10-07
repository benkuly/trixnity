package net.folivo.trixnity.crypto.driver.libvodozemac

import net.folivo.trixnity.crypto.driver.test.AccountTest
import net.folivo.trixnity.crypto.driver.test.PkDecryptionTest
import net.folivo.trixnity.crypto.driver.test.PkEncryptionTest
import net.folivo.trixnity.crypto.driver.test.PkSigningTest
import net.folivo.trixnity.crypto.driver.test.SasTest
import net.folivo.trixnity.crypto.driver.test.SessionTest
import net.folivo.trixnity.crypto.driver.test.UtilityTest

class LibVodozemacAccountTest : AccountTest(LibVodozemacCryptoDriver)
class LibVodozemacPkEncryptionTest : PkEncryptionTest(LibVodozemacCryptoDriver)
class LibVodozemacPkDecryptionTest : PkDecryptionTest(LibVodozemacCryptoDriver)
class LibVodozemacPkSigningTest : PkSigningTest(LibVodozemacCryptoDriver)
class LibVodozemacSasTest : SasTest(LibVodozemacCryptoDriver)
class LibVodozemacSessionsTest : SessionTest(LibVodozemacCryptoDriver)
class LibVodozemacUtilityTest : UtilityTest(LibVodozemacCryptoDriver)