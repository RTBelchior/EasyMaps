package com.example.easymap.model

/** Categorias de ocorrências rodoviárias pedidas no enunciado. */
enum class Category(val label: String, val colorHex: String, val symbol: String) {
    ACCIDENT("Acidente", "#B91C1C", "A"),
    ROADWORKS("Obras", "#B45309", "O"),
    TRAFFIC("Trânsito", "#1D4ED8", "T"),
    HAZARD("Perigo", "#A16207", "P"),
    ENFORCEMENT("Fiscalização", "#6D28D9", "F"),
    ROAD_CLOSED("Estrada cortada", "#BE123C", "E");

    companion object {
        fun fromName(name: String?): Category = entries.firstOrNull { it.name == name } ?: HAZARD
    }
}
