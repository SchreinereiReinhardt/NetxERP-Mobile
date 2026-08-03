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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.nexterp.mobile.ui.theme.NextERPTheme
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class Screen { TODAY, PROJECTS, PROJECT, TIME_ENTRY, SCANNER, MATERIAL, DOCUMENTS, MORE }

data class LoginState(
    val server: String = "https://cloud.kassel-net.de",
    val username: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val restoring: Boolean = true,
    val loggedIn: Boolean = false,
    val displayName: String = "",
    val role: String = "",
    val error: String? = null
)

data class DashboardData(
    val projectsToday: Int = 0,
    val tasks: Int = 0,
    val documents: Int = 0,
    val reportsOpen: Int = 0,
    val todayHours: Double = 0.0,
    val recentProjects: List<ProjectDto> = emptyList()
)

data class ProjectDto(
    val id: Int,
    val projectNo: String,
    val projectName: String,
    val customer: String,
    val status: String,
    val startDate: String?,
    val dueDate: String?,
    val address: String,
    val contactName: String,
    val phone: String,
    val email: String,
    val color: String,
    val progress: Int
)

data class DataState(
    val loading: Boolean = false,
    val dashboard: DashboardData? = null,
    val projects: List<ProjectDto> = emptyList(),
    val selectedProject: ProjectDto? = null,
    val error: String? = null
)



data class TimeEntryState(
    val workDate: String = LocalDate.now().toString(),
    val fromTime: String = "08:00",
    val toTime: String = "16:30",
    val breakMinutes: String = "30",
    val activity: String = "Montage",
    val note: String = "",
    val saving: Boolean = false,
    val success: String? = null,
    val error: String? = null
)

