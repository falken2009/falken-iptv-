package com.king365.fire

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.onFocusChanged

// ---------- Catégories en rangées façon Netflix (films et séries) ----------

/// Nombre d'affiches par rangée ; la tuile « Tout voir » ouvre la catégorie complète (tri, filtres)
private const val ROW_SIZE = 30

private val PosterWidth = 128.dp

/// Ce qu'une rangée affiche, pour un film comme pour une série
private class RowItem<T>(val value: T, val name: String, val image: String?, val info: String?)

@Composable
private fun <T> CatalogRowsScreen(
    title: String,
    loadCategories: suspend () -> List<Category>,
    cachedCategories: List<Category>,
    storeCategories: (List<Category>) -> Unit,
    loadRow: suspend (Category) -> List<T>,
    describe: (T) -> RowItem<T>,
    onItem: (T) -> Unit,
    onAll: (Category) -> Unit,
    onSearch: () -> Unit
) {
    var categories by remember { mutableStateOf(cachedCategories) }
    var loading by remember { mutableStateOf(cachedCategories.isEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var askPin by remember { mutableStateOf(false) }
    // Affiche sélectionnée : image de fond et titre en haut de l'écran
    var focused by remember { mutableStateOf<RowItem<T>?>(null) }
    // La première rangée chargée prend le focus (sans attendre « Derniers ajouts », plus long à charger)
    var focusTaken by remember { mutableStateOf(false) }
    // Dernière affiche sélectionnée : retrouvée au retour d'un film (survit au changement d'écran)
    var lastRow by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    var lastItem by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    val startRow = remember { lastRow }
    val startItem = remember { lastItem }
    // Si la rangée retrouvée tarde trop à charger, la première rangée prête prend le focus
    var fallback by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(4000); fallback = true }
    // Retour : remonte à la première rangée avant de quitter l'écran
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val topFocus = remember { FocusRequester() }
    val rowScope = androidx.compose.runtime.rememberCoroutineScope()
    BackToTop(
        atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0,
        scrollToTop = { listState.scrollToItem(0) },
        focusTop = topFocus
    )

    LaunchedEffect(categories.isNotEmpty()) {
        if (categories.isNotEmpty() && startRow > 0) runCatching { listState.scrollToItem(startRow) }
    }
    LaunchedEffect(Unit) {
        if (categories.isEmpty()) {
            runCatching { loadCategories() }
                .onSuccess { categories = it; storeCategories(it) }
                .onFailure { error = friendlyMessage(it) }
            loading = false
        }
    }

    BackdropBackground(focused?.image) {
        when {
            loading -> Loading()
            error != null -> Message("Impossible de charger les catégories", error)
            categories.isEmpty() -> Message("Aucune catégorie")
            else -> Column(Modifier.fillMaxSize().padding(start = 28.dp, top = 20.dp)) {
                // En-tête : titre de l'écran puis le titre sélectionné
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Black, color = KingRed)
                    Spacer(Modifier.width(24.dp))
                    ButtonTile(Lang.t("🔎 Rechercher"), Modifier.width(220.dp), primary = false) { onSearch() }
                }
                Text(
                    focused?.name ?: " ",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp, end = 28.dp)
                )
                Text(focused?.info ?: " ", fontSize = 14.sp, color = Muted, modifier = Modifier.padding(bottom = 8.dp))

                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    contentPadding = PaddingValues(bottom = 40.dp)
                ) {
                    val all = listOf(Catalog.RECENT) + categories
                    items(all.size, key = { all[it].id }) { index ->
                        val category = all[index]
                        CatalogRow(
                            // La rangée sélectionnée remonte toute seule en haut de l'écran (comme Netflix)
                            onRowFocused = {
                                lastRow = index
                                rowScope.launch { listState.animateScrollToItem(index) }
                            },
                            category = category,
                            restoreItem = if (index == startRow) startItem else 0,
                            fallback = fallback,
                            claimFocus = {
                                if (focusTaken || !(startRow == 0 || index == startRow || fallback)) false
                                else { focusTaken = true; true }
                            },
                            onItemFocused = { lastItem = it },
                            topFocus = if (category == Catalog.RECENT) topFocus else null,
                            loadRow = loadRow,
                            describe = describe,
                            onFocus = { focused = it },
                            onItem = onItem,
                            onAll = { onAll(category) },
                            onLocked = { askPin = true }
                        )
                    }
                }
            }
        }
        if (askPin) UnlockOverlay { askPin = false }
    }
}

