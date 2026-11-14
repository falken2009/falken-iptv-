package com.king365.fire

import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.json.JSONObject

// ---------- Transfert de l'abonnement vers l'iPhone ----------

/// L'abonnement en cours devient un lien « falken://abonnement?d=… » affiché en QR code.
/// L'appareil photo de l'iPhone le scanne, propose d'ouvrir Falken IPTV, qui enregistre l'abonnement.
object Transfert {
    fun lien(): String {
        val json = JSONObject()
            .put("m", Prefs.mode)
            .put("s", Prefs.server)
            .put("u", Prefs.username)
            .put("p", Prefs.password)
            .put("l", Prefs.m3uUrl)
            .toString()
        val data = Base64.encodeToString(json.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return "falken://abonnement?d=$data"
    }
}

@Composable
fun TransferScreen(onBack: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val matrix = remember {
        QRCodeWriter().encode(
            Transfert.lien(), BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0)
        )
    }
    KingBackground(dimmed = true) {
        Row(
            Modifier.align(Alignment.Center).padding(32.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(Modifier.size(360.dp).background(Color.White).padding(16.dp)) {
                val n = matrix.width
                val cell = size.minDimension / n
                for (x in 0 until n) for (y in 0 until matrix.height) {
                    if (matrix.get(x, y)) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
                }
            }
            Column(Modifier.width(420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ScreenTitle("Vers l'iPhone")
                Text("1. Ouvre l'appareil photo de l'iPhone et vise ce code.", fontSize = 16.sp)
                Text("2. Touche « Ouvrir dans Falken IPTV ».", fontSize = 16.sp)
                Text("3. L'abonnement est enregistré et la liste se charge.", fontSize = 16.sp)
                Text(
                    "Ce code contient ton identifiant et ton mot de passe : ne le montre à personne et ferme cet écran ensuite.",
                    fontSize = 13.sp, color = Muted
                )
                Spacer(Modifier.size(4.dp))
                ButtonTile("Terminé", Modifier.focusRequester(first)) { onBack() }
            }
        }
    }
}
