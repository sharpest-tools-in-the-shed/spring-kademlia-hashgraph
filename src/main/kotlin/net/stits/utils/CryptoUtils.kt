package net.stits.utils

import java.math.BigInteger
import java.security.*
import java.security.spec.X509EncodedKeySpec
import java.util.*


const val KEY_GENERATION_ALGORITHM = "EC"
const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
const val HASH_ALGORITHM = "SHA-256"

/**
 * This static class provides interface to some primitive cryptographic functions
 */
class CryptoUtils {
    companion object {
        /**
         * Generates ECDSA key pair
         */
        fun generateECDSAKeyPair() = KeyPairGenerator.getInstance(KEY_GENERATION_ALGORITHM).genKeyPair()

        /**
         * Creates signature of specified data with provided private key
         */
        fun sign(vararg data: ByteArray, providePrivateKey: () -> PrivateKey): ByteArray {
            val signatureGenerator = Signature.getInstance(SIGNATURE_ALGORITHM)
            signatureGenerator.initSign(providePrivateKey())

            data.forEach { signatureGenerator.update(it) }

            return signatureGenerator.sign()
        }

        /**
         * Verifies if specified signature of specified data is created by specified public key
         */
        fun verifySignature(signature: ByteArray, vararg data: ByteArray, providePublicKey: () -> PublicKey): Boolean {
            val signatureGenerator = Signature.getInstance(SIGNATURE_ALGORITHM)
            signatureGenerator.initVerify(providePublicKey())

            data.forEach { signatureGenerator.update(it) }

            return signatureGenerator.verify(signature)
        }

        /**
         * Verifies if specified data has provided hash
         */
        fun verifyIntegrity(vararg data: ByteArray, provideHash: () -> ByteArray): Boolean {
            val newHash = hash(*data)
            val oldHash = provideHash()

            return (Arrays.equals(oldHash, newHash))
        }

        /**
         * Creates digest of specified data
         */
        fun hash(vararg data: ByteArray): ByteArray {
            val md = MessageDigest.getInstance(HASH_ALGORITHM)

            data.forEach { md.update(it) }

            return md.digest()
        }

        /**
         * Translates public key to kademlia id
         */
        fun publicKeyToId(publicKey: PublicKey): BigInteger {
            return BigInteger(publicKey.encoded)
        }

        /**
         * Translates kademlia id to ecdsa public key
         */
        fun idToPublicKey(id: BigInteger): PublicKey {
            return KeyFactory.getInstance(KEY_GENERATION_ALGORITHM).generatePublic(X509EncodedKeySpec(id.toByteArray()))
        }
    }
}