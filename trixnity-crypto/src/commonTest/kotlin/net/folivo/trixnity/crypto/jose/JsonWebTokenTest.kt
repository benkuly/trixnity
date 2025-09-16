package net.folivo.trixnity.crypto.jose

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class JsonWebTokenTest {

    fun String.toX509PublicKey(): ByteArray = Base64.decode(
        replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
    )

    @Test
    fun `Json Web Signature - test validation of non-signed JWT`() = runTest {
        val jsonWebToken = JsonWebToken.fromString(
            "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImlhdCI6MTUxNjIzOTAyMn0."
        )
        assertEquals(TokenAlgorithm.None, jsonWebToken.header.algorithm, "Token algorithm should be 'none'")
        assertEquals(null, jsonWebToken.signature, "Signature should not be present")
    }

    @Test
    fun `Json Web Signature - test decoding of claims`() {
        val jsonWebToken = JsonWebToken.fromString(
            "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImlhdCI6MTUxNjIzOTAyMn0."
        )

        @Serializable
        data class AdditionalClaims(val name: String, val admin: Boolean)
        val claims = jsonWebToken.deserializePayload<AdditionalClaims>()
        assertEquals("1234567890", claims.subject)
        assertEquals(1516239022, claims.issuedAt)
        assertEquals("John Doe", claims.additional.name)
        assertEquals(true, claims.additional.admin)
    }

    @Test
    fun `Json Web Signature - test validation on RS256-signed JWT`() = runTest {
        val rsaPublicKey = """
            -----BEGIN PUBLIC KEY-----
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtAT41TwFsI2MTqf2IGKc
            v9Sn0QITybIEHJCvvP1//RnpOv0/87OPRe0zTpTMa7TuFwE9HPZTjY75gDxqJIhL
            sjrZ14XSm1tDiM+YVNcFfwDLT81qKAxN2u3QzlLxw4QYcr3D70EzHArHWDTvmVBj
            NyGb0EnqncFIWiBlZSzObHuRBbxdNQC+4Y9eSHtvLIkd3Qhd79DnTgjYDLDqCXzj
            ztbHjxpZ4yIeV7hRFa3DHshj8jaFPE3rxENtWk4UPxlwSp5b/FTADoO2f5ya/lGK
            B7a/fkiLoc9zqj1sfubhDRfBF6Y/p6jeeT0lSq6KQIo3wiW8liEoIWDipupDlqiV
            BwIDAQAB
            -----END PUBLIC KEY-----
        """.trimIndent()
        val jsonWebToken = JsonWebToken.fromString(
            "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImlhdCI6MTUxNjIzOTAyMn0.kzB2b4zkxFjnhLNREpMHdnTG_f-nylhLxz4DHuuX8upilNxuQpQqSOd0A58GqPlXaQuy9OD4ugLKhvduKRzOMc0EJoBPADwX9TYDFJ152dKyORMbbRnx9EdFUZ5T7UvLoHuo1iKuLGTvAaa6nA0Oy_l7k6FrCbuwkSjWaCpJOtQUPkovSWeIX3YW0uttI-Ve6u-VmOwKhNpg_kGvHawyUzrB30w9yb2whID7Wvd0xdG5edHIPcrznVRDUlCfTmCq3J3uz4BW4TYs1KwkVvd6D_wRNCHnJwkmnSUO-5REULnKdD17MlnwYIcaAr1XJDfy1AAp5p2cbF3vY-5bfo7pEw"
        )
        assertEquals(TokenAlgorithm.RS256, jsonWebToken.header.algorithm, "Token algorithm should be 'RS256'")
        assertNotEquals(null, jsonWebToken.signature, "Signature should be present")
        assertTrue(jsonWebToken.verifySignature(rsaPublicKey), "Signature should be valid")
    }

}
