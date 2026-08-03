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
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NextERPTheme { NextERPApp() } }
    }
}

enum class Screen { TODAY, PROJECTS, PROJECT, MONTEUR, SCANNER, MATERIAL, DOCUMENTS, MORE }

data class LoginState(
    val server: String = "https://cloud.kassel-net.de",
    val username: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val loggedIn: Boolean = false,
    val displayName: String = "",
    val error: String? = null
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("nexterp_session", 0)

    var loginState by mutableStateOf(
        LoginState(
            server = prefs.getString("server", "https://cloud.kassel-net.de") ?: "https://cloud.kassel-net.de",
            username = prefs.getString("username", "") ?: "",
            loggedIn = prefs.getBoolean("loggedIn", false),
            displayName = prefs.getString("displayName", "") ?: ""
        )
    )
        private set

    var screen by mutableStateOf(Screen.TODAY)
        private set

    fun updateServer(value: String) { loginState = loginState.copy(server = value) }
    fun updateUsername(value: String) { loginState = loginState.copy(username = value) }
    fun updatePassword(value: String) { loginState = loginState.copy(password = value) }
    fun navigate(target: Screen) { screen = target }

    fun demo() {
        loginState = loginState.copy(loggedIn = true, displayName = "Demo-Monteur", error = null)
        screen = Screen.TODAY
    }

    fun logout() {
        prefs.edit().clear().apply()
        loginState = LoginState(server = loginState.server)
        screen = Screen.TODAY
    }

    suspend fun login() {
        if (loginState.username.isBlank() || loginState.password.isBlank()) {
            loginState = loginState.copy(error = "Benutzer und Passwort eingeben.")
            return
        }
        loginState = loginState.copy(loading = true, error = null)
        val result = NextcloudAuth.check(loginState.server, loginState.username, loginState.password)
        loginState = if (result.isSuccess) {
            val displayName = result.getOrNull().orEmpty()
            prefs.edit()
                .putString("server", loginState.server.trimEnd('/'))
                .putString("username", loginState.username)
                .putString("displayName", displayName)
                .putBoolean("loggedIn", true)
                .apply()
            loginState.copy(loading = false, loggedIn = true, displayName = displayName, password = "")
        } else {
            loginState.copy(loading = false, error = result.exceptionOrNull()?.message ?: "Anmeldung fehlgeschlagen")
        }
    }
}

object NextcloudAuth {
    suspend fun check(server: String, user: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val base = server.trim().trimEnd('/')
            require(base.startsWith("https://")) { "Bitte eine HTTPS-Serveradresse verwenden." }
            val connection = (URL("$base/ocs/v2.php/cloud/user?format=json").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 12_000
                setRequestProperty("OCS-APIRequest", "true")
                val credentials = Base64.getEncoder().encodeToString("$user:$password".toByteArray())
                setRequestProperty("Authorization", "Basic $credentials")
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) error("Nextcloud-Anmeldung fehlgeschlagen (HTTP $code).")
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                Regex("\\\"displayname\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
                    .find(body)?.groupValues?.get(1) ?: user
            } finally {
                connection.disconnect()
            }
        }
    }
}

