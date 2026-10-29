package com.example.easymap.model

/** Ocorrência reportada por um utilizador. [synced] indica se já foi enviada para o Firestore. */
data class Occurrence(
    val id: String,
    val userId: String,
    val category: Category,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val photoPath: String?,
    val synced: Boolean
)
