package com.example.myhealth.session

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface AuthState {
    data object Loading : AuthState
    data object Unauthenticated : AuthState
    data class Authenticated(val userId: String) : AuthState
    data class Error(val message: String) : AuthState
}

class AuthViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AuthRepository(app)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        // 앱 시작 시 세션 복원
        viewModelScope.launch {
            repo.userIdFlow.collect { uid ->
                _authState.value = if (uid.isNullOrEmpty()) AuthState.Unauthenticated
                else AuthState.Authenticated(uid)
            }
        }
    }

    fun login(id: String, pw: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            val r = repo.login(id, pw)
            _authState.value = r.fold(
                onSuccess = { AuthState.Authenticated(it) },
                onFailure = { AuthState.Error(it.message ?: "로그인 실패") }
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            repo.logout()
            _authState.value = AuthState.Unauthenticated
        }
    }
}
