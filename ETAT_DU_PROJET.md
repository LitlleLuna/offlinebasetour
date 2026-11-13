# État du projet — Étape 3 corrigée

## Modifications apportées

- [x] Ajout de la suppression des fichiers de région dans `MapManager` (méthode manquante qui empêchait la compilation).
- [x] Ajout de l'import `Context` manquant dans `PreparationScreen`.
- [x] Ajout des actions d'importation `.map` et `.osm.pbf` qui étaient déclarées mais jamais accessibles depuis l'interface.
- [x] Téléchargements configurés sans réseau mobile ni itinérance.
- [x] Suivi des téléchargements Android via `DownloadManager`.
- [x] Ajout de Bourgogne-Franche-Comté pour couvrir Belfort côté cartographie.
- [x] Le générateur GraphHopper ne choisit plus silencieusement le premier PBF lorsqu'il y en a plusieurs.
- [x] Localisation du suivi basée sur `LocationManager.GPS_PROVIDER`, sans dépendance aux données mobiles.
- [x] Filtrage GPS basé sur précision, temps écoulé et vitesse maximale plausible.
- [x] Persistance des points et de la distance dans Room.
- [x] Restauration d'un trajet actif lors du redémarrage du service.
- [x] Historique Room et export GPX 1.1 avec horodatages UTC.
- [x] Affichage de l’instruction associée au segment d’itinéraire le plus proche, avec estimation de la distance restante sur ce segment.
- [x] Configuration FileProvider pour partager les GPX.
- [x] Création du fichier `proguard-rules.pro` référencé par Gradle.

## Non validé dans cet environnement

- [ ] Compilation Debug.
- [ ] Compilation Release.
- [ ] Tests Android instrumentés.
- [ ] Test sur téléphone physique.
- [ ] Test GNSS écran éteint en mode avion.
- [ ] Test d'affichage avec de véritables archives Mapsforge.
- [ ] Test de routage avec un PBF fusionné couvrant Belfort–Bâle.
- [ ] Test de recalcul après déviation.
- [ ] Validation terrain de la progression des instructions de navigation.
- [ ] Validation d'un GPX exporté dans une application tierce.

## Bloqueur de compilation

Le ZIP reçu contenait `gradle-wrapper.jar` de 10 octets, donc invalide. Aucun Gradle, SDK Android ou accès réseau n'était disponible dans l'environnement de correction pour récupérer le vrai JAR officiel et exécuter une compilation. Le faux JAR a été retiré plutôt que présenté comme valide. Il faut régénérer le wrapper avec `gradle wrapper --gradle-version 8.2` sur une machine disposant de Gradle.

## Limites fonctionnelles restantes

- GraphHopper compile un seul PBF à la fois. Les PBF régionaux séparés ne sont pas fusionnés par l'application.
- Le guidage dynamique a reçu une première implémentation basée sur les géométries des instructions GraphHopper ; il doit encore être testé sur téléphone et ajusté sur des parcours réels.
- La recherche intégrée porte actuellement sur les POI fournis localement, pas sur toutes les adresses et rues d'une base OSM.
- Les cartes et graphes doivent être validés sur téléphone avant le voyage.


## Révision V5 — améliorations supplémentaires

- [x] Imports Android `Build` et `Toast` ajoutés dans l'écran Carte.
- [x] Calcul de l'écart à l'itinéraire basé sur les segments de la ligne.
- [x] Import des cartes/PBF atomique avec contrôle d'extension et d'en-tête.
- [x] Téléchargements conservés dans des fichiers temporaires jusqu'à la fin confirmée et au contrôle élémentaire du format.
- [x] Contrôle des en-têtes lors de la détection des fichiers Mapsforge et PBF.
- [x] Sauvegarde du point GPS et de la distance en transaction Room.
- [x] Filtrage GPS renforcé sur les coordonnées et la précision.
- [x] Nettoyage des fichiers temporaires lors de la suppression d'une région.
- [x] Échappement des caractères XML invalides dans les noms GPX.
- [ ] Compilation Android et tests sur téléphone toujours à effectuer (Gradle et SDK Android indisponibles ici).
