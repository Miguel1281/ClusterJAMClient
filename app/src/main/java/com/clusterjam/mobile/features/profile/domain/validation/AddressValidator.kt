package com.clusterjam.mobile.features.profile.domain.validation

import com.clusterjam.mobile.features.profile.domain.model.AddressFields
import java.util.Locale
import java.util.UUID

enum class AddressField(val limit: Int) {
    LABEL(40),
    RECIPIENT(120),
    STREET(160),
    EXTERIOR_NUMBER(20),
    INTERIOR_NUMBER(20),
    NEIGHBORHOOD(120),
    MUNICIPALITY(120),
    STATE(120),
    POSTAL_CODE(5),
    COUNTRY(2),
}

object AddressValidator {
    fun isAddressId(value: String): Boolean =
        try {
            UUID.fromString(value).toString().equals(value, ignoreCase = true)
        } catch (_: IllegalArgumentException) {
            false
        }

    fun normalize(value: AddressFields): AddressFields =
        value.copy(
            label = value.label.trim(),
            recipient = value.recipient.trim(),
            street = value.street.trim(),
            exteriorNumber = value.exteriorNumber.trim(),
            interiorNumber = value.interiorNumber?.trim()?.takeIf(String::isNotEmpty),
            neighborhood = value.neighborhood.trim(),
            municipality = value.municipality.trim(),
            state = value.state.trim(),
            postalCode = value.postalCode.trim(),
            country = value.country.trim().uppercase(Locale.ROOT),
        )

    fun invalidField(value: AddressFields): AddressField? {
        val values =
            listOf(
                value.label,
                value.recipient,
                value.street,
                value.exteriorNumber,
                value.interiorNumber,
                value.neighborhood,
                value.municipality,
                value.state,
                value.postalCode,
                value.country,
            )
        for ((index, field) in AddressField.values().withIndex()) {
            val text = values[index]
            if (field == AddressField.INTERIOR_NUMBER && text == null) continue
            if (
                text == null ||
                    text.isBlank() ||
                    text.codePointCount(0, text.length) > field.limit ||
                    text.any(Char::isISOControl)
            )
                return field
        }
        if (value.country.uppercase(Locale.ROOT) !in Locale.getISOCountries())
            return AddressField.COUNTRY
        return null
    }
}
