package ua.kpi.building_audit.error

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class JournalEntry(
    val timestampMs: Long,
    val operation: String,
    val attempt: Int,
    val kind: String,
    val httpCode: Int?,
    val message: String
)

interface ErrorJournal {
    suspend fun record(entry: JournalEntry)
    suspend fun recent(limit: Int): List<JournalEntry>
    suspend fun clear()
}

/**
 * Журнал у форматі JSON Lines у внутрішньому сховищі застосунку: один запис на рядок,
 * файл читається будь-яким текстовим редактором після `adb pull`.
 */
class FileErrorJournal(
    private val file: File,
    private val gson: Gson,
    private val maxEntries: Int = 500
) : ErrorJournal {
    private val mutex = Mutex()

    override suspend fun record(entry: JournalEntry) {
        io<Unit> {
            file.appendText(gson.toJson(entry) + "\n")
            val lines = file.readLines()
            if (lines.size > maxEntries) {
                file.writeText(lines.takeLast(maxEntries).joinToString("\n", postfix = "\n"))
            }
        }
    }

    override suspend fun recent(limit: Int): List<JournalEntry> = io {
        if (!file.exists()) return@io emptyList()
        file.readLines()
            .takeLast(limit)
            .mapNotNull { line -> runCatching { gson.fromJson(line, JournalEntry::class.java) }.getOrNull() }
            .asReversed()
    }

    override suspend fun clear() {
        io<Unit> { file.writeText("") }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { mutex.withLock { block() } }
}
