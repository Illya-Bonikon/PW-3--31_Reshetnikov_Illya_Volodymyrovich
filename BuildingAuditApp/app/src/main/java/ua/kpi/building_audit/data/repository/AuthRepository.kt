package ua.kpi.building_audit.data.repository

import ua.kpi.building_audit.auth.Role
import ua.kpi.building_audit.auth.Session
import ua.kpi.building_audit.auth.SessionManager
import ua.kpi.building_audit.data.remote.EnergyAuditApi
import ua.kpi.building_audit.data.remote.InvalidPayloadException
import ua.kpi.building_audit.data.remote.LoginRequestDto
import ua.kpi.building_audit.data.remote.UserDto
import ua.kpi.building_audit.error.ErrorCenter
import ua.kpi.building_audit.error.Outcome
import ua.kpi.building_audit.error.RetryPolicy

data class UserProfile(val login: String, val fullName: String, val role: Role?)

/** Як перевірити захищений ресурс на екрані ПЗ 11. */
enum class TokenProbe(val title: String) {
    VALID("З дійсним токеном"),
    MISSING("Без токена"),
    TAMPERED("З підробленим підписом")
}

class AuthRepository(
    private val api: EnergyAuditApi,
    private val sessions: SessionManager,
    private val errors: ErrorCenter
) {
    suspend fun login(login: String, password: String, shortLived: Boolean): Outcome<Session> =
        errors.execute("POST /auth/login", notifyUser = false, guardsSession = false) {
            val response = api.login(LoginRequestDto(login.trim(), password, if (shortLived) SHORT_TTL_SECONDS else null))
            sessions.start(response.accessToken ?: throw InvalidPayloadException("Відсутнє поле accessToken"))
        }

    /** Пробні запити не завершують сеанс: відмова 401 тут є очікуваним результатом. */
    suspend fun probe(mode: TokenProbe): Outcome<UserProfile> =
        errors.execute("GET /auth/me (${mode.title.lowercase()})", RetryPolicy.NONE, guardsSession = mode == TokenProbe.VALID) {
            when (mode) {
                TokenProbe.VALID -> api.me()
                TokenProbe.MISSING -> api.meWithoutToken()
                TokenProbe.TAMPERED -> api.meWithToken("Bearer ${tamper(sessions.currentToken.orEmpty())}")
            }.toProfile()
        }

    suspend fun users(): Outcome<List<UserProfile>> =
        errors.execute("GET /admin/users", RetryPolicy.NETWORK) { api.users().map { it.toProfile() } }

    fun signOut() = sessions.signOut()

    /** Змінює останній символ підпису: структура JWT лишається правильною, а підпис — ні. */
    private fun tamper(token: String): String =
        if (token.isEmpty()) "invalid.token.value" else token.dropLast(1) + if (token.last() == 'A') 'B' else 'A'

    private fun UserDto.toProfile() = UserProfile(
        login = login ?: throw InvalidPayloadException("Відсутнє поле login"),
        fullName = fullName.orEmpty(),
        role = Role.entries.firstOrNull { it.name == role }
    )

    private companion object {
        const val SHORT_TTL_SECONDS = 45
    }
}
