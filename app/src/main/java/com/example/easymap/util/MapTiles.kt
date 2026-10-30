package com.example.easymap.util

import org.osmdroid.tileprovider.tilesource.XYTileSource

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