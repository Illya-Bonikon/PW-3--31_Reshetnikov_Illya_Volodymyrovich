package ua.kpi.building_audit.practical9

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ua.kpi.building_audit.domain.BuildingDraft

@Composable
fun BuildingFormDialog(onDismiss: () -> Unit, onSubmit: (BuildingDraft) -> Unit) {
    var draft by remember { mutableStateOf(BuildingDraft()) }
    var submitted by remember { mutableStateOf(false) }
    // Помилки показуються після першої спроби збереження, щоб порожня форма не була вся червоною.
    val errors = if (submitted) draft.validate() else emptyMap()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новий об'єкт аудиту") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DraftField("Назва", draft.name, errors["name"]) { draft = draft.copy(name = it) }
                DraftField("Адреса", draft.address, errors["address"]) { draft = draft.copy(address = it) }
                DraftField("Опалювана площа, м²", draft.heatedArea, errors["heatedArea"], numeric = true) { draft = draft.copy(heatedArea = it) }
                DraftField("E-mail аудитора", draft.auditorEmail, errors["auditorEmail"]) { draft = draft.copy(auditorEmail = it) }
                DraftField("Площа зовнішніх стін, м²", draft.wallArea, errors["wallArea"], numeric = true) { draft = draft.copy(wallArea = it) }
                DraftField("Утеплювач стін, мм", draft.insulationMm, errors["insulationMm"], numeric = true) { draft = draft.copy(insulationMm = it) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                submitted = true
                if (draft.validate().isEmpty()) onSubmit(draft)
            }) { Text("Зберегти") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )
}

@Composable
private fun DraftField(label: String, value: String, error: String?, numeric: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = if (error != null) { { Text(error) } } else null,
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        singleLine = true
    )
}
