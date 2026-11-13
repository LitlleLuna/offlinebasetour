# Révision V5 — corrections de fiabilité

## Changements intégrés

- Ajout des imports Android manquants `Build` et `Toast` dans l'écran Carte.
- Distance à l'itinéraire calculée par rapport aux segments de la polyligne, et non uniquement aux sommets : le déclenchement du recalcul est moins sensible à l'espacement des points.
- Import `.map` / `.osm.pbf` copié dans un fichier temporaire, contrôlé, puis publié par renommage ; le fichier précédent est conservé si la copie échoue.
- Contrôle de l'extension et de l'en-tête Mapsforge / OSM-PBF avant acceptation d'un import.
- Téléchargements enregistrés sous un nom temporaire et rendus visibles à l'application seulement après confirmation de succès de DownloadManager et contrôle élémentaire du format.
- Contrôle des en-têtes lors de l'affichage des cartes et de la sélection des PBF.
- Sauvegarde du point GPS et de la distance du trajet dans une transaction Room.
- Filtrage GPS renforcé : coordonnées valides, précision présente, finie et strictement positive, précision maximale de 35 m.
- Nettoyage des fichiers temporaires lors de la suppression d'une région.
- Échappement GPX renforcé pour supprimer les caractères de contrôle XML invalides.
- Version de l'application portée à `3.2-V5-PREPARATION` (versionCode 10).

## Vérifications effectuées ici

- Les fichiers XML du projet sont analysables.
- Les sources Kotlin ne présentent pas d'erreur de syntaxe détectée par le compilateur Kotlin autonome.
- Les scripts shell sont vérifiés syntaxiquement.
- L'archive ZIP finale est contrôlée après création.

## Ce qui reste non vérifié

- La compilation Android complète (AGP, SDK Android et dépendances non disponibles dans l'environnement de travail).
- Les tests sur appareil : GNSS écran éteint, mode avion, autorisations refusées, redémarrage du service, import de vraies cartes, calcul d'itinéraire et recalcul sur route.
- La compatibilité exacte des extraits Mapsforge/PBF téléchargés avec les versions de bibliothèques intégrées.
- La mémoire nécessaire à l'import GraphHopper du PBF Belfort–Bâle sur le téléphone cible.

**Cette V5 reste une révision de code source préparée pour compilation, pas un APK compilé ni une version validée sur téléphone.**
