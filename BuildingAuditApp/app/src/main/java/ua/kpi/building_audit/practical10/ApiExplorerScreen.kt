package ua.kpi.building_audit.practical10

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.kpi.building_audit.domain.EnergyCalculator
import ua.kpi.building_audit.domain.SavingsForecast
import ua.kpi.building_audit.ui.EnergyClassBadge
import ua.kpi.building_audit.ui.InfoRow
import ua.kpi.building_audit.ui.SectionCard
import ua.kpi.building_audit.ui.format
import ua.kpi.building_audit.ui.theme.ErrorRed
import ua.kpi.building_audit.ui.theme.GreenPrimary

@Composable
fun ApiExplorerScreen(viewModel: ApiExplorerViewModel = viewModel(factory = ApiExplorerViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Spacer(Modifier.padding(top = 4.dp))
            if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            SectionCard("Виклики REST API") {
                state.calls.forEach { call ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(call.endpoint, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                        Text(
                            "${call.durationMs} мс · ${call.result}",
                            color = if (call.ok) GreenPrimary else ErrorRed,
                            fontSize = 13.sp
                        )
                    }
                }
                OutlinedButton(onClick = { viewModel.loadAll() }) { Text("Повторити запити") }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.buildings, key = { it.id }) { building ->
                    FilterChip(
                        selected = state.selectedId == building.id,
                        onClick = { viewModel.select(building.id) },
                        label = { Text(building.name) }
                    )
                }
            }
        }

        state.details?.let { details ->
            val building = details.assessment.building
            val metrics = details.assessment.metrics
            item {
                SectionCard("Паспорт: ${building.name}") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EnergyClassBadge(details.assessment.energyClass)
                        Text("Джерело: ${details.origin.title}", style = MaterialTheme.typography.bodyMedium)
                    }
                    InfoRow("Рік побудови", building.yearBuilt.toString())
                    InfoRow("Площа оболонки", "${metrics.envelopeAreaM2.format()} м²")
                    InfoRow("Коефіцієнт H", "${metrics.heatTransferWPerK.format()} Вт/К")
                    InfoRow("Приведений опір R", "${metrics.effectiveResistance.format(2)} м²·К/Вт")
                    InfoRow("Розрахункові втрати", "${metrics.designHeatLossKw.format(1)} кВт")
                    InfoRow("Потреба в теплі", "${metrics.annualHeatGcal.format(1)} Гкал/рік")
                }
            }
            item {
                SectionCard("Огороджувальні конструкції (вкладений масив envelope)") {
                    building.envelope.forEach { element ->
                        Text(
                            "${element.kind.title}: ${element.areaM2.format()} м², R = ${EnergyCalculator.resistance(element).format(2)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        element.layers.forEach { layer ->
                            Text("    ${layer.material}, ${layer.thicknessMm.format()} мм, λ = ${layer.conductivity}", fontSize = 13.sp)
                        }
                    }
                }
            }
            if (building.inspectionLog.isNotEmpty()) {
                item {
                    SectionCard("Журнал обстежень") {
                        building.inspectionLog.forEach { Text("${it.date}, ${it.inspector}: ${it.note}", fontSize = 13.sp) }
                    }
                }
            }
            item { Text("Прогноз економії від термомодернізації", style = MaterialTheme.typography.titleMedium) }
            items(details.forecasts, key = { it.measure.id }) { ForecastCard(it) }
            item {
                OutlinedButton(onClick = { viewModel.toggleJson() }) {
                    Text(if (state.showJson) "Сховати JSON" else "Показати JSON об'єкта")
                }
                if (state.showJson) {
                    Text(
                        details.json,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    )
                }
            }
        }
        item { Spacer(Modifier.padding(bottom = 8.dp)) }
    }
}

@Composable
private fun ForecastCard(forecast: SavingsForecast) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(forecast.measure.title, style = MaterialTheme.typography.titleMedium)
            InfoRow("Площа", "${forecast.affectedAreaM2.format()} м²")
            InfoRow("Економія", "${forecast.annualSavingGcal.format(1)} Гкал · ${forecast.annualSavingUah.format()} грн/рік")
            InfoRow("Вкладення", "${forecast.investmentUah.format()} грн")
            InfoRow("Окупність", forecast.paybackYears?.let { "${it.format(1)} р." } ?: "не окупається")
            InfoRow("Клас", "${forecast.classBefore.label} → ${forecast.classAfter.label}")
        }
    }
}
