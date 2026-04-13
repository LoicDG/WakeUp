package com.loic.wakeup.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

private const val PREFS_FILE = "nfc_prefs"
private const val KEY_UID = "nfc_tag_uid"

class NfcTagStore(context: Context) {
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getUid(): String? = prefs.getString(KEY_UID, null)

    fun setUid(hexUid: String) = prefs.edit().putString(KEY_UID, hexUid).apply()

    fun clear() = prefs.edit().remove(KEY_UID).apply()
}
