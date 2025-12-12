package com.example.mqtt1

data class ClienteGas(
    val email: String = "",
    val nivelGas: Int = 0, // Porcentaje de 0 a 100
    val rol: String = "user", // "user" o "distribuidor"
    val empresa: String = "",
    val direccion: String = ""
)