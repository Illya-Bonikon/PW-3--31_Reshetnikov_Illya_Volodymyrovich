package ua.kpi.building_audit.auth

import com.google.gson.Gson
import com.google.gson.JsonParseException
import java.util.Base64

enum class Permission(val title: String) {
    VIEW_BUILDINGS("Перегляд реєстру будівель"),
    EDIT_BUILDINGS("Додавання та редагування об'єктів"),
    DELETE_BUILDINGS("Видалення об'єктів"),
    VIEW_USERS("Перегляд облікових записів"),
    CLEAR_JOURNAL("Очищення журналу помилок")
}

/** Права ролі задаються тут один раз; UI лише питає session.can(...). */
enum class Role(val title: String, val permissions: Set<Permission>) {
    ADMIN("Адміністратор", Permission.entries.toSet()),
    AUDITOR("Енергоаудитор", setOf(Permission.VIEW_BUILDINGS, Permission.EDIT_BUILDINGS)),
    VIEWER("Спостерігач", setOf(Permission.VIEW_BUILDINGS))
}

data class Session(
    val token: String,
    val login: String,
    val fullName: String,
    val role: Role,
    val issuedAtSec: Long,
    val expiresAtSec: Long
) {
    fun can(permission: Permission): Boolean = permission in role.permissions

    fun secondsLeft(nowMs: Long): Long = (expiresAtSec - nowMs / 1000).coerceAtLeast(0)

    fun isExpired(nowMs: Long): Boolean = nowMs / 1000 >= expiresAtSec
}

class InvalidTokenException(message: String) : Exception(message)

/**
 * Читає payload JWT без перевірки підпису: секрет є лише на сервері.
 * Клієнту достатньо знати роль і час завершення, а справжність токена перевіряє сервер на кожному запиті.
 */
class JwtDecoder(private val gson: Gson) {
    private class Claims(val sub: String?, val name: String?, val role: String?, val iat: Long?, val exp: Long?)

    fun decode(token: String): Session {
        val parts = token.split('.')
        if (parts.size != 3) throw InvalidTokenException("JWT має складатися з трьох частин")
        val json = try {
            String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
        } catch (error: IllegalArgumentException) {
            throw InvalidTokenException("Payload не є Base64URL")
        }
        val claims = try {
            gson.fromJson(json, Claims::class.java)
        } catch (error: JsonParseException) {
            throw InvalidTokenException("Payload не є JSON")
        } ?: throw InvalidTokenException("Порожній payload")
        val login = claims.sub ?: throw InvalidTokenException("Немає claim sub")
        return Session(
            token = token,
            login = login,
            fullName = claims.name ?: login,
            role = Role.entries.firstOrNull { it.name == claims.role } ?: throw InvalidTokenException("Невідома роль ${claims.role}"),
            issuedAtSec = claims.iat ?: 0,
            expiresAtSec = claims.exp ?: throw InvalidTokenException("Немає claim exp")
        )
    }
}
