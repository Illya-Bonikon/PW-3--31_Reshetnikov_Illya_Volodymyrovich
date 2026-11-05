package ua.kpi.building_audit.error

import kotlin.math.pow
import kotlin.random.Random

/**
 * Експоненційна затримка з випадковим розкидом. Якщо сервер надіслав Retry-After,
 * береться його значення: сервер краще знає, коли відновиться.
 *
 * Повтори застосовуються лише до ідемпотентних запитів (GET, PUT, DELETE).
 * Для POST повтор після таймауту може створити дублікат, тому там політика NONE.
 */
class RetryPolicy(
    val maxAttempts: Int,
    private val initialDelayMs: Long,
    private val multiplier: Double = 2.0,
    private val maxDelayMs: Long = 8_000,
    private val jitterRatio: Double = 0.0,
    private val random: Random = Random.Default
) {
    init {
        require(maxAttempts >= 1) { "Потрібна хоча б одна спроба" }
    }

    fun shouldRetry(attempt: Int, error: AppError): Boolean = attempt < maxAttempts && error.isTransient

    /** Затримка перед спробою з номером [nextAttempt] (нумерація з 1). */
    fun delayBefore(nextAttempt: Int, error: AppError): Long {
        error.retryAfterMs?.let { return it.coerceAtMost(maxDelayMs) }
        val base = initialDelayMs * multiplier.pow(nextAttempt - 2)
        val jitter = base * jitterRatio * (random.nextDouble() * 2 - 1)
        return (base + jitter).toLong().coerceIn(0, maxDelayMs)
    }

    companion object {
        val NONE = RetryPolicy(maxAttempts = 1, initialDelayMs = 0)
        val NETWORK = RetryPolicy(maxAttempts = 4, initialDelayMs = 500, jitterRatio = 0.2)
    }
}