@Composable
fun NextERPApp(vm: AppViewModel = viewModel()) {
    if (!vm.loginState.loggedIn) {
        LoginScreen(vm.loginState, vm)
        return
    }

    AppShell(vm)
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
                Icon(Icons.Default.Handyman, null, modifier = Modifier.padding(18.dp).size(44.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(20.dp))
            Text("NextERP Mobile", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Genau das, was du jetzt auf der Baustelle brauchst.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(state.server, vm::updateServer, label = { Text("Nextcloud-Server") }, leadingIcon = { Icon(Icons.Default.Cloud, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(state.username, vm::updateUsername, label = { Text("Benutzer") }, leadingIcon = { Icon(Icons.Default.Person, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(state.password, vm::updatePassword, label = { Text("Passwort / App-Passwort") }, leadingIcon = { Icon(Icons.Default.Lock, null) }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            state.error?.let { Spacer(Modifier.height(12.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(20.dp))
            Button(onClick = { scope.launch { vm.login() } }, enabled = !state.loading, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(18.dp)) {
                if (state.loading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else { Icon(Icons.Default.Login, null); Spacer(Modifier.width(8.dp)); Text("Anmelden", fontWeight = FontWeight.SemiBold) }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = vm::demo, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp)) { Text("Demo öffnen") }
        }
    }
}

@Composable
private fun AppShell(vm: AppViewModel) {
    Scaffold(
        topBar = { AppHeader(vm.loginState.displayName, vm.screen, vm::logout) },
        bottomBar = { AppBottomBar(vm.screen, vm::navigate) }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (vm.screen) {
                Screen.TODAY -> TodayScreen(vm::navigate)
                Screen.PROJECTS -> ProjectsScreen(vm::navigate)
                Screen.PROJECT -> ProjectScreen(vm::navigate)
                Screen.MONTEUR -> MonteurScreen()
                Screen.SCANNER -> PlaceholderScreen("Scanner", "Dokumente und Barcodes erfassen", Icons.Default.QrCodeScanner)
                Screen.MATERIAL -> MaterialScreen()
                Screen.DOCUMENTS -> DocumentsScreen()
                Screen.MORE -> MoreScreen(vm::logout)
            }
        }
    }
}

@Composable
private fun AppHeader(name: String, screen: Screen, logout: () -> Unit) {
    val title = when (screen) {
        Screen.TODAY -> "Heute"
        Screen.PROJECTS -> "Projekte"
        Screen.PROJECT -> "Projekt"
        Screen.MONTEUR -> "Monteurmodus"
        Screen.SCANNER -> "Scanner"
        Screen.MATERIAL -> "Material"
        Screen.DOCUMENTS -> "Dokumente"
        Screen.MORE -> "Mehr"
    }
    Surface(tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(name, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = logout) { Icon(Icons.Default.Logout, "Abmelden") }
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
            NavigationBarItem(selected = selected == screen || (selected == Screen.PROJECT && screen == Screen.PROJECTS), onClick = { navigate(screen) }, icon = { Icon(icon, label) }, label = { Text(label) })
        }
    }
}

@Composable
private fun TodayScreen(navigate: (Screen) -> Unit) {
    ScreenColumn {
        Text("Guten Morgen", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        StatusCard("Aktuelles Projekt", "Wohnhaus Müller · Kassel", Icons.Default.HomeWork, "Heute", { navigate(Screen.PROJECT) })
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { StatusCard("Navigation", "Baustelle", Icons.Default.Navigation, null, { navigate(Screen.PROJECT) }) }
            Box(Modifier.weight(1f)) { StatusCard("Ankommen", "Zeit starten", Icons.Default.PlayCircle, null, { navigate(Screen.MONTEUR) }) }
        }
        StatusCard("Offene Aufgaben", "3 Aufgaben für heute", Icons.Default.CheckCircle, "3", { navigate(Screen.PROJECT) })
        StatusCard("Neue Dokumente", "2 neue Pläne", Icons.Default.Description, "2", { navigate(Screen.DOCUMENTS) })
        StatusCard("Rapport", "Heute erfassen", Icons.Default.EditNote, null, { navigate(Screen.MONTEUR) })
    }
}

@Composable
private fun ProjectsScreen(navigate: (Screen) -> Unit) {
    ScreenColumn {
        Text("Aktuelle Projekte", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        ProjectCard("Wohnhaus Müller", "Kassel · Montage heute", "Montage", { navigate(Screen.PROJECT) })
        ProjectCard("Küche Schneider", "Baunatal · Donnerstag", "Vorbereitung", { navigate(Screen.PROJECT) })
        ProjectCard("Fenster Kita Sonnenschein", "Wolfhagen · nächste Woche", "Bestellt", { navigate(Screen.PROJECT) })
    }
}

@Composable
private fun ProjectScreen(navigate: (Screen) -> Unit) {
    val context = LocalContext.current
    ScreenColumn {
        Text("Wohnhaus Müller", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Montage · Kassel", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        StatusCard("Navigation", "Friedrichstraße 12, Kassel", Icons.Default.Navigation, null) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=Friedrichstraße+12,+Kassel")))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { SmallAction("Anrufen", Icons.Default.Phone) { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+49561123456"))) } }
            Box(Modifier.weight(1f)) { SmallAction("E-Mail", Icons.Default.Email) { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:kunde@example.de"))) } }
        }
        Text("Baustelle", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        ActionGridItem("Monteurmodus", Icons.Default.Engineering) { navigate(Screen.MONTEUR) }
        ActionGridItem("Dokumente", Icons.Default.Description) { navigate(Screen.DOCUMENTS) }
        ActionGridItem("Fotos", Icons.Default.PhotoCamera) { navigate(Screen.SCANNER) }
        ActionGridItem("Material", Icons.Default.Inventory2) { navigate(Screen.MATERIAL) }
        ActionGridItem("Rapport", Icons.Default.EditNote) { navigate(Screen.MONTEUR) }
    }
}

@Composable
private fun MonteurScreen() {
    var step by remember { mutableIntStateOf(0) }
    val steps = listOf("Navigation", "Ankommen", "Rapport", "Material", "Fotos", "Unterschrift", "Fertig")
    ScreenColumn {
        Text("Wohnhaus Müller", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Schritt ${step + 1} von ${steps.size}", color = MaterialTheme.colorScheme.primary)
        LinearProgressIndicator(progress = { (step + 1f) / steps.size }, modifier = Modifier.fillMaxWidth())
        steps.forEachIndexed { index, title ->
            Card(
                onClick = { if (index <= step + 1) step = index },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = if (index == step) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (index < step) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null)
                    Spacer(Modifier.width(14.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Button(onClick = { if (step < steps.lastIndex) step++ }, enabled = step < steps.lastIndex, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) {
            Text(if (step == steps.lastIndex) "Auftrag abgeschlossen" else "Weiter")
        }
    }
}

@Composable
private fun MaterialScreen() {
    ScreenColumn {
        Text("Material", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField("", {}, placeholder = { Text("Material suchen") }, leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth())
        StatusCard("Topfband 110°", "Lager: 34 Stück", Icons.Default.Inventory2, "34", {})
        StatusCard("Spanplattenschraube 4×50", "Lager: 8 Pakete", Icons.Default.Inventory2, "8", {})
        StatusCard("Montagekleber weiß", "Lager: 3 Kartuschen", Icons.Default.Inventory2, "3", {})
    }
}

@Composable
private fun DocumentsScreen() {
    ScreenColumn {
        Text("Dokumente", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        StatusCard("Montageplan Küche", "PDF · heute", Icons.Default.PictureAsPdf, "Neu", {})
        StatusCard("Auftragsbestätigung", "PDF · gestern", Icons.Default.Description, null, {})
        StatusCard("Grundriss Erdgeschoss", "PDF · 02.08.2026", Icons.Default.Map, null, {})
    }
}

@Composable
private fun MoreScreen(logout: () -> Unit) {
    ScreenColumn {
        Text("Mehr", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        ActionGridItem("Termine", Icons.Default.CalendarMonth) {}
        ActionGridItem("Aufgaben", Icons.Default.TaskAlt) {}
        ActionGridItem("Einstellungen", Icons.Default.Settings) {}
        ActionGridItem("Abmelden", Icons.Default.Logout, logout)
    }
}

@Composable
private fun PlaceholderScreen(title: String, subtitle: String, icon: ImageVector) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(subtitle)
        }
    }
}

@Composable
private fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
}

@Composable
private fun StatusCard(title: String, subtitle: String, icon: ImageVector, badge: String? = null, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 108.dp), shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, null, modifier = Modifier.padding(14.dp).size(30.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
            badge?.let { AssistChip(onClick = {}, label = { Text(it) }) }
        }
    }
}

@Composable
private fun ProjectCard(title: String, subtitle: String, status: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle)
            Spacer(Modifier.height(12.dp))
            AssistChip(onClick = {}, label = { Text(status) }, leadingIcon = { Icon(Icons.Default.Circle, null, Modifier.size(10.dp)) })
        }
    }
}

@Composable
private fun SmallAction(title: String, icon: ImageVector, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)) {
        Icon(icon, null)
        Spacer(Modifier.width(8.dp))
        Text(title)
    }
}

@Composable
private fun ActionGridItem(title: String, icon: ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}
