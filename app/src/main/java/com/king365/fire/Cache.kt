package com.king365.fire

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// ---------- Catalogue gardé sur l'appareil ----------

/// Le catalogue (chaînes, films, séries) est enregistré sur la Fire TV après chaque chargement.
/// Au démarrage suivant l'appli s'ouvre tout de suite avec cette copie, puis va chercher
/// la version à jour en arrière-plan. Une copie de plus de 24 heures est ignorée.
object CatalogCache {
    private const val DUREE_MS = 24L * 60 * 60 * 1000
    private var dossier: File? = null

    fun init(context: Context) {
        dossier = File(context.filesDir, "catalogue").apply { mkdirs() }
    }

    /// Un fichier par abonnement et par mode : changer de compte ne mélange pas les catalogues
    private fun fichier(nom: String): File? {
        val d = dossier ?: return null
        val compte = (Prefs.mode + "|" + Prefs.server + "|" + Prefs.username + "|" + Prefs.m3uUrl)
            .hashCode().toString().replace("-", "n")
        return File(d, "$compte-$nom.json")
    }

    private fun lire(nom: String): JSONObject? {
        val f = fichier(nom) ?: return null
        if (!f.exists()) return null
        if (System.currentTimeMillis() - f.lastModified() > DUREE_MS) return null
        return runCatching { JSONObject(f.readText()) }.getOrNull()
    }

    private fun ecrire(nom: String, contenu: JSONObject) {
        val f = fichier(nom) ?: return
        runCatching { f.writeText(contenu.toString()) }
    }

    fun vider() {
        runCatching { dossier?.listFiles()?.forEach { it.delete() } }
    }

    // ----- Conversion -----

    private fun JSONObject.texte(cle: String): String? =
        if (!has(cle) || isNull(cle)) null else optString(cle).ifEmpty { null }

    private fun categorieVers(c: Category) = JSONObject().put("i", c.id).put("n", c.name)
    private fun categorieDe(o: JSONObject) = Category(o.optString("i"), o.optString("n"))

    private fun chaineVers(c: LiveChannel) = JSONObject()
        .put("i", c.id).put("n", c.name).put("l", c.icon).put("c", c.categoryId)
        .put("a", c.hasArchive).put("d", c.archiveDays).put("u", c.url)

    private fun chaineDe(o: JSONObject) = LiveChannel(
        o.optString("i"), o.optString("n"), o.texte("l"), o.texte("c"),
        o.optBoolean("a"), o.optInt("d"), o.texte("u")
    )

    private fun filmVers(m: Movie) = JSONObject()
        .put("i", m.id).put("n", m.name).put("l", m.icon).put("e", m.ext)
        .put("r", m.rating).put("c", m.categoryId).put("y", m.year ?: JSONObject.NULL)
        .put("g", m.genre).put("a", m.added ?: JSONObject.NULL)

    private fun filmDe(o: JSONObject) = Movie(
        o.optString("i"), o.optString("n"), o.texte("l"), o.optString("e"),
        o.optDouble("r", 0.0), o.texte("c"),
        if (o.isNull("y")) null else o.optInt("y"), o.texte("g"),
        if (o.isNull("a")) null else o.optLong("a")
    )

    private fun serieVers(s: SeriesItem) = JSONObject()
        .put("i", s.id).put("n", s.name).put("l", s.cover).put("r", s.rating)
        .put("c", s.categoryId).put("a", s.added ?: JSONObject.NULL)

    private fun serieDe(o: JSONObject) = SeriesItem(
        o.optString("i"), o.optString("n"), o.texte("l"), o.optDouble("r", 0.0),
        o.texte("c"), if (o.isNull("a")) null else o.optLong("a")
    )

    private fun <T> tableau(liste: List<T>, vers: (T) -> JSONObject): JSONArray {
        val a = JSONArray()
        liste.forEach { a.put(vers(it)) }
        return a
    }

    private fun <T> liste(a: JSONArray?, de: (JSONObject) -> T): List<T> {
        if (a == null) return emptyList()
        return (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let(de) }
    }

    // ----- Chaînes -----

    suspend fun chargerChaines(): Boolean = withContext(Dispatchers.IO) {
        val o = lire("live") ?: return@withContext false
        val chaines = liste(o.optJSONArray("chaines"), ::chaineDe)
        if (chaines.isEmpty()) return@withContext false
        Session.liveCategories = liste(o.optJSONArray("categories"), ::categorieDe)
        Session.liveChannels = chaines
        true
    }

    suspend fun enregistrerChaines(): Unit = withContext(Dispatchers.IO) {
        if (Session.liveChannels.isEmpty()) return@withContext
        runCatching { ecrire("live", JSONObject()
            .put("categories", tableau(Session.liveCategories, ::categorieVers))
            .put("chaines", tableau(Session.liveChannels, ::chaineVers))) }
    }

    // ----- Films et séries -----

    suspend fun chargerVod(): Boolean = withContext(Dispatchers.IO) {
        val o = lire("vod") ?: return@withContext false
        val films = liste(o.optJSONArray("films"), ::filmDe)
        val series = liste(o.optJSONArray("series"), ::serieDe)
        if (films.isEmpty() && series.isEmpty()) return@withContext false
        Session.vodCategories = liste(o.optJSONArray("catFilms"), ::categorieDe)
        Session.seriesCategories = liste(o.optJSONArray("catSeries"), ::categorieDe)
        if (films.isNotEmpty()) Session.allMovies = films
        if (series.isNotEmpty()) Session.allSeries = series
        true
    }

    suspend fun enregistrerVod(): Unit = withContext(Dispatchers.IO) {
        val films = Session.allMovies ?: emptyList()
        val series = Session.allSeries ?: emptyList()
        if (films.isEmpty() && series.isEmpty()) return@withContext
        runCatching { ecrire("vod", JSONObject()
            .put("catFilms", tableau(Session.vodCategories, ::categorieVers))
            .put("catSeries", tableau(Session.seriesCategories, ::categorieVers))
            .put("films", tableau(films, ::filmVers))
            .put("series", tableau(series, ::serieVers))) }
    }
}
