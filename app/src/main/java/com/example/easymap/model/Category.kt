package com.example.easymap.model

import com.example.easymap.R

/** Categorias de ocorrências rodoviárias pedidas no enunciado. */
enum class Category(
    val label: String,
    val colorHex: String,
    val symbol: String,
    val bgHex: String,
    val iconBgHex: String,
    val iconResId: Int
) {
    ACCIDENT("Acidente", "#EF4444", "A", "#FFF0F0", "#EF4444", R.drawable.ic_cat_accident),
    ROADWORKS("Obras", "#F59E0B", "O", "#FFFBEB", "#F59E0B", R.drawable.ic_cat_roadworks),
    TRAFFIC("Trânsito", "#3B82F6", "T", "#EFF6FF", "#3B82F6", R.drawable.ic_cat_traffic),
    HAZARD("Perigo", "#EAB308", "P", "#FEF9C3", "#EAB308", R.drawable.ic_cat_hazard),
    ENFORCEMENT("Fiscalização", "#8B5CF6", "F", "#F3E8FF", "#8B5CF6", R.drawable.ic_cat_enforcement),
    ROAD_CLOSED("Estrada cortada", "#F43F5E", "E", "#FFE4E6", "#F43F5E", R.drawable.ic_cat_road_closed);

    companion object {
        fun fromName(name: String?): Category = entries.firstOrNull { it.name == name } ?: HAZARD
    }
}
