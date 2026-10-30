package com.clusterjam.mobile.features.profile.presentation.addresses

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.clusterjam.mobile.R
import com.clusterjam.mobile.core.ui.components.*
import com.clusterjam.mobile.core.ui.theme.JamSpacing
import com.clusterjam.mobile.features.auth.presentation.components.*
import com.clusterjam.mobile.features.profile.domain.model.CustomerAddress
import com.clusterjam.mobile.features.profile.domain.validation.AddressField

@Composable
fun AddressListScreen(
    state: AddressUiState,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onDefault: (String) -> Unit,
    onPrepareDelete: (String) -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onReload: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler { if (!state.isWriting) onBack() }
    val remaining = rememberRetrySeconds(state.retryAtMillis)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = JamSpacing.ContentWidth)
                .fillMaxSize()
                .padding(horizontal = JamSpacing.Page),
            verticalArrangement = Arrangement.spacedBy(JamSpacing.Large),
            contentPadding = PaddingValues(vertical = JamSpacing.Page),
        ) {
            item {
                if (state.error != null && state.notice != null) {
                    StatusMessage(state.notice.resolve(LocalContext.current), FeedbackTone.Success)
                }
                ScreenHeading(
                    stringResource(R.string.address_title),
                    stringResource(R.string.address_subtitle),
                )
            }
            item {
                AuthFeedback(
                    error = state.error,
                    notice = state.notice,
                    success = true,
                    remainingSeconds = remaining,
                )
                if (state.isBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                PrimaryButton(
                    stringResource(R.string.address_create),
                    onCreate,
                    enabled =
                        !state.isBusy && !state.needsRead && state.book != null && remaining == 0L,
                )
                TextButton(onClick = onReload, enabled = !state.isBusy && remaining == 0L) {
                    Text(stringResource(R.string.address_reload))
                }
                TextButton(onClick = onBack, enabled = !state.isWriting) {
                    Text(stringResource(R.string.profile_back))
                }
            }
            if (state.book?.items?.isEmpty() == true && !state.needsRead)
                item { Text(stringResource(R.string.address_empty)) }
            items(state.book?.items.orEmpty(), key = { it.id }) { address ->
                AddressCard(
                    address,
                    enabled = !state.isBusy && !state.needsRead && remaining == 0L,
                    onEdit = { onEdit(address.id) },
                    onDefault = { onDefault(address.id) },
                    onDelete = { onPrepareDelete(address.id) },
                )
            }
        }
    }
    state.pendingDelete?.address?.let { address ->
        AlertDialog(
            onDismissRequest = { if (!state.isWriting) onCancelDelete() },
            title = { Text(stringResource(R.string.address_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.address_delete_body,
                        address.fields.label,
                        address.fields.street,
                        address.fields.exteriorNumber,
                    ),
                    modifier =
                        Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmDelete, enabled = !state.isBusy) {
                    Text(stringResource(R.string.address_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelDelete, enabled = !state.isWriting) {
                    Text(stringResource(R.string.address_cancel))
                }
            },
        )
    }
}

@Composable
private fun AddressCard(
    address: CustomerAddress,
    enabled: Boolean,
    onEdit: () -> Unit,
    onDefault: () -> Unit,
    onDelete: () -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(JamSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(JamSpacing.Small),
        ) {
            val fields = address.fields
            Text(fields.label, style = MaterialTheme.typography.titleMedium)
            if (fields.isDefault)
                Text(
                    stringResource(R.string.address_default),
                    color = MaterialTheme.colorScheme.primary,
                )
            AddressSummary(address)
            TextButton(onClick = onEdit, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.address_edit))
            }
            TextButton(
                onClick = onDefault,
                enabled = enabled && !fields.isDefault,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.address_make_default))
            }
            TextButton(
                onClick = onDelete,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.address_delete))
            }
        }
    }
}

@Composable
private fun AddressSummary(address: CustomerAddress) {
    val f = address.fields
    Text(f.recipient)
    Text(listOfNotNull(f.street, f.exteriorNumber, f.interiorNumber).joinToString(" · "))
    Text(
        listOf(f.neighborhood, f.municipality, f.state, f.postalCode, f.country).joinToString(", "),
    )
}

