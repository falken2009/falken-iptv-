package com.king365.fire

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.security.MessageDigest
import java.util.Calendar
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// ---------- Codes d'activation ----------

/// Abonnement payant pour une appli installée en dehors d'une boutique.
///
/// Le principe : chaque Fire Stick affiche un « numéro d'appareil » de 8 caractères.
/// Le client te l'envoie après son paiement, tu génères un code avec l'outil
/// `outils/generer-code.py`, il le saisit dans l'appli. Le code ne marche que sur cet
/// appareil et contient sa propre date de fin : la vérification se fait sans serveur,
/// donc l'appli reste utilisable même sans internet.
object Licence {
    /// Caractères du code : ni I, ni O, ni 0, ni 1, pour éviter les confusions à la saisie
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /// Les dates sont comptées en jours depuis cette origine
    private const val ORIGINE_ANNEE = 2026

    /// 9999 = abonnement sans date de fin
    private const val A_VIE = 9999

    private lateinit var sp: android.content.SharedPreferences

    fun init(context: Context) {
        sp = context.getSharedPreferences("licence", Context.MODE_PRIVATE)
    }

    // ----- Numéro d'appareil -----

    @SuppressLint("HardwareIds")
    fun deviceId(context: Context): String {
        val brut = (Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "")
            .ifBlank { android.os.Build.FINGERPRINT } + "|" + context.packageName
        val hash = MessageDigest.getInstance("SHA-256").digest(brut.toByteArray())
        return (0 until 8).map { ALPHABET[(hash[it].toInt() and 0xFF) % ALPHABET.length] }.joinToString("")
    }

    // ----- Vérification d'un code -----

    private fun mac(cle: String, message: String): ByteArray {
        val m = Mac.getInstance("HmacSHA256")
        m.init(SecretKeySpec(cle.toByteArray(), "HmacSHA256"))
        return m.doFinal(message.toByteArray())
    }

    /// Transforme les 12 caractères du code en 7 octets
    private fun decoder(code: String): ByteArray? {
        val propre = code.uppercase().filter { it in ALPHABET }
        if (propre.length != 12) return null
        var valeur = 0L
        propre.forEach { c -> valeur = valeur * 32 + ALPHABET.indexOf(c) }
        // 12 caractères = 60 bits ; les 4 bits de bourrage du bas sont retirés,
        // il reste les 56 bits utiles, soit 7 octets
        val utile = valeur shr 4
        return ByteArray(7) { i -> ((utile shr ((6 - i) * 8)) and 0xFF).toByte() }
    }

    /// Nombre de jours de l'origine jusqu'à aujourd'hui
    private fun aujourdHui(): Int {
        val origine = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear(); set(ORIGINE_ANNEE, Calendar.JANUARY, 1)
        }.timeInMillis
        return ((System.currentTimeMillis() - origine) / 86_400_000L).toInt()
    }

    /// Jour de fin contenu dans le code, ou null si le code est faux
    private fun finDuCode(context: Context, code: String): Int? {
        val octets = decoder(code) ?: return null
        val fin = ((octets[0].toInt() and 0xFF) shl 8) or (octets[1].toInt() and 0xFF)
        val attendu = mac(BuildConfig.LICENCE_SECRET, deviceId(context) + "|" + fin)
        for (i in 0 until 5) if (octets[i + 2] != attendu[i]) return null
        return fin
    }

    // ----- Enregistrement -----

    /// Résultat de la saisie d'un code, en clair pour l'utilisateur
    fun activer(context: Context, code: String): String {
        val fin = finDuCode(context, code)
            ?: return "Code refusé. Vérifie qu'il a bien été créé pour le numéro ${deviceId(context)}."
        if (fin != A_VIE && fin <= aujourdHui()) return "Ce code est arrivé à expiration."
        sp.edit().putString("code", code.uppercase().trim()).apply()
        actif = true
        joursRestants = if (fin == A_VIE) -1 else fin - aujourdHui()
        return if (fin == A_VIE) "Activé sans limite de temps ✓"
        else "Activé pour encore $joursRestants jour${if (joursRestants > 1) "s" else ""} ✓"
    }

    fun desactiver() {
        sp.edit().remove("code").apply()
        actif = false
        joursRestants = 0
    }

    var actif = false
        private set

    /// -1 = sans limite de temps
    var joursRestants = 0
        private set

    /// À appeler au démarrage : relit le code enregistré et vérifie qu'il est toujours valable
    fun verifier(context: Context) {
        val code = sp.getString("code", null)
        if (code == null) {
            actif = false
            joursRestants = 0
            return
        }
        val fin = finDuCode(context, code)
        if (fin == null) {
            actif = false
            joursRestants = 0
            return
        }
        if (fin == A_VIE) {
            actif = true
            joursRestants = -1
            return
        }
        joursRestants = fin - aujourdHui()
        actif = joursRestants > 0
    }

    val texteEtat: String
        get() = when {
            actif && joursRestants < 0 -> "Abonnement sans limite de temps ✓"
            actif -> "Abonnement actif : encore $joursRestants jour${if (joursRestants > 1) "s" else ""}"
            else -> "Aucun abonnement"
        }
}
