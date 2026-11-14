package com.king365.fire

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp

// ---------- Langue de l'interface ----------

/// Le français est la langue d'origine : chaque texte est écrit en français dans le code,
/// et `Lang.t("texte")` renvoie sa traduction quand elle existe, sinon le français.
/// Seuls les textes principaux (accueil, réglages, boutons courants) sont traduits pour l'instant.
object Lang {
    class Choice(val code: String, val label: String)

    val choices = listOf(
        Choice("fr", "Français"),
        Choice("en", "English"),
        Choice("es", "Español")
    )

    private lateinit var sp: android.content.SharedPreferences

    /// Langue choisie (« fr » par défaut)
    var code by mutableStateOf("fr")
        private set

    /// Faux tant que la personne n'a pas choisi : l'écran de choix s'affiche alors au premier lancement
    var chosen by mutableStateOf(true)
        private set

    fun init(context: Context) {
        sp = context.getSharedPreferences("lang", Context.MODE_PRIVATE)
        chosen = sp.contains("code")
        code = sp.getString("code", null) ?: "fr"
    }

    fun choose(value: String) {
        code = value
        chosen = true
        sp.edit().putString("code", value).apply()
    }

    private val en = mapOf(
        "TV EN DIRECT" to "LIVE TV", "GUIDE" to "GUIDE", "FILMS" to "MOVIES", "SÉRIES" to "SERIES",
        "FAVORIS" to "FAVORITES", "MA LISTE" to "MY LIST", "MULTI" to "MULTI", "REPLAY" to "REPLAY",
        "RECHERCHE" to "SEARCH", "COMPTE" to "ACCOUNT", "RÉGLAGES" to "SETTINGS",
        "Réglages" to "Settings", "Langue" to "Language", "Langue de l'interface" to "Interface language",
        "Reprendre un film" to "Continue a movie", "Reprendre une série" to "Continue a series",
        "Rechercher dans cette catégorie" to "Search in this category",
        "🔎 Rechercher" to "🔎 Search", "🔎 Fermer la recherche" to "🔎 Close search",
        "Films" to "Movies", "Séries" to "Series", "Série" to "Series",
        "Ta liste est vide" to "Your list is empty", "Pas encore de favoris" to "No favorites yet",
        "Tout voir ›" to "See all ›", "Chargement…" to "Loading…",
        "Impossible de charger les films" to "Unable to load movies",
        "Impossible de charger les séries" to "Unable to load series",
        "Aucun film dans cette catégorie" to "No movies in this category",
        "Aucune série dans cette catégorie" to "No series in this category",
        "+ Ma liste" to "+ My list", "✓ Dans ma liste" to "✓ In my list",
        "🔄  Rechercher une mise à jour" to "🔄  Check for updates"
    )

    private val es = mapOf(
        "TV EN DIRECT" to "TV EN VIVO", "GUIDE" to "GUÍA", "FILMS" to "PELÍCULAS", "SÉRIES" to "SERIES",
        "FAVORIS" to "FAVORITOS", "MA LISTE" to "MI LISTA", "MULTI" to "MULTI", "REPLAY" to "REPETICIÓN",
        "RECHERCHE" to "BUSCAR", "COMPTE" to "CUENTA", "RÉGLAGES" to "AJUSTES",
        "Réglages" to "Ajustes", "Langue" to "Idioma", "Langue de l'interface" to "Idioma de la interfaz",
        "Reprendre un film" to "Continuar una película", "Reprendre une série" to "Continuar una serie",
        "Rechercher dans cette catégorie" to "Buscar en esta categoría",
        "🔎 Rechercher" to "🔎 Buscar", "🔎 Fermer la recherche" to "🔎 Cerrar la búsqueda",
        "Films" to "Películas", "Séries" to "Series", "Série" to "Serie",
        "Ta liste est vide" to "Tu lista está vacía", "Pas encore de favoris" to "Aún no hay favoritos",
        "Tout voir ›" to "Ver todo ›", "Chargement…" to "Cargando…",
        "Impossible de charger les films" to "No se pudieron cargar las películas",
        "Impossible de charger les séries" to "No se pudieron cargar las series",
        "Aucun film dans cette catégorie" to "No hay películas en esta categoría",
        "Aucune série dans cette catégorie" to "No hay series en esta categoría",
        "+ Ma liste" to "+ Mi lista", "✓ Dans ma liste" to "✓ En mi lista",
        "🔄  Rechercher une mise à jour" to "🔄  Buscar actualizaciones"
    )

    /// Traduction d'un texte français ; le français lui-même si aucune traduction n'existe
    fun t(fr: String): String = when (code) {
        "en" -> en[fr]
        "es" -> es[fr]
        else -> null
    } ?: fr
}

/// Écran de choix de la langue : au premier lancement, puis depuis les Réglages
@Composable
fun LanguageScreen(onDone: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    KingBackground(dimmed = true) {
        Column(
            Modifier.align(Alignment.Center).width(520.dp).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ScreenTitle("Langue · Language · Idioma")
            Lang.choices.forEachIndexed { i, c ->
                ButtonTile(
                    (if (Lang.chosen && Lang.code == c.code) "✓  " else "") + c.label,
                    Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                    primary = i == 0
                ) {
                    Lang.choose(c.code)
                    onDone()
                }
            }
        }
    }
}
