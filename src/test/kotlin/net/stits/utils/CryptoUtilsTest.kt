package net.stits.utils

import org.junit.Test
import java.nio.charset.StandardCharsets


class CryptoUtilsTest {
    @Test
    fun `ecdsa keys generated correctly`() {
        val keyPair = CryptoUtils.generateECDSAKeyPair()

        assert(keyPair.public.algorithm == KEY_GENERATION_ALGORITHM) { "public key algorithm is not valid" }
        assert(keyPair.public.encoded.isNotEmpty()) { "public key is empty" }
        assert(keyPair.private.algorithm == KEY_GENERATION_ALGORITHM) { "private key algorithm is not valid" }
        assert(keyPair.private.encoded.isNotEmpty()) { "private key is empty" }
    }

    @Test
    fun `complex data signed and verified correctly`() {
        val dataSlice1 = "test"
        val dataSlice2 = 123
        val dataSlice3 = ByteArray(0)

        val keyPair = CryptoUtils.generateECDSAKeyPair()
        val signature = CryptoUtils.sign(
                dataSlice1.toByteArray(StandardCharsets.UTF_8),
                dataSlice2.toBigInteger().toByteArray(),
                dataSlice3
        ) { keyPair.private }

        assert(signature.isNotEmpty()) { "signature is empty" }

        val verified = CryptoUtils.verifySignature(
                signature,
                dataSlice1.toByteArray(StandardCharsets.UTF_8),
                dataSlice2.toBigInteger().toByteArray()
                // ,dataSlice3 // also works
        ) { keyPair.public }

        assert(verified) { "signature is not verified" }
    }

    @Test
    fun `hashing works properly`() {
        val data = "some random data"
        val hash = CryptoUtils.hash(data.toByteArray(StandardCharsets.UTF_8))

        assert(hash.isNotEmpty()) { "hash is empty" }
        assert(hash.size == 32) { "hash length is not 256 bits" }
    }
}