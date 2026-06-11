package com.pixson.apbfit.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixson.apbfit.data.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    val accountRepository: AccountRepository,
) : ViewModel() {
    init {
        viewModelScope.launch {
            accountRepository.initialize()
        }
    }
}
