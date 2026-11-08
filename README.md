# Gestionnaire de fichiers — Android

Application Android native (Kotlin, Jetpack Compose, Material 3). Aucune permission Internet : rien ne quitte l'appareil.

## Fonctions

- **Navigation** : stockage interne, cartes SD et clés USB OTG ; fil d'Ariane cliquable ; retour arrière dossier par dossier ; position de défilement mémorisée.
- **Affichage** : liste ou grille, vignettes d'images et de vidéos, tri (nom naturel, taille, date, type ; croissant/décroissant ; dossiers en premier), fichiers cachés affichables.
- **Opérations** : multi-sélection, copier / couper / coller (avec progression en octets et annulation), renommer, supprimer, créer dossier / fichier, gestion des doublons (« nom (1).ext »).
- **Corbeille** : suppression réversible, restauration à l'emplacement d'origine, vidage.
- **Archives** : compression ZIP, extraction ZIP (protection zip-slip, noms non UTF-8).
- **Recherche** récursive par nom, résultats au fil de l'eau.
- **Catégories** (via MediaStore) : images, vidéos, audio, documents, archives, APK, gros fichiers (> 50 Mo), récents (30 jours).
- **Accueil** : espace utilisé par volume, raccourcis (Téléchargements, DCIM…), favoris, fichiers récents.
- **Visionneuse d'images** intégrée : balayage entre les images du dossier, zoom au pincement et double-tap.
- **Éditeur de texte** intégré (txt, md, json, code…) avec enregistrement atomique.
- **Partage**, **ouvrir avec…**, installation d'APK, **propriétés** (taille récursive, nombre de fichiers, droits).
- Thème clair / sombre / système, couleurs dynamiques (Android 12+).

## Compiler

Prérequis : JDK 17, Android SDK (plateforme 35).

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # APK minifié, signé avec la clé de debug
```

Chaque push déclenche GitHub Actions (`.github/workflows/android.yml`), qui publie les APK en artefact `fichiers-apk`.

## Autorisations

- Android 11+ : « Accès à tous les fichiers » (`MANAGE_EXTERNAL_STORAGE`), seul moyen pour un gestionnaire de fichiers de lire et écrire partout. Sur le Play Store, cette autorisation exige une déclaration (catégorie « gestionnaire de fichiers » acceptée).
- Android 8–10 : lecture/écriture du stockage.
- `REQUEST_INSTALL_PACKAGES` : installer des APK depuis l'application.

## Limites connues

- Pas de RAR/7z (ZIP uniquement), pas de FTP/SMB/cloud, pas d'accès root.
- `Android/data` et `Android/obb` restent inaccessibles sur Android 11+ (restriction système).
- L'APK release est signé avec la clé de debug : à remplacer avant toute publication.
