package com.sirim.scanner.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sirim.scanner.data.preferences.PreferencesManager
import com.sirim.scanner.data.preferences.StartupPage
import com.sirim.scanner.data.preferences.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PreferencesViewModel private constructor(
    private val manager: PreferencesManager
) : ViewModel() {

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError

    val preferences: StateFlow<UserPreferences> = manager.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    val isSessionValid: StateFlow<Boolean> = preferences
        .map { it.isSessionValid() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setStartupPage(page: StartupPage) {
        viewModelScope.launch {
            manager.setStartupPage(page)
        }
    }

    fun authenticate(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _authError.value = "Username and password are required"
            return
        }

        // Hash password with SHA-256 (basic security improvement)
        val hashedInput = hashPassword(password)
        val storedHash = "8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918" // "admin"

        if (username.equals(ADMIN_USERNAME, ignoreCase = true) && hashedInput == storedHash) {
            viewModelScope.launch {
                manager.setAuthentication()
                _authError.value = null
            }
        } else {
            _authError.value = "Invalid credentials"
        }
    }

    private fun hashPassword(password: String): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(password.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    fun logout() {
        viewModelScope.launch {
            manager.clearAuthentication()
            _authError.value = null
        }
    }

    fun checkSessionExpiry() {
        viewModelScope.launch {
            val currentPreferences = preferences.value
            if (!currentPreferences.isSessionValid()) {
                manager.clearAuthentication()
            }
        }
    }

    fun clearAuthError() {
        _authError.value = null
    }

    companion object {
        private const val ADMIN_USERNAME = "admin"
        
        fun Factory(manager: PreferencesManager): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PreferencesViewModel(manager) as T
                }
            }
    }
}
