package com.clusterjam.mobile.features.profile.data.remote.dto

import com.google.gson.annotations.JsonAdapter

@JsonAdapter(AddressRequestJsonAdapter::class)
data class AddressRequestDto(
    val label: String,
    val recipient: String,
    val street: String,
    val exteriorNumber: String,
    val interiorNumber: String?,
    val neighborhood: String,
    val municipality: String,
    val state: String,
    val postalCode: String,
    val country: String,
    val isDefault: Boolean,
)

data class SetDefaultAddressRequestDto(val isDefault: Boolean = true)
