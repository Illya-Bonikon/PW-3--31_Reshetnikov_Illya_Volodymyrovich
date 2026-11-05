package ua.kpi.building_audit.practical11

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ua.kpi.building_audit.data.repository.AuthRepository
import ua.kpi.building_audit.error.onFailure
import ua.kpi.building_audit.ui.containerFactory

data class LoginUiState(
    val login: String = "",
    val password: String = "",
    val shortLived: Boolean = false,
    val loginError: String? = null,
    val passwordError: String? = null,
    val message: String? = null,
    val isLoading: Boolean = false
)

/** Тестові облікові записи сервера; кнопки на екрані лише підставляють їх у поля. */
enum class DemoAccount(val login: String, val password: String, val title: String) {
    ADMIN("admin", "admin2026", "Адміністратор"),
    AUDITOR("auditor", "audit2026", "Аудитор"),
    VIEWER("viewer", "view2026", "Спостерігач")
}

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onLoginChange(value: String) = _state.update { it.copy(login = value, loginError = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, passwordError = null) }
    fun onShortLivedChange(value: Boolean) = _state.update { it.copy(shortLived = value) }
    fun fill(account: DemoAccount) = _state.update { it.copy(login = account.login, password = account.password, loginError = null, passwordError = null) }

    fun submit() {
        val current = state.value
        val loginError = if (current.login.isBlank()) "Введіть логін" else null
        val passwordError = if (current.password.length < 4) "Пароль має містити щонайменше 4 символи" else null
        if (loginError != null || passwordError != null) {
            _state.update { it.copy(loginError = loginError, passwordError = passwordError) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, message = null) }
            // Успіх обробляти не треба: SessionManager змінить стан, і AppRoot перейде на головний екран.
            auth.login(current.login, current.password, current.shortLived)
                .onFailure { error -> _state.update { it.copy(message = error.userMessage) } }
            _state.update { it.copy(isLoading = false) }
        }
    }

    companion object {
        val Factory = containerFactory { LoginViewModel(it.authRepository) }
    }
}
