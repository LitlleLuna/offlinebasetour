# Corrections suite à la compilation `./gradlew assembleDebug`

## Erreur bloquante corrigée
- `mergeDebugJavaResource` : « 2 files found with path 'META-INF/NOTICE.md' » (jakarta.xml.bind-api / jakarta.activation-api).
  → `app/build.gradle.kts` : bloc `packaging.resources.excludes` (NOTICE*, LICENSE*, DEPENDENCIES, INDEX.LIST,
  AL2.0, LGPL2.1, META-INF/versions/**, module-info.class). Les `META-INF/services/**` restent fusionnés.
  La liste est volontairement large pour éviter qu'un 2e conflit identique apparaisse juste après.

## Avertissement corrigé
- `GpsTrackingService.kt:36` : `limitedParallelism` nécessite `@OptIn(ExperimentalCoroutinesApi::class)` (ajouté).

## Bugs fonctionnels corrigés
- URLs de téléchargement : `bourgogne-franche-comte` n'existe ni chez Geofabrik ni chez Mapsforge (anciennes
  régions conservées) → remplacé par `franche-comte` (région de Belfort). Taille Suisse corrigée (~520 Mo).
- Premier lancement : le GPS de l'écran Carte ne démarrait jamais (permission accordée après la composition).
  La permission est maintenant observée (`MainActivity` → `MapScreen`) et le GPS démarre dès son octroi.
- `mapView.destroyAll()` n'est plus exécuté lors d'un simple changement de permission (effet séparé).
- Calcul d'itinéraire : plus d'échec silencieux (message si position GPS inconnue ou graphe absent) ;
  un échec ne supprime plus l'itinéraire affiché.
- Préparation : si un seul PBF régional est présent il est utilisé pour générer le graphe
  (le PBF importé `custom.osm.pbf` garde la priorité ; plusieurs PBF → message explicite).
- Historique : impossible de supprimer le trajet en cours d'enregistrement.
- Service GPS : `lastAcceptedLocation` en `@Volatile` (accès depuis 2 threads).
- Manifest : `largeHeap="true"` pour l'import GraphHopper.

## Non vérifié ici (pas de Gradle/SDK/réseau dans l'environnement de correction)
- La compilation complète : à relancer chez vous (`./gradlew clean assembleDebug`).
- Le fonctionnement runtime de GraphHopper 4.0 sur téléphone (mémoire, import PBF).
