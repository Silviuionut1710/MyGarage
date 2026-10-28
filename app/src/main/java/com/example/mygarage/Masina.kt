package com.example.mygarage

data class Masina(
    var id: String = "",
    val nrInmatriculare: String = "",
    val tipVehicul: String = "Auto",
    val dataExpirareRCA: String = "",
    val dataExpirareITP: String = "",
    val dataExpirareRovinieta: String = "",
    val dataExpirareTahograf: String = "",
    val dataExpirareCopiaConforma: String = "",
    val uriPdfRca: String? = null,
    val uriPdfCopia: String? = null,
    var ownerId: String = ""
)