data class SessionData(
    val accessToken: String,
    val refreshToken: String,
    val displayName: String,
    val username: String,
    val role: String,
    val expiresIn: Int
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NextERPTheme { NextERPApp() } }
    }
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("nexterp_session", 0)

    private var accessToken: String = prefs.getString("access_token", "").orEmpty()
    private var refreshToken: String = prefs.getString("refresh_token", "").orEmpty()

    var loginState by mutableStateOf(
        LoginState(
            server = prefs.getString("server", "https://cloud.kassel-net.de") ?: "https://cloud.kassel-net.de",
            username = prefs.getString("username", "") ?: "",
            displayName = prefs.getString("display_name", "") ?: "",
            role = prefs.getString("role", "") ?: "",
            restoring = true
        )
    )
        private set

    var dataState by mutableStateOf(DataState())
        private set

    var screen by mutableStateOf(Screen.TODAY)
        private set

    var timeEntryState by mutableStateOf(TimeEntryState())
        private set

    init {
        viewModelScope.launch { restoreSession() }
    }

    fun updateServer(value: String) { loginState = loginState.copy(server = value) }
    fun updateUsername(value: String) { loginState = loginState.copy(username = value) }
    fun updatePassword(value: String) { loginState = loginState.copy(password = value) }
    fun navigate(target: Screen) { screen = target }

    fun openProject(project: ProjectDto) {
        dataState = dataState.copy(selectedProject = project)
        screen = Screen.PROJECT
    }

    fun openTimeEntry() {
        timeEntryState = TimeEntryState()
        screen = Screen.TIME_ENTRY
    }

    fun updateTimeDate(value: String) { timeEntryState = timeEntryState.copy(workDate = value, success = null, error = null) }
    fun updateFromTime(value: String) { timeEntryState = timeEntryState.copy(fromTime = value, success = null, error = null) }
    fun updateToTime(value: String) { timeEntryState = timeEntryState.copy(toTime = value, success = null, error = null) }
    fun updateBreakMinutes(value: String) { timeEntryState = timeEntryState.copy(breakMinutes = value.filter(Char::isDigit), success = null, error = null) }
    fun updateActivity(value: String) { timeEntryState = timeEntryState.copy(activity = value, success = null, error = null) }
    fun updateTimeNote(value: String) { timeEntryState = timeEntryState.copy(note = value, success = null, error = null) }

    fun saveTimeEntry() {
        val project = dataState.selectedProject ?: run {
            timeEntryState = timeEntryState.copy(error = "Kein Projekt ausgewählt.")
            return
        }
        val hours = calculateHours(timeEntryState.fromTime, timeEntryState.toTime, timeEntryState.breakMinutes)
        if (hours == null || hours <= 0.0) {
            timeEntryState = timeEntryState.copy(error = "Bitte gültige Zeiten und Pause eingeben.")
            return
        }
        if (!timeEntryState.workDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            timeEntryState = timeEntryState.copy(error = "Datum bitte als JJJJ-MM-TT eingeben.")
            return
        }
        viewModelScope.launch {
            timeEntryState = timeEntryState.copy(saving = true, success = null, error = null)
            val details = buildString {
                append(timeEntryState.activity.ifBlank { "Arbeit" })
                append(" · ").append(timeEntryState.fromTime).append("–").append(timeEntryState.toTime)
                append(" · Pause ").append(timeEntryState.breakMinutes.ifBlank { "0" }).append(" Min.")
                if (timeEntryState.note.isNotBlank()) append(" · ").append(timeEntryState.note.trim())
            }
            val result = authorizedRequest { token ->
                NextErpApi.createTime(loginState.server, token, project.id, timeEntryState.workDate, hours, details)
            }
            if (result.isSuccess) {
                timeEntryState = timeEntryState.copy(saving = false, success = "${formatHours(hours)} Stunden gespeichert.", error = null)
                refreshInternal()
            } else {
                timeEntryState = timeEntryState.copy(saving = false, error = result.exceptionOrNull()?.message ?: "Zeit konnte nicht gespeichert werden.")
            }
        }
    }

    private suspend fun restoreSession() {
        if (accessToken.isBlank() && refreshToken.isBlank()) {
            loginState = loginState.copy(restoring = false)
            return
        }
        loginState = loginState.copy(loading = true, restoring = true, error = null)
        val bootstrap = authorizedRequest { token -> NextErpApi.bootstrap(loginState.server, token) }
        if (bootstrap.isFailure) {
            clearSession()
            loginState = loginState.copy(loading = false, restoring = false, loggedIn = false)
            return
        }
        val data = bootstrap.getOrThrow()
        val user = data.optJSONObject("user") ?: JSONObject()
        loginState = loginState.copy(
            loading = false,
            restoring = false,
            loggedIn = true,
            displayName = user.optString("displayName", loginState.displayName),
            username = user.optString("username", loginState.username),
            role = data.optString("role", loginState.role),
            error = null
        )
        refresh()
    }

    fun login() {
        viewModelScope.launch { loginInternal() }
    }

    private suspend fun loginInternal() {
        if (loginState.server.isBlank() || loginState.username.isBlank() || loginState.password.isBlank()) {
            loginState = loginState.copy(error = "Server, Benutzer und Passwort eingeben.")
            return
        }
        loginState = loginState.copy(loading = true, error = null)
        val result = NextErpApi.login(
            loginState.server,
            loginState.username.trim(),
            loginState.password,
            "NextERP Android"
        )
        if (result.isFailure) {
            loginState = loginState.copy(
                loading = false,
                password = "",
                error = result.exceptionOrNull()?.message ?: "Anmeldung fehlgeschlagen."
            )
            return
        }
        val session = result.getOrThrow()
        accessToken = session.accessToken
        refreshToken = session.refreshToken
        prefs.edit()
            .putString("server", normalizeServer(loginState.server))
            .putString("username", session.username)
            .putString("display_name", session.displayName)
            .putString("role", session.role)
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .apply()

        loginState = loginState.copy(
            server = normalizeServer(loginState.server),
            username = session.username,
            password = "",
            loading = false,
            restoring = false,
            loggedIn = true,
            displayName = session.displayName,
            role = session.role,
            error = null
        )
        refresh()
    }

    fun logout() {
        viewModelScope.launch {
            if (accessToken.isNotBlank()) NextErpApi.logout(loginState.server, accessToken)
            clearSession()
            dataState = DataState()
            screen = Screen.TODAY
            loginState = LoginState(server = loginState.server, username = loginState.username, restoring = false)
        }
    }

    fun refresh() {
        viewModelScope.launch { refreshInternal() }
    }

    private suspend fun refreshInternal() {
        if (!loginState.loggedIn || accessToken.isBlank()) return
        dataState = dataState.copy(loading = true, error = null)
        val dashboardResult = authorizedRequest { token -> NextErpApi.dashboard(loginState.server, token) }
        val projectsResult = authorizedRequest { token -> NextErpApi.projects(loginState.server, token) }

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

    private suspend fun <T> authorizedRequest(block: suspend (String) -> Result<T>): Result<T> {
        var result = block(accessToken)
        if (result.exceptionOrNull() is ApiUnauthorizedException && refreshToken.isNotBlank()) {
            val refreshed = NextErpApi.refresh(loginState.server, refreshToken)
            if (refreshed.isSuccess) {
                val session = refreshed.getOrThrow()
                accessToken = session.accessToken
                refreshToken = session.refreshToken
                prefs.edit()
                    .putString("access_token", accessToken)
                    .putString("refresh_token", refreshToken)
                    .putString("display_name", session.displayName)
                    .putString("role", session.role)
                    .apply()
                result = block(accessToken)
            }
        }
        return result
    }

    private fun clearSession() {
        accessToken = ""
        refreshToken = ""
        prefs.edit().remove("access_token").remove("refresh_token").remove("display_name").remove("role").apply()
    }

    private fun normalizeServer(server: String): String = server.trim().trimEnd('/')
}

