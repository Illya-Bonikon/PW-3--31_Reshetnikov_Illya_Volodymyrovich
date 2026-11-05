package ua.kpi.building_audit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ua.kpi.building_audit.AppContainer
import ua.kpi.building_audit.EnergyAuditApp
import ua.kpi.building_audit.domain.EnergyClass
import ua.kpi.building_audit.ui.theme.EnergyClassA
import ua.kpi.building_audit.ui.theme.EnergyClassB
import ua.kpi.building_audit.ui.theme.EnergyClassC
import ua.kpi.building_audit.ui.theme.EnergyClassD
import ua.kpi.building_audit.ui.theme.EnergyClassE
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Фабрика ViewModel, що бере залежності з AppContainer. */
inline fun <reified VM : ViewModel> containerFactory(noinline create: (AppContainer) -> VM): ViewModelProvider.Factory =
    viewModelFactory {
        initializer { create((this[APPLICATION_KEY] as EnergyAuditApp).container) }
    }

private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

fun formatTime(epochMs: Long): String = TIME.format(Instant.ofEpochMilli(epochMs))

fun Double.format(digits: Int = 0): String = String.format(Locale("uk", "UA"), "%,.${digits}f", this)

fun EnergyClass.color(): Color = when (this) {
    EnergyClass.A_PLUS, EnergyClass.A -> EnergyClassA
    EnergyClass.B -> EnergyClassB
    EnergyClass.C -> EnergyClassC
    EnergyClass.D -> EnergyClassD
    EnergyClass.E, EnergyClass.F -> EnergyClassE
}

@Composable
fun EnergyClassBadge(energyClass: EnergyClass) {
    Text(
        text = energyClass.label,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(energyClass.color(), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 2.dp)
    )
}

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Banner(text: String, color: Color) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .background(color, RoundedCornerShape(8.dp))
            .padding(10.dp)
    )
}
