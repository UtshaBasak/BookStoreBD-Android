package io.github.utshabasak.bookstorebd.core.datastore

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.ProviderException
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Encrypts small secrets, such as the refresh token, before they are written to disk. */
interface SecretCipher {
    fun encrypt(plain: String): String

    /** Null when the value cannot be read back, for instance after the key was lost. */
    fun decrypt(encoded: String): String?
}

/**
 * The phone's own signing key. Its private half never leaves the keystore; the
 * server holds the public half and checks every session refresh against it.
 */
interface DeviceKeys {
    /** Makes a new key pair, replacing any old one, and returns its public half: base64 SPKI. */
    fun createKey(): String

    /** The current key's public half, or null when there is none. */
    fun publicKey(): String?

    /** Signs [payload] with the private key: base64 of a DER-encoded ECDSA signature. Null with no key. */
    fun sign(payload: ByteArray): String?

    fun deleteKey()
}

private const val ANDROID_KEYSTORE = "AndroidKeyStore"

/** StrongBox, a separate secure chip, exists from Android 9; older phones use the regular keystore. */
private val STRONGBOX_SUPPORTED = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

/**
 * AES-256-GCM with a key generated inside the Android keystore - in StrongBox
 * where the phone has one - which cannot be exported, so a copy of the app's
 * files is not enough to read the session.
 */
@Singleton
class KeystoreSecretCipher
    @Inject
    constructor() : SecretCipher {
        private val keyStore: KeyStore by lazy { KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) } }

        override fun encrypt(plain: String): String {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            return Base64.encodeToString(sealed, Base64.NO_WRAP)
        }

        override fun decrypt(encoded: String): String? =
            try {
                val sealed = Base64.decode(encoded, Base64.NO_WRAP)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, existingKey() ?: return null, GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
                String(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES), Charsets.UTF_8)
            } catch (_: GeneralSecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }

        private fun existingKey(): SecretKey? = (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey

        @Synchronized
        private fun key(): SecretKey = existingKey() ?: generate(strongBox = STRONGBOX_SUPPORTED) ?: generate(strongBox = false)!!

        private fun generate(strongBox: Boolean): SecretKey? =
            try {
                KeyGenerator
                    .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                    .apply {
                        init(
                            KeyGenParameterSpec
                                .Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                                .setKeySize(KEY_BITS)
                                .setRandomizedEncryptionRequired(true)
                                .apply { if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setIsStrongBoxBacked(true) }
                                .build(),
                        )
                    }.generateKey()
            } catch (_: ProviderException) {
                // StrongBoxUnavailableException (API 28+) is a ProviderException; the caller tries again without.
                null
            }

        private companion object {
            const val ALIAS = "bookstorebd.session.v1"
            const val TRANSFORMATION = "AES/GCM/NoPadding"
            const val KEY_BITS = 256
            const val TAG_BITS = 128
            const val IV_BYTES = 12
        }
    }

/** A P-256 signing key in the Android keystore (StrongBox where available). */
@Singleton
class KeystoreDeviceKeys
    @Inject
    constructor() : DeviceKeys {
        private val keyStore: KeyStore by lazy { KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) } }

        @Synchronized
        override fun createKey(): String {
            deleteKey()
            val pair =
                generate(strongBox = STRONGBOX_SUPPORTED)
                    ?: generate(strongBox = false)
                    ?: error("The keystore could not create a signing key")
            return Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP)
        }

        override fun publicKey(): String? =
            keyStore.getCertificate(ALIAS)?.publicKey?.encoded?.let { Base64.encodeToString(it, Base64.NO_WRAP) }

        override fun sign(payload: ByteArray): String? {
            val key = keyStore.getKey(ALIAS, null) as? PrivateKey ?: return null
            val signature = Signature.getInstance("SHA256withECDSA").apply { initSign(key) }
            signature.update(payload)
            return Base64.encodeToString(signature.sign(), Base64.NO_WRAP)
        }

        @Synchronized
        override fun deleteKey() {
            if (keyStore.containsAlias(ALIAS)) keyStore.deleteEntry(ALIAS)
        }

        private fun generate(strongBox: Boolean) =
            try {
                KeyPairGenerator
                    .getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
                    .apply {
                        initialize(
                            KeyGenParameterSpec
                                .Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                                .setDigests(KeyProperties.DIGEST_SHA256)
                                .apply { if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setIsStrongBoxBacked(true) }
                                .build(),
                        )
                    }.generateKeyPair()
            } catch (_: ProviderException) {
                // StrongBoxUnavailableException (API 28+) is a ProviderException; the caller tries again without.
                null
            }

        private companion object {
            const val ALIAS = "bookstorebd.device.v1"
        }
    }
