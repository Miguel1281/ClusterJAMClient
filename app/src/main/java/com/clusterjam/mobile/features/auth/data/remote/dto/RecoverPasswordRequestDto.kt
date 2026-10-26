package com.clusterjam.mobile.features.auth.data.remote.dto

data class RecoverPasswordRequestDto(val email: String, val code: String, val newPassword: String)
