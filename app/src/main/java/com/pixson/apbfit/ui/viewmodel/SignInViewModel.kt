package com.pixson.apbfit.ui.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixson.apbfit.data.repository.AccountRepository
import com.pixson.apbfit.ui.util.UiStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignInUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val uiStrings: UiStrings,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    fun getSignInIntent(): Intent = accountRepository.getSignInIntent()

    fun onSignInResult(data: Intent?) {
        viewModelScope.launch {
            _uiState.value = SignInUiState(isLoading = true)
            val result = accountRepository.handleSignInResult(data)
            _uiState.value = result.fold(
                onSuccess = { SignInUiState(isLoading = false) },
                onFailure = {
                    SignInUiState(
                        isLoading = false,
                        errorMessage = it.message ?: uiStrings.signInFailed,
                    )
                },
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
