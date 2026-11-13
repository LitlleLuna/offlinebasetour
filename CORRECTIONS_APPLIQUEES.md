# Corrections appliquées à l'Étape 3

## Fonctionnalités corrigées dans le code

1. **Préparation** : méthodes de suppression manquantes, import des fichiers `.map` et `.osm.pbf` désormais accessible, rafraîchissement de l'état des téléchargements.
2. **Téléchargements** : requêtes DownloadManager limitées au Wi-Fi (pas de réseau mobile ni d'itinérance).
3. **Couverture cartographique** : ajout de Bourgogne-Franche-Comté pour inclure Belfort.
4. **Routage** : choix explicite d'un PBF unique ou d'un PBF fusionné ; le code n'utilise plus arbitrairement le premier PBF trouvé.
5. **Chargement GraphHopper** : déplacement du chargement du graphe hors du thread principal et chargement paresseux avant calcul.
6. **GPS** : la carte et le service de suivi utilisent le fournisseur GPS natif Android ; le suivi ne dépend pas de Google Play Services ni d'un réseau de données.
7. **Suivi** : filtrage par précision, temps écoulé et vitesse plausible ; points et distance persistés dans Room ; reprise d'un trajet ouvert lors du redémarrage du service.
8. **Historique** : consultation des trajets et export GPX 1.1 partageable.
9. **Compilation** : ajout de `proguard-rules.pro`, correction des symboles/méthodes manquants identifiés.

## Limites non masquées

- Aucun build Android n'a pu être exécuté ici : Gradle et le SDK Android ne sont pas installés.
- Le wrapper binaire fourni était invalide ; il a été retiré et doit être régénéré sur une machine disposant de Gradle.
- La couverture routière continue Belfort–Bâle nécessite un PBF unique couvrant la zone. GraphHopper ne fusionne pas les extraits régionaux automatiquement.
- Les tests physiques GPS, écran éteint, mode avion et lecture des cartes avec des données réelles restent à faire.
