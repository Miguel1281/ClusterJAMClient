package com.clusterjam.mobile.features.profile.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clusterjam.mobile.features.auth.domain.model.SessionState
import com.clusterjam.mobile.features.auth.domain.repository.AuthRepository
import com.clusterjam.mobile.features.profile.domain.model.ProfileChanges
import com.clusterjam.mobile.features.profile.domain.model.ProfileFailure
import com.clusterjam.mobile.features.profile.domain.repository.ProfileRepository
import com.clusterjam.mobile.features.profile.domain.validation.ProfileValidator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProfileFormViewModel(
    private val repository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private var ownerId: String? = savedState["profile.owner"]
    private var uncertain: Boolean = savedState["profile.uncertain"] ?: false
    private val _uiState =
        MutableStateFlow(
            ProfileFormUiState(
                mode =
                    if (savedState.get<String>("profile.mode") == "EDIT") ProfileFormMode.EDIT
                    else ProfileFormMode.ONBOARDING,
                isOpen = savedState["profile.open"] ?: false,
                fullName = savedState["profile.name"] ?: "",
                phone = savedState["profile.phone"] ?: "",
                contactEmail = savedState["profile.email"] ?: "",
                dirty = savedState["profile.dirty"] ?: false,
            ),
        )
    val uiState = _uiState.asStateFlow()
    private var visible = false
    private var operationId = 0L
    private var job: Job? = null

    init {
        viewModelScope.launch {
            authRepository.sessionState.collect { session ->
                when (session) {
                    is SessionState.Authenticated -> {
                        if (ownerId != null && ownerId != session.identity.userId) close()
                        ownerId = session.identity.userId
                        savedState["profile.owner"] = ownerId
                        if (visible && _uiState.value.isOpen && _uiState.value.needsReload) reload()
                    }
                    SessionState.Unauthenticated,
                    is SessionState.AccessDenied -> close()
                    else -> suspendWork()
                }
            }
        }
    }

    fun begin(mode: ProfileFormMode) {
        val owner = currentOwner() ?: return
        if (ownerId == owner && _uiState.value.isOpen && _uiState.value.mode == mode) return
        close()
        ownerId = owner
        _uiState.value = ProfileFormUiState(mode = mode, isOpen = true)
        saveDraft()
    }

    fun onVisible() {
        visible = true
        if (_uiState.value.isOpen && _uiState.value.needsReload) reload()
    }

    fun resumeMode(mode: ProfileFormMode) {
        if (!_uiState.value.isOpen || _uiState.value.isBusy) return
        _uiState.update { it.copy(mode = mode) }
        saveDraft()
    }

    fun onHidden() {
        visible = false
        suspendWork()
    }

    fun changeName(value: String) = change { it.copy(fullName = value) }

    fun changePhone(value: String) = change { it.copy(phone = value) }

    fun changeEmail(value: String) = change { it.copy(contactEmail = value) }

    private fun change(update: (ProfileFormUiState) -> ProfileFormUiState) {
        if (!_uiState.value.isOpen || _uiState.value.isBusy || _uiState.value.saved) return
        _uiState.update { update(it).copy(dirty = true, error = null, validationError = null) }
        saveDraft()
    }

    fun reload() {
        val owner = currentOwner() ?: return
        if (!visible || !_uiState.value.isOpen || _uiState.value.isBusy) return
        val operation = ++operationId
        _uiState.update { it.copy(isLoading = true, error = null, validationError = null) }
        job = viewModelScope.launch {
            try {
                val result = repository.getMyProfile()
                if (!isCurrent(operation, owner)) return@launch
                val snapshot = result.getOrNull()
                if (snapshot == null || !snapshot.profile.userId.equals(owner, true)) {
                    val error = result.exceptionOrNull() ?: ProfileFailure("IDENTITY_MISMATCH")
                    _uiState.update {
                        it.copy(needsReload = true, error = ProfileMessageMapper.failure(error))
                    }
                    return@launch
                }
                val current = _uiState.value
                val review =
                    current.dirty && (uncertain || current.snapshot?.revision != snapshot.revision)
                _uiState.value =
                    current.copy(
                        snapshot = snapshot,
                        needsReload = false,
                        needsReview = review,
                        fullName =
                            if (current.dirty) current.fullName
                            else snapshot.profile.fullName.orEmpty(),
                        phone =
                            if (current.dirty) current.phone else snapshot.profile.phone.orEmpty(),
                        contactEmail =
                            if (current.dirty) current.contactEmail
                            else snapshot.profile.contactEmail.orEmpty(),
                    )
                saveDraft()
            } finally {
                if (operation == operationId) _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun keepDraft() {
        if (_uiState.value.isBusy || !_uiState.value.needsReview) return
        uncertain = false
        _uiState.update { it.copy(needsReview = false, error = null) }
        saveDraft()
    }

    fun useServerValues() {
        val state = _uiState.value
        val profile = state.snapshot?.profile ?: return
        if (state.isBusy) return
        uncertain = false
        _uiState.update {
            it.copy(
                fullName = profile.fullName.orEmpty(),
                phone = profile.phone.orEmpty(),
                contactEmail = profile.contactEmail.orEmpty(),
                dirty = false,
                needsReview = false,
                error = null,
                validationError = null,
            )
        }
        saveDraft()
    }

    fun save() {
        val form = _uiState.value
        val owner = currentOwner() ?: return
        if (!visible || !form.canSave || System.currentTimeMillis() < form.retryAtMillis) return
        val changes =
            ProfileChanges(form.fullName.trim(), form.phone.trim(), form.contactEmail.trim())
        val invalid =
            ProfileValidator.nameError(form.fullName)
                ?: ProfileValidator.phoneError(form.phone)
                ?: ProfileValidator.contactEmailError(form.contactEmail)
        if (invalid != null) {
            _uiState.update { it.copy(validationError = invalid, error = null) }
            return
        }
        val revision = form.snapshot?.revision ?: return
        val operation = ++operationId
        uncertain = true
        savedState["profile.uncertain"] = true
        _uiState.update { it.copy(isSaving = true, error = null, validationError = null) }
        job = viewModelScope.launch {
            try {
                val result = repository.updateMyProfile(changes, revision)
                if (!isCurrent(operation, owner)) return@launch
                val snapshot = result.getOrNull()
                if (snapshot != null && snapshot.profile.userId.equals(owner, true)) {
                    uncertain = false
                    _uiState.update {
                        it.copy(
                            snapshot = snapshot,
                            fullName = snapshot.profile.fullName.orEmpty(),
                            phone = snapshot.profile.phone.orEmpty(),
                            contactEmail = snapshot.profile.contactEmail.orEmpty(),
                            saved = true,
                            isOpen = false,
                            dirty = false,
                            needsReload = false,
                        )
                    }
                    saveDraft()
                } else {
                    val failure = result.exceptionOrNull() ?: ProfileFailure("INVALID_RESPONSE")
                    val status = (failure as? ProfileFailure)?.status
                    uncertain = status == null || status >= 500
                    val mustRead = uncertain || status in setOf(401, 412, 428)
                    val wait =
                        (failure as? ProfileFailure)?.retryAfterMillis?.coerceIn(0, 86_400_000L)
                            ?: 0L
                    _uiState.update {
                        it.copy(
                            error = ProfileMessageMapper.failure(failure),
                            needsReload = mustRead,
                            retryAtMillis = System.currentTimeMillis() + wait,
                        )
                    }
                    saveDraft()
                }
            } finally {
                if (operation == operationId) _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun close() {
        operationId++
        job?.cancel()
        job = null
        ownerId = null
        uncertain = false
        _uiState.value = ProfileFormUiState()
        for (key in
            listOf(
                "owner",
                "open",
                "mode",
                "name",
                "phone",
                "email",
                "dirty",
                "uncertain",
            )) savedState.remove<Any>("profile.$key")
    }

    private fun suspendWork() {
        operationId++
        job?.cancel()
        job = null
        _uiState.update { it.copy(isLoading = false, isSaving = false, needsReload = !it.saved) }
    }

    private fun currentOwner(): String? =
        (authRepository.sessionState.value as? SessionState.Authenticated)?.identity?.userId

    private fun isCurrent(operation: Long, owner: String): Boolean =
        visible && operation == operationId && ownerId == owner && currentOwner() == owner

    private fun saveDraft() {
        val form = _uiState.value
        savedState["profile.owner"] = ownerId
        savedState["profile.open"] = form.isOpen
        savedState["profile.mode"] = form.mode.name
        savedState["profile.name"] = form.fullName
        savedState["profile.phone"] = form.phone
        savedState["profile.email"] = form.contactEmail
        savedState["profile.dirty"] = form.dirty
        savedState["profile.uncertain"] = uncertain
    }
}
