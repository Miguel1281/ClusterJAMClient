package com.clusterjam.mobile.features.profile.data.remote.dto

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

/**
 * Only this request must serialize interiorNumber=null; Profile PATCH still omits absent fields.
 */
class AddressRequestJsonAdapter : TypeAdapter<AddressRequestDto>() {
    override fun write(out: JsonWriter, value: AddressRequestDto?) {
        if (value == null) {
            out.nullValue()
            return
        }
        val previous = out.serializeNulls
        out.serializeNulls = true
        try {
            out.beginObject()
            for ((name, text) in
                listOf(
                    "label" to value.label,
                    "recipient" to value.recipient,
                    "street" to value.street,
                    "exteriorNumber" to value.exteriorNumber,
                    "interiorNumber" to value.interiorNumber,
                    "neighborhood" to value.neighborhood,
                    "municipality" to value.municipality,
                    "state" to value.state,
                    "postalCode" to value.postalCode,
                    "country" to value.country,
                )) out.name(name).value(text)
            out.name("isDefault").value(value.isDefault)
            out.endObject()
        } finally {
            out.serializeNulls = previous
        }
    }

    override fun read(input: JsonReader): AddressRequestDto =
        throw UnsupportedOperationException("Address request serialization is write-only.")
}