class ApiUnauthorizedException(message: String) : RuntimeException(message)

object NextErpApi {
    private fun apiBase(server: String): String {
        val clean = server.trim().trimEnd('/')
        require(clean.startsWith("https://")) { "Bitte eine HTTPS-Serveradresse verwenden." }
        return "$clean/index.php/apps/reinhardterp/api/mobile/v1"
    }

    private suspend fun request(
        server: String,
        path: String,
        method: String = "GET",
        token: String? = null,
        body: JSONObject? = null
    ): Any = withContext(Dispatchers.IO) {
        val connection = (URL(apiBase(server) + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            token?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                doOutput = true
                outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            }
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (code == 401) throw ApiUnauthorizedException(apiMessage(json, "Anmeldung abgelaufen."))
            if (code !in 200..299) error(apiMessage(json, "Serverfehler HTTP $code."))
            if (!json.optBoolean("success", false)) error(apiMessage(json, "NextERP meldet einen Fehler."))
            json.opt("data") ?: JSONObject.NULL
        } finally {
            connection.disconnect()
        }
    }

    suspend fun login(server: String, username: String, password: String, deviceName: String): Result<SessionData> = runCatching {
        val data = request(
            server,
            "/login",
            "POST",
            body = JSONObject()
                .put("username", username)
                .put("password", password)
                .put("deviceName", deviceName)
        ) as? JSONObject ?: error("Ungültige Login-Antwort.")
        data.toSession()
    }

    suspend fun refresh(server: String, refreshToken: String): Result<SessionData> = runCatching {
        (request(server, "/refresh", "POST", body = JSONObject().put("refreshToken", refreshToken)) as? JSONObject
            ?: error("Ungültige Refresh-Antwort.")).toSession()
    }

    suspend fun logout(server: String, token: String): Result<Unit> = runCatching {
        request(server, "/logout", "POST", token)
        Unit
    }

    suspend fun bootstrap(server: String, token: String): Result<JSONObject> = runCatching {
        request(server, "/bootstrap", token = token) as? JSONObject ?: error("Ungültige Bootstrap-Antwort.")
    }

