package com.clusterjam.mobile.features.profile.data.remote.dto

data class AddressResponseDto(
    val id: String? = null,
    val label: String? = null,
    val recipient: String? = null,
    val street: String? = null,
    val exteriorNumber: String? = null,
    val interiorNumber: String? = null,
    val neighborhood: String? = null,
    val municipality: String? = null,
    val state: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
    val isDefault: Boolean? = null,
)

data class AddressListResponseDto(val items: List<AddressResponseDto?>? = null)
