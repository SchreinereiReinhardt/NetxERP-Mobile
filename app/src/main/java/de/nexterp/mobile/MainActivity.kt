package de.nexterp.mobile

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import de.nexterp.mobile.ui.theme.NextERPTheme
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class Screen { TODAY, PROJECTS, PROJECT, SCANNER, MATERIAL, DOCUMENTS, MORE }

data class LoginState(
    val server: String = "https://cloud.kassel-net.de",
    val username: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val loggedIn: Boolean = false,
    val displayName: String = "",
    val error: String? = null
)

data class DashboardData(
    val displayName: String = "",
    val role: String = "monteur",
    val activeProjects: Int = 0,
    val openTasks: Int = 0,
    val newDocuments: Int = 0,
    val openReports: Int = 0,
    val todayProject: ProjectDto? = null
)

data class ProjectDto(
    val id: Int,
    val projectNo: String,
    val title: String,
    val status: String,
    val customerName: String,
    val address: String,
    val phone: String,
    val email: String
)

data class DataState(
    val loading: Boolean = false,
    val dashboard: DashboardData? = null,
    val projects: List<ProjectDto> = emptyList(),
    val selectedProject: ProjectDto? = null,
    val error: String? = null
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NextERPTheme { NextERPApp() } }
    }
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("nexterp_session", 0)

    var loginState by mutableStateOf(
        LoginState(
            server = prefs.getString("server", "https://cloud.kassel-net.de") ?: "https://cloud.kassel-net.de",
            username = prefs.getString("username", "") ?: ""
        )
    )
        private set

    var dataState by mutableStateOf(DataState())
        private set

    var screen by mutableStateOf(Screen.TODAY)
        private set

    fun updateServer(value: String) { loginState = loginState.copy(server = value) }
    fun updateUsername(value: String) { loginState = loginState.copy(username = value) }
    fun updatePassword(value: String) { loginState = loginState.copy(password = value) }

    fun navigate(target: Screen) { screen = target }

    fun openProject(project: ProjectDto) {
        dataState = dataState.copy(selectedProject = project)
        screen = Screen.PROJECT
    }

    fun logout() {
        loginState = LoginState(server = loginState.server, username = loginState.username)
        dataState = DataState()
        screen = Screen.TODAY
    }

    suspend fun login() {
        if (loginState.username.isBlank() || loginState.password.isBlank()) {
            loginState = loginState.copy(error = "Benutzer und App-Passwort eingeben.")
            return
        }

        loginState = loginState.copy(loading = true, error = null)
        val auth = NextcloudApi.checkLogin(loginState.server, loginState.username, loginState.password)
        if (auth.isFailure) {
            loginState = loginState.copy(
                loading = false,
                error = auth.exceptionOrNull()?.message ?: "Anmeldung fehlgeschlagen"
            )
            return
        }

        val displayName = auth.getOrNull().orEmpty()
        prefs.edit()
            .putString("server", loginState.server.trimEnd('/'))
            .putString("username", loginState.username)
            .apply()

        loginState = loginState.copy(
            loading = false,
            loggedIn = true,
            displayName = displayName,
            error = null
        )
        refresh()
    }

    suspend fun refresh() {
        if (!loginState.loggedIn || loginState.password.isBlank()) return
        dataState = dataState.copy(loading = true, error = null)

        val dashboardResult = NextcloudApi.loadDashboard(
            loginState.server,
            loginState.username,
            loginState.password
        )
        val projectsResult = NextcloudApi.loadProjects(
            loginState.server,
            loginState.username,
            loginState.password
        )

        if (dashboardResult.isFailure || projectsResult.isFailure) {
            val message = dashboardResult.exceptionOrNull()?.message
                ?: projectsResult.exceptionOrNull()?.message
                ?: "Daten konnten nicht geladen werden."
            dataState = dataState.copy(loading = false, error = message)
            return
        }

        dataState = DataState(
            loading = false,
            dashboard = dashboardResult.getOrNull(),
            projects = projectsResult.getOrNull().orEmpty(),
            selectedProject = dataState.selectedProject,
            error = null
        )
    }
}

