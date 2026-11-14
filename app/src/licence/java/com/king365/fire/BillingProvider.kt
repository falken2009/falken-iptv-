package com.king365.fire

import android.content.Context

/// Version vendue par abonnement, installée hors boutique : l'accès est ouvert par un code
/// d'activation saisi dans l'appli (voir Licence.kt et outils/generer-code.py)
object BillingProvider {
    fun create(): BillingBackend = object : BillingBackend {
        override fun start(context: Context, onPremium: (Boolean) -> Unit, onPrices: (Map<String, String>) -> Unit) {
            Licence.init(context)
            Licence.verifier(context)
            onPremium(Licence.actif)
        }

        /// Pas de paiement dans l'appli : le client paie en dehors, puis saisit son code
        override fun purchase(sku: String) {}
        override fun restore() {}
    }
}
