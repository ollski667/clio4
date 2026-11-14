# Clio Home

Écran d'accueil (launcher) léger pour l'autoradio Android **AWESAFE PX9** d'une Clio 4 (2016, 1.5 dCi) :
CarPlay via ZLink, suivi d'entretien et du kilométrage, stationnement, prix du carburant, météo.

## Matériel visé

| | |
|---|---|
| Processeur | MediaTek MT8163 (4 × Cortex-A53, 1,3 GHz) |
| Mémoire | 2 Go |
| Écran | 1280 × 720 |
| Android annoncé | 13 (l'appli marche dès Android 7) |
| Boîtier CAN | Raise, profil Renault |

## Choix techniques

- Kotlin, composants Android standard, **aucune bibliothèque** : APK d'environ 100 Ko.
- Interface construite en code, sans animations, pour rester fluide sur un petit processeur.
- Tout ce qui peut marcher **hors ligne** marche hors ligne : heure, vitesse GPS, suivi, stationnement.
  La météo (Open-Meteo) et les prix du carburant (données officielles prix-carburants) gardent la dernière valeur connue.
- Taille d'interface réglable (« Grande » par défaut) et mise en page qui s'adapte à la taille de l'écran.

## Installer l'APK sur l'autoradio

1. Téléchargez `ClioHome.apk` depuis la page **Releases** du dépôt (version « derniere-version »).
2. Copiez-le sur une clé USB, branchez-la sur l'autoradio.
3. Ouvrez le fichier avec le gestionnaire de fichiers de l'autoradio et installez-le
   (autorisez l'installation d'applis inconnues si Android le demande).
4. Appuyez sur le bouton Accueil : choisissez **Clio Home**, puis **Toujours**.

Pour revenir à l'écran d'origine : Applis → Réglages → Système → « Changer l'écran d'accueil par défaut ».

## Compiler soi-même

Chaque modification poussée sur `main` est compilée automatiquement par GitHub (onglet **Actions**).
En local : `gradle assembleRelease` avec le SDK Android (compileSdk 34).

La clé de signature `app/cliohome.keystore` est volontairement dans le dépôt : elle permet d'installer
les nouvelles versions par-dessus l'ancienne sans perdre les données. Elle ne protège rien d'autre.

## Publier une nouvelle version

1. Augmenter `versionCode` et `versionName` dans `app/build.gradle.kts` (et `BuildInfo.version`).
2. Ajouter une entrée en tête de `Notes.RELEASES` (`app/src/main/java/fr/cliohome/Notes.kt`) :
   nouveautés et correctifs, affichés une fois après la mise à jour.
3. Pousser sur `main` : GitHub construit l'APK et publie `ClioHome.apk` + `version.json`,
   puis l'appli propose la mise à jour par Internet.

## À faire

- Lire la température extérieure du boîtier CAN Raise (à tester sur la voiture).
- Utiliser le signal des phares pour le mode nuit, si l'autoradio le transmet.
