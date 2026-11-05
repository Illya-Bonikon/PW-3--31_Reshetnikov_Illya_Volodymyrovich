package ua.kpi.building_audit.practical9

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.kpi.building_audit.auth.Permission
import ua.kpi.building_audit.data.repository.DataSourceMode
import ua.kpi.building_audit.domain.BuildingAssessment
import ua.kpi.building_audit.domain.EnergyClass
import ua.kpi.building_audit.domain.SortOrder
import ua.kpi.building_audit.ui.Banner
import ua.kpi.building_audit.ui.EnergyClassBadge
import ua.kpi.building_audit.ui.InfoRow
import ua.kpi.building_audit.ui.SectionCard
import ua.kpi.building_audit.ui.format
import ua.kpi.building_audit.ui.formatTime
import ua.kpi.building_audit.ui.theme.WarningAmber

@Composable
fun RegistryScreen(viewModel: RegistryViewModel = viewModel(factory = RegistryViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showForm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Spacer(Modifier.padding(top = 4.dp))
            Text("Джерело даних", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DataSourceMode.entries.forEach { mode ->
                    FilterChip(selected = state.mode == mode, onClick = { viewModel.selectMode(mode) }, label = { Text(mode.title) })
                }
            }
            if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            when {
                state.isStale -> Banner("Сервер недоступний. Показано дані з кешу" + state.lastSyncMs.syncSuffix(), WarningAmber)
                state.mode != DataSourceMode.REMOTE && state.lastSyncMs != null ->
                    Text("Кеш оновлено" + state.lastSyncMs.syncSuffix(), style = MaterialTheme.typography.bodyMedium)
            }
        }

        item { SummaryCard(state) }

        item {
            OutlinedTextField(
                value = state.query.text,
                onValueChange = { viewModel.updateQuery(state.query.copy(text = it)) },
                label = { Text("Пошук за назвою або адресою") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(EnergyClass.entries) { energyClass ->
                    FilterChip(
                        selected = energyClass in state.query.classes,
                        onClick = { viewModel.updateQuery(state.query.toggle(energyClass)) },
                        label = { Text(energyClass.label) }
                    )
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(SortOrder.entries) { order ->
                    FilterChip(
                        selected = state.query.sort == order,
                        onClick = { viewModel.updateQuery(state.query.copy(sort = order)) },
                        label = { Text(order.title) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.reload(force = true) }) { Text("Оновити") }
                // Кнопка додавання видима лише ролям із правом редагування.
                if (Permission.EDIT_BUILDINGS in state.permissions) {
                    Button(onClick = { showForm = true }) { Text("Додати об'єкт") }
                }
            }
        }

        items(state.items, key = { it.building.id }) { item ->
            BuildingCard(
                item = item,
                canDelete = Permission.DELETE_BUILDINGS in state.permissions || item.building.id < 0,
                onDelete = { viewModel.delete(item.building) }
            )
        }

        item { Spacer(Modifier.padding(bottom = 8.dp)) }
    }

    if (showForm) {
        BuildingFormDialog(
            onDismiss = { showForm = false },
            onSubmit = { draft ->
                viewModel.add(draft)
                showForm = false
            }
        )
    }
}

private fun Long?.syncSuffix(): String = this?.let { " о ${formatTime(it)}" }.orEmpty()

@Composable
private fun SummaryCard(state: RegistryUiState) {
    val summary = state.summary
    SectionCard("Зведення за фільтром") {
        InfoRow("Об'єктів", summary.buildingCount.toString())
        InfoRow("Опалювана площа", "${summary.heatedAreaM2.format()} м²")
        InfoRow("Потреба в теплі", "${summary.annualHeatGcal.format(1)} Гкал/рік")
        InfoRow("Вартість опалення", "${summary.annualCostUah.format()} грн/рік")
        InfoRow("Середні питомі втрати", "${summary.weightedSpecificLossWPerM2.format(1)} Вт/м²")
        InfoRow("Класи", summary.classDistribution.entries.joinToString { "${it.key.label}: ${it.value}" }.ifEmpty { "—" })
        Text("Тариф: ${state.tariffsOrigin.title}", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BuildingCard(item: BuildingAssessment, canDelete: Boolean, onDelete: () -> Unit) {
    val building = item.building
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(building.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                EnergyClassBadge(item.energyClass)
            }
            Text(building.address, style = MaterialTheme.typography.bodyMedium)
            InfoRow("Питомі втрати", "${item.metrics.specificHeatLossWPerM2.format(1)} Вт/м²")
            InfoRow("Опалення на рік", "${item.annualCostUah.format()} грн")
            if (building.id < 0) Text("Створено локально, на сервері відсутній", style = MaterialTheme.typography.bodyMedium)
            if (canDelete) {
                TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.End)) { Text("Видалити") }
            }
        }
    }
}
