package com.example.easymap.util

import org.osmdroid.tileprovider.tilesource.XYTileSource

/**
 * Fonte dos mosaicos do mapa: OpenStreetMap França (dados do OpenStreetMap, gratuito e sem chave de API).
 * Está num só sítio para ser fácil trocar de fornecedor se for preciso.
 */
object MapTiles {
    val source = XYTileSource(
        "OsmFrance", 0, 19, 256, ".png",
        arrayOf(
            "https://a.tile.openstreetmap.fr/osmfr/",
            "https://b.tile.openstreetmap.fr/osmfr/",
            "https://c.tile.openstreetmap.fr/osmfr/"
        ),
        "© OpenStreetMap contributors"
    )
}