object NextcloudApi {
    private fun basicAuth(user: String, password: String): String {
        val raw = "$user:$password".toByteArray(StandardCharsets.UTF_8)
        return "Basic ${Base64.getEncoder().encodeToString(raw)}"
    }

    private suspend fun getJson(
        server: String,
        path: String,
        user: String,
        password: String,
        ocs: Boolean = false
    ): JSONObject = withContext(Dispatchers.IO) {
        val base = server.trim().trimEnd('/')
        require(base.startsWith("https://")) { "Bitte eine HTTPS-Serveradresse verwenden." }
        val connection = (URL("$base$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", basicAuth(user, password))
            if (ocs) setRequestProperty("OCS-APIRequest", "true")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = when (code) {
                    401 -> "Anmeldung abgelehnt. Bitte App-Passwort prüfen."
                    404 -> "Die NextERP-Mobile-API ist auf dem Server noch nicht installiert."
                    else -> "Serverfehler HTTP $code."
                }
                error(detail)
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun checkLogin(server: String, user: String, password: String): Result<String> = runCatching {
        val json = getJson(server, "/ocs/v2.php/cloud/user?format=json", user, password, ocs = true)
        json.optJSONObject("ocs")
            ?.optJSONObject("data")
            ?.optString("displayname")
            ?.takeIf { it.isNotBlank() }
            ?: user
    }

    suspend fun loadDashboard(server: String, user: String, password: String): Result<DashboardData> = runCatching {
        val json = getJson(server, "/index.php/apps/reinhardterp/api/mobile/v1/dashboard", user, password)
        val userJson = json.optJSONObject("user") ?: JSONObject()
        val cards = json.optJSONObject("cards") ?: JSONObject()
        val today = json.optJSONObject("todayProject")
        DashboardData(
            displayName = userJson.optString("displayName", user),
            role = userJson.optString("role", "monteur"),
            activeProjects = cards.optInt("activeProjects", 0),
            openTasks = cards.optInt("openTasks", 0),
            newDocuments = cards.optInt("newDocuments", 0),
            openReports = cards.optInt("openReports", 0),
            todayProject = today?.takeIf { it.length() > 0 }?.toProject()
        )
    }

    suspend fun loadProjects(server: String, user: String, password: String): Result<List<ProjectDto>> = runCatching {
        val json = getJson(server, "/index.php/apps/reinhardterp/api/mobile/v1/projects", user, password)
        val array = json.optJSONArray("projects") ?: JSONArray()
        buildList {
            for (i in 0 until array.length()) {
                add(array.getJSONObject(i).toProject())
            }
        }
    }

    private fun JSONObject.toProject(): ProjectDto = ProjectDto(
        id = optInt("id"),
        projectNo = optString("projectNo"),
        title = optString("title", "Projekt"),
        status = optString("status", "offen"),
        customerName = optString("customerName"),
        address = optString("address"),
        phone = optString("phone"),
        email = optString("email")
    )
}

@Composable
fun NextERPApp(vm: AppViewModel = viewModel()) {
    if (!vm.loginState.loggedIn) {
        LoginScreen(vm.loginState, vm)
    } else {
        AppShell(vm)
    }
}

@Composable
private fun LoginScreen(state: LoginState, vm: AppViewModel) {
    val scope = rememberCoroutineScope()
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(
                    Icons.Default.Handyman,
                    contentDescription = null,
                    modifier = Modifier.padding(18.dp).size(44.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.height(20.dp))
            Text("NextERP Mobile", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Alles, was du heute auf der Baustelle brauchst.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(
                state.server,
                vm::updateServer,
                label = { Text("Nextcloud-Server") },
                leadingIcon = { Icon(Icons.Default.Cloud, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                state.username,
                vm::updateUsername,
                label = { Text("Benutzer") },
                leadingIcon = { Icon(Icons.Default.Person, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                state.password,
                vm::updatePassword,
                label = { Text("App-Passwort") },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            state.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { scope.launch { vm.login() } },
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                if (state.loading) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Login, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Anmelden", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Es werden keine Testdaten angezeigt. Nach der Anmeldung lädt die App ausschließlich Daten aus NextERP.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun AppShell(vm: AppViewModel) {
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            AppHeader(
                name = vm.loginState.displayName,
                screen = vm.screen,
                onRefresh = { scope.launch { vm.refresh() } },
                onLogout = vm::logout
            )
        },
        bottomBar = { AppBottomBar(vm.screen, vm::navigate) }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (vm.screen) {
                Screen.TODAY -> TodayScreen(vm.dataState, vm::openProject, { scope.launch { vm.refresh() } })
                Screen.PROJECTS -> ProjectsScreen(vm.dataState, vm::openProject, { scope.launch { vm.refresh() } })
                Screen.PROJECT -> ProjectScreen(vm.dataState.selectedProject)
                Screen.SCANNER -> PlaceholderScreen("Scanner", "Dokumente und Barcodes erfassen", Icons.Default.QrCodeScanner)
                Screen.MATERIAL -> PlaceholderScreen("Material", "Noch keine Materialdaten vom Server", Icons.Default.Inventory2)
                Screen.DOCUMENTS -> PlaceholderScreen("Dokumente", "Noch keine Dokumentdaten vom Server", Icons.Default.Description)
                Screen.MORE -> MoreScreen(vm::logout)
            }
        }
    }
}

@Composable
private fun AppHeader(name: String, screen: Screen, onRefresh: () -> Unit, onLogout: () -> Unit) {
    val title = when (screen) {
        Screen.TODAY -> "Heute"
        Screen.PROJECTS -> "Projekte"
        Screen.PROJECT -> "Projekt"
        Screen.SCANNER -> "Scanner"
        Screen.MATERIAL -> "Material"
        Screen.DOCUMENTS -> "Dokumente"
        Screen.MORE -> "Mehr"
    }
    Surface(tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(name, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Aktualisieren") }
            IconButton(onClick = onLogout) { Icon(Icons.Default.Logout, "Abmelden") }
        }
    }
}

@Composable
private fun AppBottomBar(selected: Screen, navigate: (Screen) -> Unit) {
    val items = listOf(
        Triple(Screen.TODAY, Icons.Default.Home, "Heute"),
        Triple(Screen.PROJECTS, Icons.Default.Folder, "Projekte"),
        Triple(Screen.SCANNER, Icons.Default.QrCodeScanner, "Scanner"),
        Triple(Screen.MATERIAL, Icons.Default.Inventory2, "Material"),
        Triple(Screen.DOCUMENTS, Icons.Default.Description, "Dokumente"),
        Triple(Screen.MORE, Icons.Default.MoreHoriz, "Mehr")
    )
    NavigationBar {
        items.forEach { (screen, icon, label) ->
            NavigationBarItem(
                selected = selected == screen || (selected == Screen.PROJECT && screen == Screen.PROJECTS),
                onClick = { navigate(screen) },
                icon = { Icon(icon, label) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun TodayScreen(state: DataState, openProject: (ProjectDto) -> Unit, refresh: () -> Unit) {
    ScreenColumn {
        when {
            state.loading -> LoadingState()
            state.error != null -> ErrorState(state.error, refresh)
            state.dashboard == null -> EmptyState("Noch keine Dashboard-Daten")
            else -> {
                val data = state.dashboard
                Text("Guten Morgen", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                data.todayProject?.let { project ->
                    StatusCard(
                        title = project.title,
                        subtitle = listOf(project.customerName, project.address).filter { it.isNotBlank() }.joinToString(" · "),
                        icon = Icons.Default.HomeWork,
                        badge = project.status,
                        onClick = { openProject(project) }
                    )
                } ?: EmptyState("Für heute ist kein Projekt hinterlegt.")
                StatusCard("Aktive Projekte", "Im NextERP vorhanden", Icons.Default.Folder, data.activeProjects.toString(), {})
                StatusCard("Offene Aufgaben", "Vom Server gemeldet", Icons.Default.CheckCircle, data.openTasks.toString(), {})
                StatusCard("Neue Dokumente", "Vom Server gemeldet", Icons.Default.Description, data.newDocuments.toString(), {})
                StatusCard("Offene Rapporte", "Vom Server gemeldet", Icons.Default.EditNote, data.openReports.toString(), {})
            }
        }
    }
}

@Composable
private fun ProjectsScreen(state: DataState, openProject: (ProjectDto) -> Unit, refresh: () -> Unit) {
    ScreenColumn {
        Text("Aktuelle Projekte", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        when {
            state.loading -> LoadingState()
            state.error != null -> ErrorState(state.error, refresh)
            state.projects.isEmpty() -> EmptyState("Keine aktiven Projekte im NextERP gefunden.")
            else -> state.projects.forEach { project ->
                ProjectCard(project, { openProject(project) })
            }
        }
    }
}

@Composable
private fun ProjectScreen(project: ProjectDto?) {
    if (project == null) {
        ScreenColumn { EmptyState("Kein Projekt ausgewählt.") }
        return
    }
    val context = LocalContext.current
    ScreenColumn {
        Text(project.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (project.projectNo.isNotBlank()) Text(project.projectNo, style = MaterialTheme.typography.labelLarge)
        if (project.customerName.isNotBlank()) Text(project.customerName)
        if (project.address.isNotBlank()) Text(project.address)
        Spacer(Modifier.height(8.dp))
        AssistChip(onClick = {}, label = { Text(project.status) })
        Spacer(Modifier.height(12.dp))
        if (project.address.isNotBlank()) {
            PrimaryAction("Navigation", Icons.Default.Navigation) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(project.address)}")))
            }
        }
        if (project.phone.isNotBlank()) {
            PrimaryAction("Anrufen", Icons.Default.Phone) {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${project.phone}")))
            }
        }
        if (project.email.isNotBlank()) {
            PrimaryAction("E-Mail", Icons.Default.Email) {
                context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${project.email}")))
            }
        }
    }
}

@Composable
private fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
private fun StatusCard(title: String, subtitle: String, icon: ImageVector, badge: String?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, null, modifier = Modifier.padding(14.dp).size(26.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            badge?.let { AssistChip(onClick = {}, label = { Text(it) }) }
        }
    }
}

@Composable
private fun ProjectCard(project: ProjectDto, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, null)
                Spacer(Modifier.width(10.dp))
                Text(project.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (project.customerName.isNotBlank()) Text(project.customerName, style = MaterialTheme.typography.bodyMedium)
            if (project.address.isNotBlank()) Text(project.address, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            AssistChip(onClick = {}, label = { Text(project.status) })
        }
    }
}

@Composable
private fun PrimaryAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) {
        Icon(icon, null)
        Spacer(Modifier.width(10.dp))
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(message: String, refresh: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Daten konnten nicht geladen werden", fontWeight = FontWeight.Bold)
            Text(message)
            Button(onClick = refresh) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Erneut versuchen") }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Info, null, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(10.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, subtitle: String, icon: ImageVector) {
    ScreenColumn {
        Icon(icon, null, modifier = Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(subtitle)
    }
}

@Composable
private fun MoreScreen(logout: () -> Unit) {
    ScreenColumn {
        Text("Mehr", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("NextERP Mobile 0.9.0")
        OutlinedButton(onClick = logout, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Logout, null)
            Spacer(Modifier.width(8.dp))
            Text("Abmelden")
        }
    }
}
