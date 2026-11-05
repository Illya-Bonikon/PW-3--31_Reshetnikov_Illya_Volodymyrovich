package ua.kpi.building_audit.practical11

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.kpi.building_audit.auth.Permission
import ua.kpi.building_audit.data.repository.TokenProbe
import ua.kpi.building_audit.ui.InfoRow
import ua.kpi.building_audit.ui.SectionCard
import ua.kpi.building_audit.ui.formatTime
import ua.kpi.building_audit.ui.theme.ErrorRed
import ua.kpi.building_audit.ui.theme.GreenPrimary

@Composable
fun SessionScreen(viewModel: SessionViewModel = viewModel(factory = SessionViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session = state.session ?: return

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionCard("Поточний сеанс (claims з JWT)") {
            InfoRow("sub", session.login)
            InfoRow("name", session.fullName)
            InfoRow("role", session.role.title)
            InfoRow("iat", formatTime(session.issuedAtSec * 1000))
            InfoRow("exp", formatTime(session.expiresAtSec * 1000))
            InfoRow("Залишилось", "${state.secondsLeft / 60} хв ${state.secondsLeft % 60} с")
        }

        SectionCard("Права ролі «${session.role.title}»") {
            Permission.entries.forEach { permission ->
                val granted = session.can(permission)
                Text(
                    "${permission.title}: ${if (granted) "дозволено" else "заборонено"}",
                    color = if (granted) GreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SectionCard("Перевірка захищеного ресурсу GET /auth/me") {
            TokenProbe.entries.forEach { probe ->
                OutlinedButton(onClick = { viewModel.probe(probe) }) { Text(probe.title) }
            }
            state.probes.forEach { result ->
                Text("${result.probe.title} → ${result.text}", color = if (result.ok) GreenPrimary else ErrorRed)
            }
        }

        // Розділ існує лише для адміністратора: решта ролей не бачить навіть кнопки.
        if (session.can(Permission.VIEW_USERS)) {
            SectionCard("Облікові записи (GET /admin/users)") {
                OutlinedButton(onClick = { viewModel.loadUsers() }) { Text("Завантажити") }
                state.users.forEach { Text("${it.login} — ${it.fullName}, ${it.role?.title ?: "?"}") }
            }
        }

        Button(onClick = { viewModel.signOut() }) { Text("Вийти") }
    }
}
