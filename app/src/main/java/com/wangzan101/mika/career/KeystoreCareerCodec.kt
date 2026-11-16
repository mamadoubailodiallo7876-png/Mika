package com.wangzan101.mika.career

import android.util.Base64
import com.wangzan101.mika.core.career.CareerCodec
import com.wangzan101.mika.settings.KeyCipher

/**
 * Encrypts the career database (profile, documents, applications) with an Android Keystore key
 * before it reaches disk. The Keystore key never leaves the device; uninstalling the app makes the
 * ciphertext unreadable. [CareerStore] still writes atomically and keeps corrupt copies.
 */
class KeystoreCareerCodec(private val cipher: KeyCipher) : CareerCodec {
    override fun encode(plain: String): String =
        Base64.encodeToString(cipher.encrypt(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)

    override fun decode(stored: String): String =
        String(cipher.decrypt(Base64.decode(stored, Base64.NO_WRAP)), Charsets.UTF_8)
}
