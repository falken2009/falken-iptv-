package com.king365.fire

import android.content.Context
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// ---------- Synchronisation des abonnements entre la Fire TV et l'iPhone ----------

/// Les abonnements sont chiffrés (AES-256) avec le « mot de passe de synchronisation » avant d'être
/// envoyés à une base Firebase : le serveur ne voit jamais les identifiants. Le nom de la fiche est
/// lui aussi dérivé du mot de passe : on ne peut pas retrouver la fiche sans le connaître.
/// L'iPhone utilise exactement le même format (voir Sync.swift).
object Sync {
    private const val BASE = "https://falken-iptv-default-rtdb.europe-west1.firebasedatabase.app"
    private const val SALT = "falken-iptv-sync"
    private const val ROUNDS = 20_000

    private lateinit var sp: android.content.SharedPreferences

    fun init(context: Context) {
        sp = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    }

    var status by mutableStateOf("")

    var password: String
        get() = sp.getString("password", "") ?: ""
        set(v) = sp.edit().putString("password", v.trim()).apply()

    private fun docId(p: String): String =
        MessageDigest.getInstance("SHA-256").digest(("falken-sync-id|$p").toByteArray())
            .joinToString("") { "%02x".format(it) }.take(40)

    private fun key(p: String): SecretKeySpec {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        return SecretKeySpec(f.generateSecret(PBEKeySpec(p.toCharArray(), SALT.toByteArray(), ROUNDS, 256)).encoded, "AES")
    }

    /// Format : base64( nonce 12 octets + texte chiffré + étiquette GCM )
    private fun encrypt(p: String, text: String): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(p), GCMParameterSpec(128, iv))
        return Base64.encodeToString(iv + c.doFinal(text.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(p: String, b64: String): String {
        val all = Base64.decode(b64, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(p), GCMParameterSpec(128, all.copyOfRange(0, 12)))
        return String(c.doFinal(all.copyOfRange(12, all.size)))
    }

    private fun request(method: String, path: String, body: String? = null): String? {
        val conn = URL("$BASE$path").openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        try {
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /// Récupère les abonnements de l'autre appareil, les ajoute, puis publie la liste complète
    suspend fun syncNow(): String = withContext(Dispatchers.IO) {
        val p = password
        if (p.isBlank()) return@withContext "Aucun mot de passe de synchronisation"
        runCatching {
            val id = docId(p)
            var added = 0
            val remote = request("GET", "/sync/$id.json")?.takeIf { it.isNotBlank() && it != "null" }
            if (remote != null) {
                val text = decrypt(p, JSONObject(remote).getString("d"))
                val a = JSONArray(text)
                val received = (0 until a.length()).mapNotNull { i ->
                    val o = a.optJSONObject(i) ?: return@mapNotNull null
                    Prefs.Subscription(
                        "", o.optString("name"), o.optString("mode"), o.optString("server"),
                        o.optString("user"), o.optString("pass"), o.optString("m3u")
                    )
                }
                val before = Prefs.subscriptions.size
                withContext(Dispatchers.Main) { Prefs.mergeSubscriptions(received) }
                added = Prefs.subscriptions.size - before
            }
            val list = JSONArray()
            Prefs.subscriptions.forEach {
                list.put(JSONObject().put("name", it.name).put("mode", it.mode).put("server", it.server)
                    .put("user", it.username).put("pass", it.password).put("m3u", it.m3uUrl))
            }
            val doc = JSONObject().put("d", encrypt(p, list.toString())).put("t", System.currentTimeMillis())
            request("PUT", "/sync/$id.json", doc.toString())
            if (added > 0) "Synchronisé ✓ · $added abonnement(s) reçu(s)" else "Synchronisé ✓"
        }.getOrElse { "Échec de la synchronisation : ${it.message}" }
    }

    fun syncAsync() {
        if (!::sp.isInitialized || password.isBlank()) return
        Thread { status = kotlinx.coroutines.runBlocking { syncNow() } }.start()
    }
}
