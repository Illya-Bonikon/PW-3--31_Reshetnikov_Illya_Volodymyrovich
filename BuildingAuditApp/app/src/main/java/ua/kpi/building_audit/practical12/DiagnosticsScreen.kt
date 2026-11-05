package ua.kpi.building_audit.practical12

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.kpi.building_audit.data.repository.ChaosMode
import ua.kpi.building_audit.ui.Banner
import ua.kpi.building_audit.ui.InfoRow
import ua.kpi.building_audit.ui.SectionCard
import ua.kpi.building_audit.ui.formatTime
import ua.kpi.building_audit.ui.theme.ErrorRed
import ua.kpi.building_audit.ui.theme.GreenPrimary

@Composable
fun DiagnosticsScreen(viewModel: DiagnosticsViewModel = viewModel(factory = DiagnosticsViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (state.network.isOnline) {
            Banner("Мережа доступна: ${state.network.transport}", GreenPrimary)
        } else {
            Banner("Немає мережі. Запити не надсилаються, доки з'єднання не відновиться.", ErrorRed)
        }

        SectionCard("Режим тестового сервера") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(ChaosMode.entries) { mode ->
                    FilterChip(selected = state.chaos == mode, onClick = { viewModel.setChaos(mode) }, label = { Text(mode.title) })
                }
            }
        }

        SectionCard("Тестовий запит GET /tariffs із повторами") {
            Button(onClick = { viewModel.runTest() }, enabled = !state.run.running) { Text("Виконати") }
            state.run.attempts.forEach { report ->
                val error = report.error
                val line = when {
                    error == null -> "Спроба ${report.attempt}: успіх"
                    report.nextDelayMs > 0 -> "Спроба ${report.attempt}: ${error.kind} (${error.httpCode ?: "-"}), повтор через ${report.nextDelayMs} мс"
                    else -> "Спроба ${report.attempt}: ${error.kind} (${error.httpCode ?: "-"}), без повтору"
                }
                Text(line, color = if (error == null) GreenPrimary else MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            }
            state.run.result?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        }

        SectionCard("Журнал помилок (error_journal.jsonl)") {
            if (state.journal.isEmpty()) Text("Записів немає")
            state.journal.forEach { entry ->
                Column {
                    InfoRow("${formatTime(entry.timestampMs)} · ${entry.operation}", "спроба ${entry.attempt}")
                    Text("${entry.kind} ${entry.httpCode ?: ""}: ${entry.message}", fontSize = 12.sp)
                }
            }
            if (state.canClearJournal) {
                OutlinedButton(onClick = { viewModel.clearJournal() }) { Text("Очистити журнал") }
            }
        }
    }
}
