package net.folivo.trixnity.olm

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class UnpaddedBase64Test {
    @Test
    fun shouldEncodeUnpaddedBase64() {
        "".encodeToByteArray().encodeUnpaddedBase64() shouldBe ""
        "f".encodeToByteArray().encodeUnpaddedBase64() shouldBe "Zg"
        "fo".encodeToByteArray().encodeUnpaddedBase64() shouldBe "Zm8"
        "foo".encodeToByteArray().encodeUnpaddedBase64() shouldBe "Zm9v"
        "foob".encodeToByteArray().encodeUnpaddedBase64() shouldBe "Zm9vYg"
        "fooba".encodeToByteArray().encodeUnpaddedBase64() shouldBe "Zm9vYmE"
        "foobar".encodeToByteArray().encodeUnpaddedBase64() shouldBe "Zm9vYmFy"
    }

    @Test
    fun shouldDecodeUnpaddedBase64() {
        "".decodeUnpaddedBase64Bytes().decodeToString() shouldBe ""
        "Zg".decodeUnpaddedBase64Bytes().decodeToString() shouldBe "f"
        "Zg==".decodeUnpaddedBase64Bytes().decodeToString() shouldBe "f"
        "Zm8".decodeUnpaddedBase64Bytes().decodeToString() shouldBe "fo"
        "Zm8=".decodeUnpaddedBase64Bytes().decodeToString() shouldBe "fo"
        "Zm9v".decodeUnpaddedBase64Bytes().decodeToString() shouldBe "foo"
        "Zm9vYg".decodeUnpaddedBase64Bytes().decodeToString() shouldBe "foob"
        "Zm9vYg==".decodeUnpaddedBase64Bytes().decodeToString() shouldBe "foob"
        "Zm9vYmE".decodeUnpaddedBase64Bytes().decodeToString() shouldBe "fooba"
        "Zm9vYmE=".decodeUnpaddedBase64Bytes().decodeToString() shouldBe "fooba"
        "Zm9vYmFy".decodeUnpaddedBase64Bytes().decodeToString() shouldBe "foobar"
    }
}