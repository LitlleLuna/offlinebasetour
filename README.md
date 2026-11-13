# Basel Tour Offline — V5 (préparation corrigée)

Application Android Kotlin / Jetpack Compose destinée à la consultation de cartes Mapsforge, au calcul d'itinéraires GraphHopper et à l'enregistrement GPS local.

## Corrections intégrées dans cette révision V5

- Les imports de cartes et PBF sont copiés de manière atomique et leur en-tête est contrôlé avant validation.
- Les téléchargements sont d’abord placés dans un fichier temporaire et ne sont publiés qu’après confirmation de leur réussite.
- La sauvegarde d’un point GPS et de la distance associée est transactionnelle dans Room.
- Le calcul de l’écart à la route se fait sur les segments de la polyligne, et le filtrage GNSS rejette les coordonnées ou précisions invalides.
- Le suivi GPS utilise désormais `LocationManager.GPS_PROVIDER` (GNSS du téléphone) : la collecte des positions n'a pas besoin de réseau mobile.
- Le service de suivi sauvegarde les points dans Room, filtre les positions imprécises et les vitesses aberrantes, et restaure un trajet resté ouvert après un redémarrage du service.
- Les téléchargements via Android `DownloadManager` sont configurés pour ne pas utiliser de réseau mobile ni d'itinérance.
- La préparation permet d'importer une carte Mapsforge `.map` et un fichier routier `.osm.pbf`.
- La liste inclut maintenant Bourgogne-Franche-Comté, nécessaire pour la couverture de Belfort.
- Le gestionnaire de cartes supprime les fichiers d'une région et ignore les archives manifestement vides.
- L'écran Historique affiche les trajets Room et permet de partager un export GPX 1.1.
- Le fournisseur FileProvider est configuré pour les exports GPX.
- Les autorisations de localisation en arrière-plan ne sont plus demandées automatiquement au tout premier lancement.

## Limites importantes à connaître

1. **Le Gradle Wrapper livré dans le ZIP d'origine était un fichier invalide de 10 octets.** L'environnement de correction ne dispose ni de Gradle, ni du SDK Android, ni d'un accès réseau permettant de récupérer le JAR officiel. Le faux JAR a été retiré. Les scripts `gradlew` indiquent comment utiliser Gradle installé ou régénérer le wrapper. Le projet n'a donc pas pu être compilé ici.
2. GraphHopper importe un seul PBF pour créer un graphe. Les PBF Bourgogne-Franche-Comté, Alsace et Suisse téléchargés séparément ne sont **pas fusionnés automatiquement**. Pour calculer un trajet continu Belfort–Bâle, importer un PBF unique couvrant toute la zone ou fusionné au préalable sur ordinateur avec un outil adapté.
3. Le routage ne devient disponible qu'après génération réussie du graphe sur le téléphone. Les performances et la mémoire dépendent fortement du modèle de téléphone et de la taille du PBF.
4. Le guidage étape par étape dynamique, le suivi de progression précis sur chaque instruction et la validation du recalcul de déviation nécessitent encore des tests et du développement.
5. Les téléchargements et la lecture de cartes doivent être vérifiés avec de vrais fichiers `.map` Mapsforge et des PBF valides. La présence d'un fichier ne garantit pas à elle seule que sa couverture est correcte.
6. Le suivi GNSS doit être testé sur un téléphone réel, écran éteint et mode avion activé (GPS laissé activé). Aucun test matériel n'a été effectué dans cet environnement.

## Compilation

Prérequis : Android Studio récent, JDK 17, Android SDK 34 et Gradle 8.2.

Si Gradle est installé sur la machine :

```bash
gradle wrapper --gradle-version 8.2
./gradlew assembleDebug
./gradlew test
```

Sous Windows :

```bat
gradle wrapper --gradle-version 8.2
gradlew.bat assembleDebug
gradlew.bat test
```

## Préparer les données

- Télécharger les cartes Mapsforge `.map` pour Bourgogne-Franche-Comté, Alsace et Suisse.
- Télécharger un PBF couvrant l'ensemble de la zone Belfort–Bâle ou importer un PBF fusionné.
- Pour un graphe continu, fusionner sur ordinateur les extraits Geofabrik avec Osmium, par exemple :

```bash
osmium merge bourgogne-franche-comte-latest.osm.pbf alsace-latest.osm.pbf switzerland-latest.osm.pbf -o belfort-bale.osm.pbf
```

- Importer `belfort-bale.osm.pbf` dans l'application avec le bouton **Importer PBF** (il sera enregistré comme `custom.osm.pbf`).
- Générer le graphe GraphHopper dans l'écran Préparation.
- Vérifier que les cartes et le graphe couvrent réellement la zone du voyage avant de partir.

Les données cartographiques OpenStreetMap et les fichiers dérivés doivent conserver leurs attributions et licences respectives.
