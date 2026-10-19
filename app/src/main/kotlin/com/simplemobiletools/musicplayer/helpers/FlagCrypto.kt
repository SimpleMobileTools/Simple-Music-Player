package com.simplemobiletools.musicplayer.helpers

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object FlagCrypto {
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128

    fun deriveKey(manufacturer: String, model: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest((manufacturer + model).toByteArray())

    // encrypted layout: IV(12 bytes) || ciphertext+tag
    fun decrypt(encrypted: ByteArray, key: ByteArray): ByteArray {
        val iv = encrypted.copyOfRange(0, GCM_IV_LENGTH)
        val data = encrypted.copyOfRange(GCM_IV_LENGTH, encrypted.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(data)
    }
}
