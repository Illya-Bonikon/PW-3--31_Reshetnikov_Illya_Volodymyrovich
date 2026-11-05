package ua.kpi.building_audit.practical11

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.kpi.building_audit.auth.SessionState
import ua.kpi.building_audit.ui.Banner
import ua.kpi.building_audit.ui.theme.ErrorRed
import ua.kpi.building_audit.ui.theme.WarningAmber

@Composable
fun LoginScreen(ended: SessionState.Ended?, viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        Text("Енергоаудит будівель", style = MaterialTheme.typography.titleLarge)
        Text("Вхід до системи", style = MaterialTheme.typography.bodyLarge)

        ended?.let { Banner("Сеанс користувача ${it.login} завершено. ${it.reason.message}", WarningAmber) }

        OutlinedTextField(
            value = state.login,
            onValueChange = viewModel::onLoginChange,
            label = { Text("Логін") },
            isError = state.loginError != null,
            supportingText = state.loginError?.let { error -> @Composable { Text(error) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text("Пароль") },
            isError = state.passwordError != null,
            supportingText = state.passwordError?.let { error -> @Composable { Text(error) } },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = state.shortLived, onCheckedChange = viewModel::onShortLivedChange)
            Text("Короткий токен (45 с) для перевірки завершення сеансу", style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DemoAccount.entries.forEach { account ->
                AssistChip(onClick = { viewModel.fill(account) }, label = { Text(account.title) })
            }
        }
        state.message?.let { Banner(it, ErrorRed) }
        Button(onClick = viewModel::submit, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) {
            if (state.isLoading) CircularProgressIndicator(Modifier.padding(2.dp)) else Text("Увійти")
        }
    }
}
