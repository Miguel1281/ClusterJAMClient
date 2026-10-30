package com.clusterjam.mobile.features.profile.presentation.addresses

import com.clusterjam.mobile.features.auth.presentation.AuthMessage
import com.clusterjam.mobile.features.profile.domain.model.*
import com.clusterjam.mobile.features.profile.domain.validation.AddressField

enum class AddressStage {
    LIST,
    CREATE,
    EDIT,
}

data class AddressUiState(
    val stage: AddressStage = AddressStage.LIST,
    val addressId: String? = null,
    val values: Map<AddressField, String> = emptyMap(),
    val isDefault: Boolean = false,
    val book: AddressBookSnapshot? = null,
    val item: AddressSnapshot? = null,
    val pendingDelete: AddressSnapshot? = null,
    val isLoading: Boolean = false,
    val isWriting: Boolean = false,
    val dirty: Boolean = false,
    val needsRead: Boolean = true,
    val needsReview: Boolean = false,
    val error: AuthMessage? = null,
    val notice: AuthMessage? = null,
    val invalidField: AddressField? = null,
    val retryAtMillis: Long = 0,
) {
    val isBusy
        get() = isLoading || isWriting

    val canSave
        get() =
            stage != AddressStage.LIST &&
                !isBusy &&
                !needsRead &&
                !needsReview &&
                (if (stage == AddressStage.CREATE) book != null else item != null)

    fun fields() =
        AddressFields(
            values[AddressField.LABEL].orEmpty(),
            values[AddressField.RECIPIENT].orEmpty(),
            values[AddressField.STREET].orEmpty(),
            values[AddressField.EXTERIOR_NUMBER].orEmpty(),
            values[AddressField.INTERIOR_NUMBER]?.takeIf(String::isNotBlank),
            values[AddressField.NEIGHBORHOOD].orEmpty(),
            values[AddressField.MUNICIPALITY].orEmpty(),
            values[AddressField.STATE].orEmpty(),
            values[AddressField.POSTAL_CODE].orEmpty(),
            values[AddressField.COUNTRY].orEmpty(),
            isDefault,
        )
}

internal fun AddressFields.toFormValues(): Map<AddressField, String> =
    AddressField.values()
        .zip(
            listOf(
                label,
                recipient,
                street,
                exteriorNumber,
                interiorNumber.orEmpty(),
                neighborhood,
                municipality,
                state,
                postalCode,
                country,
            ),
        )
        .toMap()
