package com.king365.fire

import android.os.Environment
import java.io.File

// ---------- Sauvegarde des favoris ----------

/// Les favoris (chaînes), « Ma liste » (films et séries) sont aussi copiés dans le dossier Documents
/// de la Fire TV, à chaque changement. Après une désinstallation puis réinstallation, ils sont remis
/// tout seuls au premier lancement, ou avec le bouton des Réglages.
object Sauvegarde {
    private fun file(): File =
        File(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Falken-IPTV"), "sauvegarde.json")

    fun save(): Boolean = runCatching {
        val f = file()
        f.parentFile?.mkdirs()
        f.writeText(Prefs.exportBackup())
        true
    }.getOrDefault(false)

    /// Copie en arrière-plan, sans jamais gêner l'appli (silencieuse si l'accès au stockage est refusé)
    fun saveAsync() {
        Thread { save() }.start()
    }

    fun restore(): Boolean = runCatching {
        val f = file()
        f.exists() && Prefs.importBackup(f.readText())
    }.getOrDefault(false)

    /// Au démarrage : si l'appli est vide (réinstallation), on remet la sauvegarde
    fun restoreIfEmpty() {
        if (Prefs.favorites.isEmpty() && Prefs.watchlist.isEmpty()) restore()
    }
}
