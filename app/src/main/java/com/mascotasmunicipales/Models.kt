package com.mascotasmunicipales

import com.google.firebase.Timestamp

// Las fotos son recursos incluidos en la app; Firestore solo guarda esta clave.
data class Pet(
    val id: String = "",
    val ownerId: String = "",
    val name: String = "",
    val species: String = "",
    val breed: String = "",
    val sex: String = "",
    val age: String = "",
    val color: String = "",
    val territoryId: String = "",
    val status: String = "Con responsable",
    val photoKey: String = "",
    val qrCode: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val pending: Boolean = false
)

data class Report(
    val id: String = "",
    val ownerId: String = "",
    val petId: String = "",
    val petName: String = "",
    val type: String = "",
    val species: String = "",
    val territoryId: String = "",
    val description: String = "",
    val status: String = "Abierto",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val pending: Boolean = false
)

data class Territory(val id: String, val label: String)

val TERRITORIES = listOf(
    Territory("comuna-1", "Comuna 1 · Centro Histórico"),
    Territory("comuna-2", "Comuna 2 · Nororiental"),
    Territory("comuna-3", "Comuna 3 · Suroriental"),
    Territory("comuna-4", "Comuna 4 · Occidental")
)

fun territoryLabel(id: String): String = TERRITORIES.firstOrNull { it.id == id }?.label ?: id

fun photoResource(key: String): Int = when (key) {
    "luna" -> R.drawable.luna
    "michi" -> R.drawable.michi
    "rocco" -> R.drawable.rocco
    "nina" -> R.drawable.nina
    else -> 0
}