    suspend fun dashboard(server: String, token: String): Result<DashboardData> = runCatching {
        val data = request(server, "/dashboard", token = token) as? JSONObject ?: error("Ungültige Dashboard-Antwort.")
        DashboardData(
            projectsToday = data.optInt("projectsToday", 0),
            tasks = data.optInt("tasks", 0),
            documents = data.optInt("documents", 0),
            reportsOpen = data.optInt("reportsOpen", 0),
            todayHours = data.optDouble("todayHours", 0.0),
            recentProjects = data.optJSONArray("recentProjects").toProjects()
        )
    }

    suspend fun projects(server: String, token: String): Result<List<ProjectDto>> = runCatching {
        when (val data = request(server, "/projects", token = token)) {
            is org.json.JSONArray -> data.toProjects()
            is JSONObject -> data.toProjectsFromUnknownShape()
            else -> emptyList()
        }
    }

    suspend fun createTime(
        server: String,
        token: String,
        projectId: Int,
        workDate: String,
        hours: Double,
        activity: String
    ): Result<JSONObject> = runCatching {
        request(
            server = server,
            path = "/time",
            method = "POST",
            token = token,
            body = JSONObject()
                .put("projectId", projectId)
                .put("workDate", workDate)
                .put("hours", hours)
                .put("activity", activity)
        ) as? JSONObject ?: error("Ungültige Antwort der Zeiterfassung.")
    }

    private fun JSONObject.toSession(): SessionData {
        val user = optJSONObject("user") ?: JSONObject()
        return SessionData(
            accessToken = optString("accessToken").also { require(it.isNotBlank()) { "Access-Token fehlt." } },
            refreshToken = optString("refreshToken").also { require(it.isNotBlank()) { "Refresh-Token fehlt." } },
            displayName = user.optString("displayName", user.optString("username")),
            username = user.optString("username", user.optString("id")),
            role = optString("role", "employee"),
            expiresIn = optInt("expiresIn", 3600)
        )
    }

    private fun JSONObject.toProjectsFromUnknownShape(): List<ProjectDto> {
        // Der aktuelle Server liefert direkt ein JSON-Array in data. Einige Hotfix-Stände
        // liefern alternativ {projects:[...]}. Beide Varianten werden unterstützt.
        val wrapped = optJSONArray("projects")
        if (wrapped != null) return wrapped.toProjects()
        val indexed = mutableListOf<ProjectDto>()
        var index = 0
        while (has(index.toString())) {
            optJSONObject(index.toString())?.let { indexed += it.toProject() }
            index++
        }
        return indexed
    }

    private fun org.json.JSONArray?.toProjects(): List<ProjectDto> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) optJSONObject(i)?.let { add(it.toProject()) }
        }
    }

    private fun JSONObject.toProject(): ProjectDto = ProjectDto(
        id = optInt("id"),
        projectNo = optString("projectNo"),
        projectName = optString("projectName", optString("title", "Projekt")),
        customer = optString("customer", optString("customerName")),
        status = optString("status", "offen"),
        startDate = optString("startDate").takeIf { it.isNotBlank() },
        dueDate = optString("dueDate").takeIf { it.isNotBlank() },
        address = optString("address"),
        contactName = optString("contactName"),
        phone = optString("phone"),
        email = optString("email"),
        color = optString("color", "#546E7A"),
        progress = optInt("progress", 0)
    )

    private fun apiMessage(json: JSONObject, fallback: String): String {
        val message = json.optString("message")
        if (message.isNotBlank()) return message
        val errors = json.optJSONArray("errors")
        return if (errors != null && errors.length() > 0) errors.optString(0, fallback) else fallback
    }
}

@Composable
fun NextERPApp(vm: AppViewModel = viewModel()) {
    when {
        vm.loginState.restoring -> SplashScreen()
        !vm.loginState.loggedIn -> LoginScreen(vm.loginState, vm)
        else -> AppShell(vm)
    }
}

