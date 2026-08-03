package de.nexterp.mobile

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import de.nexterp.mobile.ui.theme.NextERPTheme
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NextERPTheme { NextERPApp() } }
    }
}

data class LoginState(
    val server: String = "https://cloud.kassel-net.de",
    val username: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val loggedIn: Boolean = false,
    val displayName: String = "",
    val error: String? = null
)

class LoginViewModel : ViewModel() {
    var state by mutableStateOf(LoginState())
        private set

    fun updateServer(value: String) { state = state.copy(server = value) }
    fun updateUsername(value: String) { state = state.copy(username = value) }
    fun updatePassword(value: String) { state = state.copy(password = value) }
    fun demo() { state = state.copy(loggedIn = true, displayName = "Demo-Monteur", error = null) }
    fun logout() { state = LoginState(server = state.server) }

    suspend fun login() {
        if (state.username.isBlank() || state.password.isBlank()) {
            state = state.copy(error = "Benutzer und Passwort eingeben.")
            return
        }
        state = state.copy(loading = true, error = null)
        val result = NextcloudAuth.check(state.server, state.username, state.password)
        state = if (result.isSuccess) {
            state.copy(loading = false, loggedIn = true, displayName = result.getOrNull().orEmpty())
        } else {
            state.copy(loading = false, error = result.exceptionOrNull()?.message ?: "Anmeldung fehlgeschlagen")
        }
    }
}

object NextcloudAuth {
    suspend fun check(server: String, user: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val base = server.trim().trimEnd('/')
            require(base.startsWith("https://")) { "Bitte eine HTTPS-Serveradresse verwenden." }
            val url = URL("$base/ocs/v2.php/cloud/user?format=json")
            val connection = (url.openConnection() as HttpURLConnection).apply {
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
fun NextERPApp(vm: LoginViewModel = viewModel()) {
    val state = vm.state
    if (state.loggedIn) Dashboard(state.displayName, vm::logout) else LoginScreen(state, vm)
}

@Composable
private fun LoginScreen(state: LoginState, vm: LoginViewModel) {
    val scope = rememberCoroutineScope()
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Handyman, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(18.dp))
            Text("NextERP Mobile", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Alles, was du heute auf der Baustelle brauchst.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(state.server, vm::updateServer, label = { Text("Nextcloud-Server") }, leadingIcon = { Icon(Icons.Default.Cloud, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(state.username, vm::updateUsername, label = { Text("Benutzer") }, leadingIcon = { Icon(Icons.Default.Person, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(state.password, vm::updatePassword, label = { Text("Passwort / App-Passwort") }, leadingIcon = { Icon(Icons.Default.Lock, null) }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            state.error?.let { Spacer(Modifier.height(12.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(20.dp))
            Button(onClick = { scope.launch { vm.login() } }, enabled = !state.loading, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) {
                if (state.loading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) else { Icon(Icons.Default.Login, null); Spacer(Modifier.width(8.dp)); Text("Anmelden") }
            }
            TextButton(onClick = vm::demo, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Demo öffnen") }
        }
    }
}

@Composable
private fun Dashboard(name: String, logout: () -> Unit) {
    Scaffold(
        topBar = {
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Heute", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(name, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = logout) { Icon(Icons.Default.Logout, "Abmelden") }
                }
            }
        },
        bottomBar = { NavigationBar { listOf(Icons.Default.Home to "Heute", Icons.Default.Folder to "Projekte", Icons.Default.QrCodeScanner to "Scanner", Icons.Default.Inventory2 to "Material", Icons.Default.Description to "Dokumente").forEachIndexed { i, item -> NavigationBarItem(selected = i == 0, onClick = {}, icon = { Icon(item.first, item.second) }, label = { Text(item.second) }) } } }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Guten Morgen", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            ActionCard("Aktuelles Projekt", "Wohnhaus Müller · Kassel", Icons.Default.HomeWork)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { Box(Modifier.weight(1f)) { ActionCard("Navigation", "Baustelle öffnen", Icons.Default.Navigation) }; Box(Modifier.weight(1f)) { ActionCard("Ankommen", "Zeit starten", Icons.Default.PlayCircle) } }
            ActionCard("Offene Aufgaben", "3 Aufgaben für heute", Icons.Default.CheckCircle)
            ActionCard("Neue Dokumente", "2 neue Pläne", Icons.Default.Description)
            ActionCard("Rapport", "Heute erfassen", Icons.Default.EditNote)
        }
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(onClick = {}, modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp), shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.fillMaxSize().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer) { Icon(icon, null, modifier = Modifier.padding(16.dp).size(30.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            Spacer(Modifier.width(16.dp))
            Column { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
