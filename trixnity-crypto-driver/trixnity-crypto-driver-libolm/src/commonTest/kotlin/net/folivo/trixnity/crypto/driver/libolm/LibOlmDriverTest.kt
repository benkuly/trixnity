package net.folivo.trixnity.crypto.driver.libolm

import net.folivo.trixnity.crypto.driver.test.*

class LibOlmAccountTest : AccountTest(LibOlmCryptoDriver)
class LibOlmGroupSessionText : GroupSessionTest(LibOlmCryptoDriver)
class LibOlmInboundGroupSessionTest : GroupSessionTest(LibOlmCryptoDriver)
class LibOlmPkEncryptionTest : PkEncryptionTest(LibOlmCryptoDriver)
class LibOlmPkDecryptionTest : PkDecryptionTest(LibOlmCryptoDriver)
class LibOlmPkSigningTest : PkSigningTest(LibOlmCryptoDriver)
class LibOlmSasTest : SasTest(LibOlmCryptoDriver)
class LibOlmSessionsTest : SessionTest(LibOlmCryptoDriver)
class LibOlmUtilityTest : UtilityTest(LibOlmCryptoDriver)