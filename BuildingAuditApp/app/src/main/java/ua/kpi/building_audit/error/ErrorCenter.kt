package ua.kpi.building_audit.error

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import ua.kpi.building_audit.network.ConnectivityStatus

/** Повідомлення для UI: CRITICAL показується діалогом, решта — через Snackbar. */
data class UiMessage(val text: String, val critical: Boolean)

/** Звіт про одну спробу запиту; екран ПЗ 12 малює з них хронологію повторів. */
data class AttemptReport(val attempt: Int, val error: AppError?, val nextDelayMs: Long)

/**
 * Централізований обробник: усі мережеві виклики застосунку проходять через [execute].
 * Тут в одному місці перевіряється мережа, класифікуються винятки, виконуються повтори,
 * пишеться журнал і формуються повідомлення користувачу.
 */
class ErrorCenter(
    private val mapper: ErrorMapper,
    private val journal: ErrorJournal,
    private val connectivity: ConnectivityStatus,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) }
) {
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 16)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    /** Відмови токена; SessionManager слухає цей потік і завершує сеанс. */
    private val _authFailures = MutableSharedFlow<AuthFailure>(extraBufferCapacity = 4)
    val authFailures: SharedFlow<AuthFailure> = _authFailures.asSharedFlow()

    /** Збільшується з кожним записом, щоб екран журналу знав, коли перечитати файл. */
    private val _journalVersion = MutableStateFlow(0L)
    val journalVersion: StateFlow<Long> = _journalVersion.asStateFlow()

    suspend fun <T> execute(
        operation: String,
        policy: RetryPolicy = RetryPolicy.NONE,
        notifyUser: Boolean = true,
        guardsSession: Boolean = true,
        onAttempt: (AttemptReport) -> Unit = {},
        block: suspend () -> T
    ): Outcome<T> {
        var attempt = 1
        while (true) {
            val error = if (!connectivity.state.value.isOnline) AppError.NoConnection else try {
                val value = block()
                onAttempt(AttemptReport(attempt, null, 0))
                return Outcome.Success(value)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (throwable: Throwable) {
                mapper.map(throwable)
            }

            record(operation, attempt, error)
            val retry = policy.shouldRetry(attempt, error)
            val pause = if (retry) policy.delayBefore(attempt + 1, error) else 0L
            onAttempt(AttemptReport(attempt, error, pause))
            if (!retry) {
                publish(error, notifyUser, guardsSession)
                return Outcome.Failure(error)
            }
            sleep(pause)
            attempt++
        }
    }

    fun notify(text: String) {
        _messages.tryEmit(UiMessage(text, critical = false))
    }

    private suspend fun record(operation: String, attempt: Int, error: AppError) {
        journal.record(JournalEntry(clock(), operation, attempt, error.kind, error.httpCode, error.userMessage))
        _journalVersion.value++
    }

    private fun publish(error: AppError, notifyUser: Boolean, guardsSession: Boolean) {
        if (guardsSession && error is AppError.Unauthorized && error.reason != AuthFailure.BAD_CREDENTIALS) {
            _authFailures.tryEmit(error.reason)
            return
        }
        if (!notifyUser) return
        val critical = error is AppError.Forbidden || error is AppError.ServerFailure || error is AppError.InvalidResponse
        _messages.tryEmit(UiMessage(error.userMessage, critical))
    }
}
