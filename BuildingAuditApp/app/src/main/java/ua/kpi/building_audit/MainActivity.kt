package ua.kpi.building_audit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.kpi.building_audit.auth.SessionState
import ua.kpi.building_audit.error.ErrorCenter
import ua.kpi.building_audit.practical10.ApiExplorerScreen
import ua.kpi.building_audit.practical11.LoginScreen
import ua.kpi.building_audit.practical11.SessionScreen
import ua.kpi.building_audit.practical12.DiagnosticsScreen
import ua.kpi.building_audit.practical9.RegistryScreen
import ua.kpi.building_audit.ui.components.DeveloperInfoDialog
import ua.kpi.building_audit.ui.theme.BuildingAuditTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as EnergyAuditApp).container
        setContent {
            BuildingAuditTheme {
                AppRoot(container)
            }
        }
    }
}

/** Без активного сеансу показується вхід; завершення сеансу будь-де в застосунку повертає сюди. */
@Composable
fun AppRoot(container: AppContainer) {
    val sessionState by container.sessionManager.state.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val state = sessionState) {
            is SessionState.Active -> MainHub(container.errorCenter)
            is SessionState.Ended -> LoginScreen(ended = state)
            SessionState.SignedOut -> LoginScreen(ended = null)
        }
    }
}

private val TABS = listOf("ПЗ 9: Repository", "ПЗ 10: REST API", "ПЗ 11: Сеанс", "ПЗ 12: Помилки")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainHub(errorCenter: ErrorCenter) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showDeveloperDialog by remember { mutableStateOf(false) }
    var criticalMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Єдине місце, де помилки з усіх екранів потрапляють до користувача.
    LaunchedEffect(errorCenter) {
        errorCenter.messages.collect { message ->
            if (message.critical) criticalMessage = message.text else snackbarHostState.showSnackbar(message.text)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Енергоаудит будівель", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = { showDeveloperDialog = true }) {
                        Text("Про розробника", fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        label = { Text(title) },
                        icon = {}
                    )
                }
            }
        }
    ) { innerPadding ->
        Surface(Modifier.fillMaxSize().padding(innerPadding), color = MaterialTheme.colorScheme.background) {
            when (selectedTab) {
                0 -> RegistryScreen()
                1 -> ApiExplorerScreen()
                2 -> SessionScreen()
                else -> DiagnosticsScreen()
            }
        }
    }

    if (showDeveloperDialog) DeveloperInfoDialog(onDismissRequest = { showDeveloperDialog = false })

    criticalMessage?.let { text ->
        AlertDialog(
            onDismissRequest = { criticalMessage = null },
            title = { Text("Помилка") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { criticalMessage = null }) { Text("Зрозуміло") } }
        )
    }
}
