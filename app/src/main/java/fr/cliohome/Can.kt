package fr.cliohome

/**
 * Infos du boîtier CAN (Raise, profil Renault) : température extérieure, etc.
 * L'autoradio ne documente pas comment les autres applis peuvent les lire.
 * Tant que ce n'est pas branché, la valeur reste vide et l'accueil affiche la météo à la place.
 */
object Can {
    @Volatile var outsideTemp: Int? = null
}
