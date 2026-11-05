package ua.kpi.building_audit.data.repository

import ua.kpi.building_audit.data.remote.ChaosRequestDto
import ua.kpi.building_audit.data.remote.EnergyAuditApi
import ua.kpi.building_audit.error.AttemptReport
import ua.kpi.building_audit.error.ErrorCenter
import ua.kpi.building_audit.error.ErrorJournal
import ua.kpi.building_audit.error.JournalEntry
import ua.kpi.building_audit.error.Outcome
import ua.kpi.building_audit.error.RetryPolicy

/** Режими збоїв тестового сервера; назви збігаються з server/audit_server/chaos.py. */
enum class ChaosMode(val title: String) {
    NONE("Без збоїв"),
    FLAKY("2 відмови 503, далі успіх"),
    UNAVAILABLE("503 + Retry-After"),
    RATE_LIMIT("429 + Retry-After"),
    SERVER_ERROR("500"),
    TIMEOUT("Затримка 12 с"),
    MALFORMED_JSON("Пошкоджений JSON")
}

class DiagnosticsRepository(
    private val api: EnergyAuditApi,
    private val errors: ErrorCenter,
    private val journal: ErrorJournal
) {
    suspend fun setChaos(mode: ChaosMode): Outcome<Unit> =
        errors.execute("POST /debug/chaos") { api.configureChaos(ChaosRequestDto(mode.name)); Unit }

    suspend fun testRequest(onAttempt: (AttemptReport) -> Unit): Outcome<Int> =
        errors.execute("GET /tariffs (тест)", RetryPolicy.NETWORK, onAttempt = onAttempt) {
            api.tariffs().measures.orEmpty().size
        }

    suspend fun journal(limit: Int = 30): List<JournalEntry> = journal.recent(limit)

    suspend fun clearJournal() = journal.clear()
}
