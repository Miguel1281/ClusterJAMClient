package com.clusterjam.mobile.features.profile.domain.model

data class AddressFields(
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

data class CustomerAddress(val id: String, val fields: AddressFields)

data class AddressSnapshot(val address: CustomerAddress, val revision: String)

data class AddressBookSnapshot(val items: List<CustomerAddress>, val revision: String)