@Composable
private fun <T> CatalogRow(
    category: Category,
    claimFocus: () -> Boolean,
    restoreItem: Int,
    fallback: Boolean,
    onItemFocused: (Int) -> Unit,
    topFocus: FocusRequester?,
    onRowFocused: () -> Unit,
    loadRow: suspend (Category) -> List<T>,
    describe: (T) -> RowItem<T>,
    onFocus: (RowItem<T>) -> Unit,
    onItem: (T) -> Unit,
    onAll: () -> Unit,
    onLocked: () -> Unit
) {
    val locked = Prefs.isLocked(category.name)
    var rowItems by remember(category.id) { mutableStateOf<List<RowItem<T>>?>(null) }
    var failed by remember(category.id) { mutableStateOf(false) }
    val ownFocus = remember { FocusRequester() }
    val firstFocus = topFocus ?: ownFocus
    val restoreFocus = remember { FocusRequester() }
    val rowState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Chaque rangée se charge quand elle apparaît à l'écran (et reste en mémoire ensuite)
    LaunchedEffect(category.id, locked) {
        if (locked || rowItems != null) return@LaunchedEffect
        runCatching { withContext(Dispatchers.Default) { loadRow(category) } }
            .onSuccess { list -> rowItems = list.map(describe) }
            .onFailure { failed = true }
    }
    LaunchedEffect(rowItems != null, fallback) {
        if (rowItems?.isNotEmpty() == true && claimFocus()) {
            val target = if (restoreItem > 0 && restoreItem < (rowItems?.size ?: 0)) {
                runCatching { rowState.scrollToItem(restoreItem) }
                restoreFocus
            } else firstFocus
            // L'affiche doit d'abord être affichée : quelques essais rapprochés
            repeat(10) {
                kotlinx.coroutines.delay(80)
                if (runCatching { target.requestFocus() }.isSuccess) return@LaunchedEffect
            }
        }
    }

    var rowHasFocus by remember { mutableStateOf(false) }
    Column(Modifier.onFocusChanged { state ->
        if (state.hasFocus && !rowHasFocus) onRowFocused()
        rowHasFocus = state.hasFocus
    }) {
        Text(
            (if (Prefs.isProtected(category.name)) "🔒 " else "") + category.name,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        when {
            locked -> LazyRow(contentPadding = PaddingValues(end = 28.dp)) {
                item { WideTile("🔒  Catégorie verrouillée : OK pour la déverrouiller", onLocked) }
            }
            failed -> LazyRow(contentPadding = PaddingValues(end = 28.dp)) {
                item { WideTile("Impossible de charger : OK pour ouvrir la catégorie", onAll) }
            }
            rowItems == null -> Box(Modifier.fillMaxWidth().height(PosterWidth * 1.5f + 20.dp)) {
                Text("Chargement…", color = Muted, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterStart))
            }
            rowItems!!.isEmpty() -> Text("Aucun titre", color = Muted, fontSize = 13.sp)
            else -> LazyRow(
                state = rowState,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = 4.dp, end = 28.dp, top = 6.dp, bottom = 6.dp)
            ) {
                val list = rowItems!!
                items(list.size) { index ->
                    val entry = list[index]
                    Box(
                        Modifier.width(PosterWidth)
                            .then(
                                if (index == 0) Modifier.focusRequester(firstFocus)
                                else if (index == restoreItem) Modifier.focusRequester(restoreFocus)
                                else Modifier
                            )
                    ) {
                        PosterCard(entry.name, entry.image, onFocus = { onFocus(entry); onItemFocused(index) }) { onItem(entry.value) }
                    }
                }
                item {
                    Box(Modifier.width(PosterWidth)) {
                        FocusCard(onClick = onAll, shape = RoundedCornerShape(8.dp), focusedScale = 1.08f) {
                            Box(Modifier.width(PosterWidth).height(PosterWidth * 1.5f), contentAlignment = Alignment.Center) {
                                Text("Tout voir ›", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WideTile(text: String, onClick: () -> Unit) {
    FocusCard(onClick = onClick, shape = RoundedCornerShape(8.dp), focusedScale = 1.04f) {
        Box(Modifier.width(520.dp).height(64.dp), contentAlignment = Alignment.CenterStart) {
            Text(text, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 18.dp))
        }
    }
}

private fun stars(rating: Double): String? = if (rating > 0) "★ " + String.format("%.1f", rating / 2) else null

@Composable
fun MovieRowsScreen(nav: Navigator) = if (Session.api == null) XtreamOnly("les films") else CatalogRowsScreen(
    title = "FILMS",
    loadCategories = { Session.api!!.vodCategories() },
    cachedCategories = Session.vodCategories,
    storeCategories = { Session.vodCategories = it },
    loadRow = { category ->
        val api = Session.api!!
        val list = if (category == Catalog.RECENT) {
            val all = Session.allMovies ?: api.allMovies().also { Session.allMovies = it }
            val hidden = Session.vodCategories.filter { Prefs.isLocked(it.name) }.map { it.id }.toSet()
            Catalog.sort(all.filter { it.categoryId !in hidden }, CatalogSort.RECENT, { it.name }, { it.rating }, { it.added })
                .take(ROW_SIZE * 4)
        } else {
            Session.moviesByCategory[category.id] ?: api.movies(category.id).also { Session.moviesByCategory[category.id] = it }
        }
        // Une seule copie par film (la meilleure qualité)
        val kept = if (Prefs.groupSagas) Duplicates.keepBest(list, { it.name }, { it.year }) else list
        // Les derniers ajouts d'abord, dans chaque catégorie
        var row = Catalog.sort(kept, CatalogSort.RECENT, { it.name }, { it.rating }, { it.added }).take(ROW_SIZE)
        // Jaquettes cassées : cherchées en arrière-plan sur Internet (TMDB), la rangée s'affiche tout de suite
        Tmdb.prefetchMovies(row)
        // Jaquettes absentes : cherchées avant l'affichage
        Tmdb.enrichMovies(row.filter { it.icon.isNullOrBlank() }) { found ->
            val posters = found.associate { it.id to it.icon }
            row = row.map { m -> posters[m.id]?.let { m.copy(icon = it) } ?: m }
        }
        row
    },
    describe = { m ->
        RowItem(m, m.name, m.icon, listOfNotNull(m.year?.toString(), m.genre, stars(m.rating)).joinToString("  ·  ").ifEmpty { null })
    },
    onItem = { nav.go(Screen.MovieDetail(it)) },
    onAll = { nav.go(Screen.MovieGrid(it)) },
    onSearch = { Session.searchScope = "movies"; nav.go(Screen.Search) }
)

@Composable
fun SeriesRowsScreen(nav: Navigator) = if (Session.api == null) XtreamOnly("les séries") else CatalogRowsScreen(
    title = "SÉRIES",
    loadCategories = { Session.api!!.seriesCategories() },
    cachedCategories = Session.seriesCategories,
    storeCategories = { Session.seriesCategories = it },
    loadRow = { category ->
        val api = Session.api!!
        val list = if (category == Catalog.RECENT) {
            val all = Session.allSeries ?: api.allSeries().also { Session.allSeries = it }
            val hidden = Session.seriesCategories.filter { Prefs.isLocked(it.name) }.map { it.id }.toSet()
            Catalog.sort(all.filter { it.categoryId !in hidden }, CatalogSort.RECENT, { it.name }, { it.rating }, { it.added })
                .take(ROW_SIZE * 4)
        } else {
            Session.seriesByCategory[category.id] ?: api.series(category.id).also { Session.seriesByCategory[category.id] = it }
        }
        val kept = if (Prefs.groupSagas) Duplicates.keepBest(list, { it.name }) else list
        var row = Catalog.sort(kept, CatalogSort.RECENT, { it.name }, { it.rating }, { it.added }).take(ROW_SIZE)
        Tmdb.prefetchSeries(row)
        val missing = row.filter { it.cover.isNullOrBlank() }
        Tmdb.enrichSeries(missing) { found ->
            val covers = found.associate { it.id to it.cover }
            row = row.map { s -> covers[s.id]?.let { s.copy(cover = it) } ?: s }
        }
        row
    },
    describe = { s -> RowItem(s, s.name, s.cover, stars(s.rating)) },
    onItem = { nav.go(Screen.SeriesDetail(it)) },
    onAll = { nav.go(Screen.SeriesGrid(it)) },
    onSearch = { Session.searchScope = "series"; nav.go(Screen.Search) }
)
