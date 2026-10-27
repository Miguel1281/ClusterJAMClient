package com.clusterjam.mobile.features.auth.domain.model

enum class AppRole(val contractValue: String) {
    CLIENT("cliente"),
    ADMINISTRATOR("administrador");

    companion object {
        fun fromContract(value: String?): AppRole? = entries.find { it.contractValue == value }
    }
}