@Composable
fun AddressFormScreen(
    state: AddressUiState,
    onChange: (AddressField, String) -> Unit,
    onDefaultChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onReload: () -> Unit,
    onKeepDraft: () -> Unit,
    onUseServer: () -> Unit,
    onBack: () -> Unit,
) {
    var discard by remember { mutableStateOf(false) }
    fun leave() {
        if (!state.isWriting) {
            if (state.dirty) discard = true else onBack()
        }
    }
    BackHandler { leave() }
    val remaining = rememberRetrySeconds(state.retryAtMillis)
    AuthForm(
        title =
            stringResource(
                if (state.stage == AddressStage.EDIT) R.string.address_edit
                else R.string.address_create,
            ),
        subtitle = stringResource(R.string.address_form_subtitle),
        feedbackKey = state.error ?: state.needsReview,
        feedback = {
            AuthFeedback(error = state.error, remainingSeconds = remaining)
            if (state.needsRead)
                TextButton(onClick = onReload, enabled = !state.isBusy && remaining == 0L) {
                    Text(stringResource(R.string.profile_reload))
                }
            if (state.needsReview) {
                Text(
                    stringResource(
                        if (state.stage == AddressStage.CREATE) R.string.address_review_create
                        else R.string.profile_review_required,
                    ),
                )
                if (state.stage == AddressStage.EDIT)
                    state.item?.address?.let { AddressSummary(it) }
                else
                    state.book?.items?.forEach {
                        Text(it.fields.label)
                        AddressSummary(it)
                    }
                TextButton(onClick = onKeepDraft, enabled = !state.isBusy) {
                    Text(stringResource(R.string.profile_keep_draft))
                }
                if (state.stage == AddressStage.EDIT)
                    TextButton(onClick = onUseServer, enabled = !state.isBusy) {
                        Text(stringResource(R.string.profile_use_server))
                    }
            }
        },
        action = {
            PrimaryButton(
                stringResource(
                    if (state.isWriting) R.string.profile_saving else R.string.address_save,
                ),
                onSave,
                enabled = state.canSave && remaining == 0L,
                loading = state.isWriting,
            )
            TextButton(onClick = ::leave, enabled = !state.isWriting) {
                Text(stringResource(R.string.address_back))
            }
        },
    ) {
        if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        for (field in AddressField.values()) AuthField(
            state.values[field].orEmpty(),
            { onChange(field, it) },
            stringResource(field.labelResource()),
            enabled = !state.isBusy,
            hint =
                if (field == AddressField.COUNTRY) stringResource(R.string.address_country_hint)
                else if (field == AddressField.INTERIOR_NUMBER)
                    stringResource(R.string.address_interior_hint)
                else null,
            error =
                if (state.invalidField == field)
                    stringResource(
                        R.string.address_field_invalid,
                        stringResource(field.labelResource()),
                        field.limit,
                    )
                else null,
            onDone =
                if (field == AddressField.COUNTRY)
                    ({ if (state.canSave && remaining == 0L) onSave() })
                else null,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(JamSpacing.Small),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.address_default))
                Text(
                    stringResource(R.string.address_default_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = state.isDefault,
                onCheckedChange = onDefaultChange,
                enabled = !state.isBusy,
            )
        }
    }
    if (discard)
        AlertDialog(
            onDismissRequest = { discard = false },
            title = { Text(stringResource(R.string.profile_discard_title)) },
            text = { Text(stringResource(R.string.address_discard_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        discard = false
                        onBack()
                    },
                ) {
                    Text(stringResource(R.string.profile_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { discard = false }) {
                    Text(stringResource(R.string.profile_keep_editing))
                }
            },
        )
}

@StringRes
private fun AddressField.labelResource(): Int =
    when (this) {
        AddressField.LABEL -> R.string.address_label
        AddressField.RECIPIENT -> R.string.address_recipient
        AddressField.STREET -> R.string.address_street
        AddressField.EXTERIOR_NUMBER -> R.string.address_exterior
        AddressField.INTERIOR_NUMBER -> R.string.address_interior
        AddressField.NEIGHBORHOOD -> R.string.address_neighborhood
        AddressField.MUNICIPALITY -> R.string.address_municipality
        AddressField.STATE -> R.string.address_state
        AddressField.POSTAL_CODE -> R.string.address_postal
        AddressField.COUNTRY -> R.string.address_country
    }
