package com.king365.fire

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay

// ---------- Retrouver sa place en revenant sur un écran ----------

/// Quand on ouvre un film puis qu'on revient, l'écran est reconstruit : le focus repartait sur la
/// première case. Cette mémoire garde le numéro de la dernière case sélectionnée (elle survit
/// au changement d'écran) pour la retrouver au retour.
@Stable
class FocusMemory(private val saved: MutableState<Int>, val first: FocusRequester) {
    /// Case qui avait le focus la dernière fois, lue une seule fois à l'arrivée sur l'écran
    val startIndex: Int = saved.value
    private val restore = FocusRequester()

    var index: Int
        get() = saved.value
        set(value) { saved.value = value }

    /// À poser sur chaque case : la première garde le rôle du bouton Retour (remonter en haut),
    /// et la case retrouvée reçoit le focus au retour
    fun modifier(i: Int): Modifier = when {
        i == 0 -> Modifier.focusRequester(first)
        i == startIndex -> Modifier.focusRequester(restore)
        else -> Modifier
    }

    /// Donne le focus à la case retrouvée (après quelques essais : elle doit d'abord s'afficher)
    suspend fun requestStart() {
        val target = if (startIndex == 0) first else restore
        repeat(12) {
            if (runCatching { target.requestFocus() }.isSuccess) return
            delay(60)
        }
    }
}

@Composable
fun rememberFocusMemory(first: FocusRequester): FocusMemory {
    val saved = rememberSaveable { mutableStateOf(0) }
    return remember { FocusMemory(saved, first) }
}
