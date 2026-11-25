package io.github.akhamiak.claudeusage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Tokens(
    val access: String,
    val refresh: String?,
    val expiresAtMs: Long,
    val scope: String,
)

data class PendingLogin(val verifier: String, val state: String, val scopes: String)

interface TokenStorage {
    fun load(): Tokens?
    fun save(t: Tokens)
    fun clear()
    fun loadPending(): PendingLogin?
    fun savePending(p: PendingLogin?)
}

/**
 * Tokens encrypted with a non-exportable AES-256-GCM key in Android Keystore (§5.3),
 * stored in private SharedPreferences. Never logged.
 */
class KeystoreTokenStore(context: Context) : TokenStorage {
    private val prefs = context.applicationContext.getSharedPreferences("tokens", Context.MODE_PRIVATE)

    /** Cheap presence check (no decryption) for rendering. */
    fun hasTokens(): Boolean = prefs.contains(KEY_TOKENS)

    /** Last 4 characters only (§5.3). */
    fun maskedTail(): String? = load()?.access?.takeLast(4)?.let { "…$it" }

    override fun load(): Tokens? = decrypt(KEY_TOKENS)?.let {
        try {
            val j = JSONObject(it)
            Tokens(
                access = j.getString("a"),
                refresh = j.optString("r").takeIf { r -> r.isNotEmpty() },
                expiresAtMs = j.getLong("e"),
                scope = j.optString("s"),
            )
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    override fun save(t: Tokens) {
        val j = JSONObject().put("a", t.access).put("r", t.refresh ?: "").put("e", t.expiresAtMs).put("s", t.scope)
        // commit(): the rotated refresh token must be on disk before we use the new access token.
        prefs.edit().putString(KEY_TOKENS, encrypt(j.toString())).commit()
    }

    @Synchronized
    override fun clear() {
        prefs.edit().clear().commit()
    }

    override fun loadPending(): PendingLogin? = decrypt(KEY_PENDING)?.let {
        try {
            val j = JSONObject(it)
            PendingLogin(j.getString("v"), j.getString("s"), j.getString("sc"))
        } catch (_: Exception) {
            null
        }
    }

    override fun savePending(p: PendingLogin?) {
        val e = prefs.edit()
        if (p == null) e.remove(KEY_PENDING) else
            e.putString(KEY_PENDING, encrypt(JSONObject().put("v", p.verifier).put("s", p.state).put("sc", p.scopes).toString()))
        e.commit()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, key())
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(c.iv + ct)
    }

    private fun decrypt(prefKey: String): String? {
        val stored = prefs.getString(prefKey, null) ?: return null
        return try {
            val raw = Base64.getDecoder().decode(stored)
            val c = Cipher.getInstance(TRANSFORM)
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, IV_LEN))
            String(c.doFinal(raw, IV_LEN, raw.size - IV_LEN), Charsets.UTF_8)
        } catch (_: Exception) {
            null // key lost (e.g. after restore) → treated as signed out
        }
    }

    private companion object {
        const val ALIAS = "claudeusage_tokens_v1"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_LEN = 12
        const val KEY_TOKENS = "t"
        const val KEY_PENDING = "p"
    }
}
