package com.clusterjam.mobile.features.profile.presentation.addresses

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clusterjam.mobile.R
import com.clusterjam.mobile.features.auth.domain.model.SessionState
import com.clusterjam.mobile.features.auth.domain.repository.AuthRepository
import com.clusterjam.mobile.features.auth.presentation.AuthMessage
import com.clusterjam.mobile.features.profile.domain.model.ProfileFailure
import com.clusterjam.mobile.features.profile.domain.repository.ProfileRepository
import com.clusterjam.mobile.features.profile.domain.validation.AddressField
import com.clusterjam.mobile.features.profile.domain.validation.AddressValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AddressViewModel(
    private val repository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private var ownerId: String? = savedState["address.owner"]
    private var uncertain: Boolean = savedState["address.uncertain"] ?: false
    private var visible = false
    private var operationId = 0L
    private var job: Job? = null
    private val _uiState =
        MutableStateFlow(
            AddressUiState(
                stage =
                    AddressStage.values().find {
                        it.name == savedState.get<String>("address.stage")
                    } ?: AddressStage.LIST,
                addressId = savedState["address.id"],
                values =
                    AddressField.values().associateWith {
                        savedState.get<String>("address.field.${it.name}").orEmpty()
                    },
                isDefault = savedState["address.default"] ?: false,
                dirty = savedState["address.dirty"] ?: false,
            ),
        )
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.sessionState.collect { session ->
                when (session) {
                    is SessionState.Authenticated -> {
                        if (ownerId != null && ownerId != session.identity.userId) clear()
                        ownerId = session.identity.userId
                        savedState["address.owner"] = ownerId
                        if (visible && _uiState.value.needsRead) reload()
                    }
                    SessionState.Unauthenticated,
                    is SessionState.AccessDenied -> clear()
                    else -> suspendWork()
                }
            }
        }
    }

    fun onVisible() {
        if (!visible) {
            visible = true
            if (_uiState.value.needsRead) reload()
        }
    }

    fun onHidden() {
        visible = false
        suspendWork()
    }

    fun beginCreate() = begin(AddressStage.CREATE, null)

    fun beginEdit(id: String) {
        if (AddressValidator.isAddressId(id)) begin(AddressStage.EDIT, id)
    }

    private fun begin(stage: AddressStage, id: String?) {
        if (_uiState.value.isBusy) return
        _uiState.value = AddressUiState(stage = stage, addressId = id)
        uncertain = false
        saveDraft()
        if (visible) reload()
    }

    fun change(field: AddressField, text: String) {
        if (_uiState.value.isBusy || _uiState.value.stage == AddressStage.LIST) return
        _uiState.update {
            it.copy(
                values = it.values + (field to text),
                dirty = true,
                error = null,
                invalidField = null,
            )
        }
        saveDraft()
    }

    fun changeDefault(value: Boolean) {
        if (_uiState.value.isBusy || _uiState.value.stage == AddressStage.LIST) return
        _uiState.update { it.copy(isDefault = value, dirty = true) }
        saveDraft()
    }

    fun reload() =
        run(false) { operation, owner ->
            val old = _uiState.value
            if (old.stage == AddressStage.EDIT) {
                val id = old.addressId ?: throw ProfileFailure("ADDRESS_NOT_FOUND", status = 404)
                val fresh = repository.getMyAddress(id).getOrThrow()
                checkCurrent(operation, owner)
                _uiState.update {
                    it.copy(
                        item = fresh,
                        needsRead = false,
                        values = if (old.dirty) old.values else fresh.address.fields.toFormValues(),
                        isDefault =
                            if (old.dirty) old.isDefault else fresh.address.fields.isDefault,
                        needsReview =
                            (old.dirty || uncertain) &&
                                (uncertain || old.item?.revision != fresh.revision),
                    )
                }
            } else {
                val fresh = repository.getMyAddresses().getOrThrow()
                checkCurrent(operation, owner)
                _uiState.update {
                    it.copy(
                        book = fresh,
                        needsRead = false,
                        pendingDelete = null,
                        needsReview =
                            old.stage == AddressStage.CREATE &&
                                (old.dirty || uncertain) &&
                                (uncertain || old.book?.revision != fresh.revision),
                    )
                }
            }
            saveDraft()
        }

    fun keepDraft() {
        if (_uiState.value.isBusy || _uiState.value.needsRead) return
        uncertain = false
        _uiState.update { it.copy(needsReview = false, error = null) }
        saveDraft()
    }

    fun useServerValues() {
        val state = _uiState.value
        val fields = state.item?.address?.fields ?: return
        if (state.isBusy || state.needsRead) return
        uncertain = false
        _uiState.update {
            it.copy(
                values = fields.toFormValues(),
                isDefault = fields.isDefault,
                dirty = false,
                needsReview = false,
                error = null,
                invalidField = null,
            )
        }
        saveDraft()
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        val fields = AddressValidator.normalize(state.fields())
        val invalid = AddressValidator.invalidField(fields)
        if (invalid != null) {
            _uiState.update { it.copy(invalidField = invalid) }
            return
        }
        run(true) { operation, owner ->
            markWritePending()
            if (state.stage == AddressStage.CREATE)
                repository.createMyAddress(fields, requireNotNull(state.book).revision).getOrThrow()
            else
                repository
                    .updateMyAddress(
                        requireNotNull(state.addressId),
                        fields,
                        requireNotNull(state.item).revision,
                    )
                    .getOrThrow()
            checkCurrent(operation, owner)
            confirmed(R.string.address_saved, operation, owner)
        }
    }

    fun makeDefault(id: String) =
        run(true) { operation, owner ->
            val item = repository.getMyAddress(id).getOrThrow()
            checkCurrent(operation, owner)
            markWritePending()
            repository.setDefaultAddress(id, item.revision).getOrThrow()
            checkCurrent(operation, owner)
            confirmed(R.string.address_default_saved, operation, owner)
        }

    fun prepareDelete(id: String) =
        run(false) { operation, owner ->
            val item = repository.getMyAddress(id).getOrThrow()
            checkCurrent(operation, owner)
            _uiState.update { it.copy(pendingDelete = item) }
        }

    fun cancelDelete() {
        if (!_uiState.value.isWriting) _uiState.update { it.copy(pendingDelete = null) }
    }

    fun deleteConfirmed() {
        val item = _uiState.value.pendingDelete ?: return
        run(true) { operation, owner ->
            markWritePending()
            repository.deleteMyAddress(item.address.id, item.revision).getOrThrow()
            checkCurrent(operation, owner)
            confirmed(R.string.address_deleted, operation, owner)
        }
    }

    fun returnToList() {
        if (_uiState.value.isWriting) return
        suspendWork()
        uncertain = false
        _uiState.value = AddressUiState()
        saveDraft()
        if (visible) reload()
    }

    private suspend fun confirmed(message: Int, operation: Long, owner: String) {
        uncertain = false
        _uiState.value = AddressUiState(isWriting = true, notice = AuthMessage(message))
        saveDraft()
        val book = repository.getMyAddresses().getOrThrow()
        checkCurrent(operation, owner)
        _uiState.update { it.copy(book = book, needsRead = false) }
    }

    private fun run(writing: Boolean, block: suspend (Long, String) -> Unit) {
        val owner = currentOwner() ?: return
        val state = _uiState.value
        if (
            !visible ||
                ownerId != owner ||
                state.isBusy ||
                System.currentTimeMillis() < state.retryAtMillis
        )
            return
        val operation = ++operationId
        _uiState.update {
            it.copy(isLoading = !writing, isWriting = writing, error = null, notice = null)
        }
        job = viewModelScope.launch {
            try {
                block(operation, owner)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrent(operation, owner)) {
                    val failure = error as? ProfileFailure
                    val status = failure?.status
                    if (uncertain && status != null && status in 400..499) uncertain = false
                    val now = System.currentTimeMillis()
                    val wait = failure?.retryAfterMillis?.coerceIn(0, Long.MAX_VALUE - now) ?: 0L
                    _uiState.update {
                        it.copy(
                            error = AddressMessageMapper.failure(error),
                            needsRead = true,
                            pendingDelete = null,
                            retryAtMillis = now + wait,
                        )
                    }
                    saveDraft()
                }
            } finally {
                if (operation == operationId)
                    _uiState.update { it.copy(isLoading = false, isWriting = false) }
            }
        }
    }

    private fun markWritePending() {
        uncertain = true
        savedState["address.uncertain"] = true
    }

    private fun currentOwner() =
        (authRepository.sessionState.value as? SessionState.Authenticated)?.identity?.userId

    private fun isCurrent(operation: Long, owner: String) =
        visible && operation == operationId && ownerId == owner && currentOwner() == owner

    private fun checkCurrent(operation: Long, owner: String) {
        if (!isCurrent(operation, owner)) throw CancellationException("Session changed")
    }

    private fun suspendWork() {
        operationId++
        job?.cancel()
        job = null
        _uiState.update {
            it.copy(isLoading = false, isWriting = false, pendingDelete = null, needsRead = true)
        }
    }

    private fun clear() {
        suspendWork()
        ownerId = null
        uncertain = false
        _uiState.value = AddressUiState()
        for (key in listOf("owner", "stage", "id", "default", "dirty", "uncertain")) savedState
            .remove<Any>("address.$key")
        for (field in AddressField.values()) savedState.remove<Any>("address.field.${field.name}")
    }

    private fun saveDraft() {
        val state = _uiState.value
        savedState["address.owner"] = ownerId
        savedState["address.stage"] = state.stage.name
        savedState["address.id"] = state.addressId
        savedState["address.default"] = state.isDefault
        savedState["address.dirty"] = state.dirty
        savedState["address.uncertain"] = uncertain
        for (field in AddressField.values()) savedState["address.field.${field.name}"] =
            state.values[field].orEmpty()
    }
}
