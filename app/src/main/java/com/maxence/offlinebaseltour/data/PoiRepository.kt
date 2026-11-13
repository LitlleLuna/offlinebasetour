package com.maxence.offlinebaseltour.data

import org.mapsforge.core.model.LatLong

data class TouristSpot(val name: String, val description: String, val location: LatLong, val category: String)

object PoiRepository {
    val spots = listOf(
        TouristSpot("Basler Münster", "Cathédrale emblématique en grès rouge avec vue sur le Rhin.", LatLong(47.5563, 7.5925), "Monument"),
        TouristSpot("Rathaus", "Hôtel de ville rouge datant de plus de 500 ans, au centre de Bâle.", LatLong(47.5581, 7.5878), "Monument"),
        TouristSpot("Mittlere Brücke", "L'un des plus anciens passages sur le Rhin.", LatLong(47.5604, 7.5896), "Pont"),
        TouristSpot("Spalentor", "Ancienne porte de la ville, vestige des remparts médiévaux.", LatLong(47.5580, 7.5815), "Histoire"),
        TouristSpot("Fontaine Tinguely", "Fontaine mécanique avec sculptures en mouvement.", LatLong(47.5539, 7.5900), "Art"),
        TouristSpot("Vieille ville de Bâle", "Ruelles historiques et bâtiments préservés.", LatLong(47.5560, 7.5880), "Quartier"),
        TouristSpot("Dreiländereck", "Point de rencontre exact entre la France, la Suisse et l'Allemagne.", LatLong(47.5878, 7.5894), "Lieu d'intérêt"),
        TouristSpot("Quais du Rhin", "Promenade aménagée le long du fleuve, très prisée en été.", LatLong(47.5595, 7.5932), "Promenade"),
        TouristSpot("Citadelle de Belfort", "Forteresse de Vauban avec le célèbre Lion de Bartholdi.", LatLong(47.6366, 6.8653), "Monument")
    )
    
    fun search(query: String): List<TouristSpot> {
        val lowerQuery = query.lowercase()
        return spots.filter { 
            it.name.lowercase().contains(lowerQuery) || 
            it.category.lowercase().contains(lowerQuery) 
        }
    }
}