@Composable
private fun SplashScreen() {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Handyman, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(18.dp))
            Text("NextERP Mobile", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun LoginScreen(state: LoginState, vm: AppViewModel) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Default.Handyman, null, Modifier.padding(18.dp).size(44.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(20.dp))
            Text("NextERP Mobile", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Deine Baustelle. Genau jetzt.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(state.server, vm::updateServer, label = { Text("Nextcloud-Server") }, leadingIcon = { Icon(Icons.Default.Cloud, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(state.username, vm::updateUsername, label = { Text("Benutzer") }, leadingIcon = { Icon(Icons.Default.Person, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(state.password, vm::updatePassword, label = { Text("Passwort oder App-Passwort") }, leadingIcon = { Icon(Icons.Default.Lock, null) }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            state.error?.let { Spacer(Modifier.height(12.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(20.dp))
            Button(onClick = vm::login, enabled = !state.loading, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(18.dp)) {
                if (state.loading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else { Icon(Icons.Default.Login, null); Spacer(Modifier.width(8.dp)); Text("Anmelden", fontWeight = FontWeight.SemiBold) }
            }
            Spacer(Modifier.height(10.dp))
            Text("Anmeldung direkt an der NextERP Mobile API v1. Keine Testdaten.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AppShell(vm: AppViewModel) {
    Scaffold(
        topBar = { AppHeader(vm.loginState.displayName, vm.loginState.role, vm.screen, vm::refresh, vm::logout) },
        bottomBar = { AppBottomBar(vm.screen, vm::navigate) }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (vm.screen) {
                Screen.TODAY -> TodayScreen(vm.dataState, vm::openProject, vm::refresh)
                Screen.PROJECTS -> ProjectsScreen(vm.dataState, vm::openProject, vm::refresh)
                Screen.PROJECT -> ProjectScreen(vm.dataState.selectedProject, vm::openTimeEntry)
                Screen.TIME_ENTRY -> TimeEntryScreen(vm.dataState.selectedProject, vm.timeEntryState, vm)
                Screen.SCANNER -> PlaceholderScreen("Scanner", "Dokumente und QR-Codes folgen im nächsten Ausbau.", Icons.Default.QrCodeScanner)
                Screen.MATERIAL -> PlaceholderScreen("Material", "Die Materialsuche wird als Nächstes an /material angebunden.", Icons.Default.Inventory2)
                Screen.DOCUMENTS -> PlaceholderScreen("Dokumente", "Projektunterlagen werden im nächsten Schritt geladen.", Icons.Default.Description)
                Screen.MORE -> MoreScreen(vm.loginState, vm::logout)
            }
        }
    }
}

@Composable
private fun AppHeader(name: String, role: String, screen: Screen, onRefresh: () -> Unit, onLogout: () -> Unit) {
    val title = when (screen) {
        Screen.TODAY -> "Heute"; Screen.PROJECTS -> "Projekte"; Screen.PROJECT -> "Projekt"; Screen.TIME_ENTRY -> "Zeiten"; Screen.SCANNER -> "Scanner"; Screen.MATERIAL -> "Material"; Screen.DOCUMENTS -> "Dokumente"; Screen.MORE -> "Mehr"
    }
    Surface(tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(listOf(name, roleLabel(role)).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Aktualisieren") }
            IconButton(onClick = onLogout) { Icon(Icons.Default.Logout, "Abmelden") }
        }
    }
}

@Composable
private fun AppBottomBar(selected: Screen, navigate: (Screen) -> Unit) {
    val items = listOf(
        Triple(Screen.TODAY, Icons.Default.Home, "Heute"), Triple(Screen.PROJECTS, Icons.Default.Folder, "Projekte"), Triple(Screen.SCANNER, Icons.Default.QrCodeScanner, "Scanner"), Triple(Screen.MATERIAL, Icons.Default.Inventory2, "Material"), Triple(Screen.DOCUMENTS, Icons.Default.Description, "Dokumente"), Triple(Screen.MORE, Icons.Default.MoreHoriz, "Mehr")
    )
    NavigationBar {
        items.forEach { (screen, icon, label) ->
            NavigationBarItem(selected = selected == screen || (selected == Screen.PROJECT && screen == Screen.PROJECTS), onClick = { navigate(screen) }, icon = { Icon(icon, label) }, label = { Text(label) })
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
                Text("Heute", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                MetricGrid(data)
                Text("Aktuelle Projekte", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (data.recentProjects.isEmpty()) EmptyState("Keine aktiven Projekte gefunden.")
                else data.recentProjects.take(4).forEach { project -> ProjectCard(project) { openProject(project) } }
            }
        }
    }
}

@Composable
private fun MetricGrid(data: DashboardData) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Projekte heute", data.projectsToday.toString(), Icons.Default.HomeWork, Modifier.weight(1f))
            MetricCard("Aufgaben", data.tasks.toString(), Icons.Default.TaskAlt, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Dokumente", data.documents.toString(), Icons.Default.Description, Modifier.weight(1f))
            MetricCard("Rapporte", data.reportsOpen.toString(), Icons.Default.EditNote, Modifier.weight(1f))
        }
        MetricCard("Stunden heute", String.format(java.util.Locale.GERMANY, "%.1f h", data.todayHours), Icons.Default.Schedule, Modifier.fillMaxWidth())
    }
}

@Composable
private fun MetricCard(title: String, value: String, icon: ImageVector, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.bodySmall)
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
            else -> state.projects.forEach { project -> ProjectCard(project) { openProject(project) } }
        }
    }
}

@Composable
private fun ProjectScreen(project: ProjectDto?, openTimeEntry: () -> Unit) {
    if (project == null) { ScreenColumn { EmptyState("Kein Projekt ausgewählt.") }; return }
    val context = LocalContext.current
    ScreenColumn {
        Text(project.projectName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (project.projectNo.isNotBlank()) Text(project.projectNo, style = MaterialTheme.typography.labelLarge)
        if (project.customer.isNotBlank()) Text(project.customer)
        if (project.address.isNotBlank()) Text(project.address)
        LinearProgressIndicator(progress = { project.progress.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth())
        AssistChip(onClick = {}, label = { Text(project.status) })
        if (project.address.isNotBlank()) PrimaryAction("Navigation", Icons.Default.Navigation) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(project.address)}"))) }
        if (project.phone.isNotBlank()) PrimaryAction("Anrufen", Icons.Default.Phone) { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${project.phone}"))) }
        if (project.email.isNotBlank()) PrimaryAction("E-Mail", Icons.Default.Email) { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${project.email}"))) }
        PrimaryAction("Dokumente", Icons.Default.Description) {}
        PrimaryAction("Fotos", Icons.Default.PhotoCamera) {}
        PrimaryAction("Material", Icons.Default.Inventory2) {}
        PrimaryAction("Zeiten erfassen", Icons.Default.Schedule, openTimeEntry)
        PrimaryAction("Rapport", Icons.Default.EditNote) {}
    }
}

@Composable
private fun TimeEntryScreen(project: ProjectDto?, state: TimeEntryState, vm: AppViewModel) {
    if (project == null) { ScreenColumn { EmptyState("Kein Projekt ausgewählt.") }; return }
    val hours = calculateHours(state.fromTime, state.toTime, state.breakMinutes)
    val activities = listOf("Montage", "Service", "Reparatur", "Aufmaß", "Werkstatt", "Planung", "Anfahrt", "Sonstiges")

    ScreenColumn {
        Text(project.projectName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (project.projectNo.isNotBlank()) Text(project.projectNo, style = MaterialTheme.typography.labelLarge)

        Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Arbeitszeit eintragen", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = state.workDate,
                    onValueChange = vm::updateTimeDate,
                    label = { Text("Datum (JJJJ-MM-TT)") },
                    leadingIcon = { Icon(Icons.Default.CalendarMonth, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = state.fromTime,
                        onValueChange = vm::updateFromTime,
                        label = { Text("Von") },
                        placeholder = { Text("08:00") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = state.toTime,
                        onValueChange = vm::updateToTime,
                        label = { Text("Bis") },
                        placeholder = { Text("16:30") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = state.breakMinutes,
                    onValueChange = vm::updateBreakMinutes,
                    label = { Text("Pause in Minuten") },
                    leadingIcon = { Icon(Icons.Default.Coffee, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Tätigkeit", fontWeight = FontWeight.SemiBold)
                activities.chunked(2).forEach { rowItems ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItems.forEach { activity ->
                            FilterChip(
                                selected = state.activity == activity,
                                onClick = { vm.updateActivity(activity) },
                                label = { Text(activity) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                    }
                }

                OutlinedTextField(
                    value = state.note,
                    onValueChange = vm::updateTimeNote,
                    label = { Text("Notiz") },
                    placeholder = { Text("Was wurde gemacht?") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Arbeitszeit", style = MaterialTheme.typography.labelLarge)
                    Text(hours?.let(::formatHours)?.plus(" Stunden") ?: "–", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }
        state.success?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }

        Button(
            onClick = vm::saveTimeEntry,
            enabled = !state.saving && hours != null && hours > 0.0,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            if (state.saving) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            else { Icon(Icons.Default.Save, null); Spacer(Modifier.width(10.dp)); Text("Zeit speichern", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ProjectCard(project: ProjectDto, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp))
                Icon(Icons.Default.Folder, null, tint = parseColor(project.color))
                Spacer(Modifier.width(10.dp))
                Text(project.projectName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (project.projectNo.isNotBlank()) Text(project.projectNo, style = MaterialTheme.typography.labelSmall)
            if (project.customer.isNotBlank()) Text(project.customer)
            if (project.address.isNotBlank()) Text(project.address, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { project.progress.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            AssistChip(onClick = {}, label = { Text(project.status) })
        }
    }
}

@Composable
private fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun PrimaryAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) { Icon(icon, null); Spacer(Modifier.width(10.dp)); Text(label, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun LoadingState() { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }

@Composable
private fun ErrorState(message: String, refresh: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Daten konnten nicht geladen werden", fontWeight = FontWeight.Bold); Text(message)
            Button(onClick = refresh) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Erneut versuchen") }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Info, null, Modifier.size(36.dp)); Spacer(Modifier.height(10.dp)); Text(message) } }
}

@Composable
private fun PlaceholderScreen(title: String, subtitle: String, icon: ImageVector) {
    ScreenColumn { Icon(icon, null, Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary); Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(subtitle) }
}

@Composable
private fun MoreScreen(login: LoginState, logout: () -> Unit) {
    ScreenColumn {
        Text("Mehr", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(login.displayName, fontWeight = FontWeight.Bold)
        Text(roleLabel(login.role))
        Text("NextERP Mobile 1.2.0 · API v1")
        OutlinedButton(onClick = logout, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Logout, null); Spacer(Modifier.width(8.dp)); Text("Abmelden") }
    }
}

private fun calculateHours(from: String, to: String, breakMinutes: String): Double? {
    fun minutes(value: String): Int? {
        val parts = value.trim().split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }
    val start = minutes(from) ?: return null
    var end = minutes(to) ?: return null
    if (end < start) end += 24 * 60
    val pause = breakMinutes.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val net = end - start - pause
    return if (net > 0) net / 60.0 else null
}

private fun formatHours(hours: Double): String = String.format(java.util.Locale.GERMANY, "%.2f", hours)

private fun roleLabel(role: String): String = when (role.lowercase()) {
    "administrator", "admin" -> "Administrator"; "office" -> "Büro"; "manager" -> "Projektleiter"; "employee" -> "Monteur"; else -> role
}

private fun parseColor(value: String): Color = runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(Color(0xFF546E7A))
