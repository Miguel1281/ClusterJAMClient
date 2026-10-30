package com.clusterjam.mobile.features.profile.data.mapper

import com.clusterjam.mobile.features.profile.data.remote.dto.AddressListResponseDto
import com.clusterjam.mobile.features.profile.data.remote.dto.AddressRequestDto
import com.clusterjam.mobile.features.profile.data.remote.dto.AddressResponseDto
import com.clusterjam.mobile.features.profile.domain.model.*
import com.clusterjam.mobile.features.profile.domain.validation.AddressValidator
import retrofit2.Response

internal fun AddressFields.toRequest() =
    AddressRequestDto(
        label,
        recipient,
        street,
        exteriorNumber,
        interiorNumber,
        neighborhood,
        municipality,
        state,
        postalCode,
        country,
        isDefault,
    )

internal fun AddressResponseDto.toAddress(): CustomerAddress =
    CustomerAddress(
        id?.takeIf(AddressValidator::isAddressId) ?: invalidAddressResponse(),
        AddressFields(
            required(label),
            required(recipient),
            required(street),
            required(exteriorNumber),
            interiorNumber,
            required(neighborhood),
            required(municipality),
            required(state),
            required(postalCode),
            required(country),
            isDefault ?: invalidAddressResponse(),
        ),
    )

internal fun Response<AddressResponseDto>.toAddressSnapshot(expectedStatus: Int): AddressSnapshot {
    if (code() != expectedStatus) invalidAddressResponse()
    return AddressSnapshot(body()?.toAddress() ?: invalidAddressResponse(), addressRevision())
}

internal fun Response<AddressListResponseDto>.toAddressBook(): AddressBookSnapshot {
    if (code() != 200) invalidAddressResponse()
    val items =
        body()?.items?.map { it?.toAddress() ?: invalidAddressResponse() }
            ?: invalidAddressResponse()
    if (
        items.map { it.id.lowercase(java.util.Locale.ROOT) }.distinct().size != items.size ||
            items.count { it.fields.isDefault } > 1
    )
        invalidAddressResponse()
    return AddressBookSnapshot(items, addressRevision())
}

private fun Response<*>.addressRevision(): String =
    headers()["ETag"]?.takeIf(::isProfileRevision) ?: invalidAddressResponse()

private fun required(value: String?): String =
    value?.takeIf(String::isNotBlank) ?: invalidAddressResponse()

private fun invalidAddressResponse(): Nothing = throw ProfileFailure("INVALID_RESPONSE")
