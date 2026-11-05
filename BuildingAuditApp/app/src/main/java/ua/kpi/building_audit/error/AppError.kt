package ua.kpi.building_audit.error

/** Причини відмови в автентифікації; назви збігаються з кодами помилок сервера. */
enum class AuthFailure(val message: String) {
    BAD_CREDENTIALS("Невірний логін або пароль."),
    TOKEN_MISSING("Запит без токена відхилено: потрібна автентифікація."),
    TOKEN_INVALID("Токен недійсний: підпис або структура не збігаються."),
    TOKEN_EXPIRED("Термін дії токена минув. Увійдіть повторно.");

    companion object {
        fun fromCode(code: String?): AuthFailure = entries.firstOrNull { it.name == code } ?: TOKEN_INVALID
    }
}

/**
 * Єдина модель помилки для всього застосунку. UI працює лише з нею і не знає
 * про IOException, HttpException чи винятки Gson.
 *
 * isTransient означає, що повтор того самого запиту має шанс на успіх.
 */
sealed class AppError(val userMessage: String) {
    open val isTransient: Boolean = false
    open val httpCode: Int? = null
    open val retryAfterMs: Long? = null

    val kind: String get() = this::class.simpleName ?: "AppError"

    data object NoConnection : AppError("Немає підключення до мережі. Перевірте з'єднання і повторіть спробу.")

    data object Timeout : AppError("Сервер не відповів вчасно.") {
        override val isTransient = true
    }

    data object ServerUnreachable : AppError("Сервер недоступний. Перевірте, чи його запущено.") {
        override val isTransient = true
    }

    data class NetworkFailure(val details: String) : AppError("Збій мережевого з'єднання.") {
        override val isTransient = true
    }

    data class Unauthorized(val reason: AuthFailure) : AppError(reason.message) {
        override val httpCode = 401
    }

    data object Forbidden : AppError("Недостатньо прав для цієї дії.") {
        override val httpCode = 403
    }

    data object NotFound : AppError("Запитаний об'єкт не знайдено на сервері.") {
        override val httpCode = 404
    }

    data class Validation(override val httpCode: Int, val fields: Map<String, String>) :
        AppError("Сервер відхилив дані: " + fields.entries.joinToString { "${it.key} — ${it.value}" }.ifEmpty { "некоректний запит" })

    data class RateLimited(override val retryAfterMs: Long?) : AppError("Забагато запитів до сервера.") {
        override val isTransient = true
        override val httpCode = 429
    }

    data class ServiceUnavailable(override val httpCode: Int, override val retryAfterMs: Long?) :
        AppError("Сервіс тимчасово недоступний (HTTP $httpCode).") {
        override val isTransient = true
    }

    /** 500 не вважається тимчасовою: повтор того самого запиту зазвичай дає ту саму помилку. */
    data class ServerFailure(override val httpCode: Int) : AppError("Внутрішня помилка сервера (HTTP $httpCode).")

    data class InvalidResponse(val details: String) : AppError("Сервер повернув некоректні дані.")

    /** Відмова на боці клієнта без звернення до сервера. */
    data class Rejected(val reason: String) : AppError(reason)

    data class Unexpected(val details: String) : AppError("Непередбачена помилка: $details")
}

/** Дозволяє шару даних кинути вже класифіковану помилку крізь suspend-виклики. */
class AppErrorException(val error: AppError) : Exception(error.userMessage)
