package de.nexterp.mobile

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.Paint
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.nexterp.mobile.ui.theme.NextERPTheme
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class Screen { TODAY, PROJECTS, PROJECT, TIME_ENTRY, REPORT, SCANNER, MATERIAL, DOCUMENTS, MORE }

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

data class MaterialDto(
    val id: Int,
    val articleNo: String,
    val name: String,
    val barcode: String,
    val unit: String,
    val stockQuantity: Double,
    val minimumStock: Double,
    val salePrice: Double
)

data class DocumentDto(
    val id: Int,
    val projectId: Int,
    val documentType: String,
    val fileName: String,
    val filePath: String,
    val mimeType: String,
    val status: String,
    val createdBy: String,
    val createdAt: String
)

data class TimeMaterialPosition(
    val materialId: Int? = null,
    val articleNo: String = "",
    val name: String = "",
    val quantity: String = "1",
    val unit: String = "Stk.",
    val unitPrice: Double = 0.0
)


data class ReportHourPosition(
    val workDate: String = LocalDate.now().toString(),
    val fromTime: String = "08:00",
    val toTime: String = "16:30",
    val breakMinutes: String = "30",
    val activity: String = "Montage"
)

data class ReportPhoto(
    val localUri: String,
    val fileName: String,
    val category: String,
    val uploadedDocumentId: Int? = null,
    val uploading: Boolean = false,
    val error: String? = null
)

data class ImportableTimeDto(
    val id: Int,
    val workDate: String,
    val userId: String,
    val displayName: String,
    val hours: Double,
    val activity: String,
    val materialCount: Int,
    val materialTotal: Double
)

data class ReportState(
    val reportDate: String = LocalDate.now().toString(),
    val title: String = "Arbeitsrapport",
    val notes: String = "",
    val customerNote: String = "",
    val customerSignedBy: String = "",
    val customerSignatureData: String = "",
    val technicianSignedBy: String = "",
    val technicianSignatureData: String = "",
    val photos: List<ReportPhoto> = emptyList(),
    val invoiceReady: Boolean = false,
    val hours: List<ReportHourPosition> = listOf(ReportHourPosition()),
    val materials: List<TimeMaterialPosition> = listOf(TimeMaterialPosition()),
    val availableTimes: List<ImportableTimeDto> = emptyList(),
    val selectedTimeIds: Set<Int> = emptySet(),
    val loadingTimes: Boolean = false,
    val saving: Boolean = false,
    val success: String? = null,
    val error: String? = null
)

data class DataState(
    val loading: Boolean = false,
    val dashboard: DashboardData? = null,
    val projects: List<ProjectDto> = emptyList(),
    val materials: List<MaterialDto> = emptyList(),
    val materialsLoading: Boolean = false,
    val documents: List<DocumentDto> = emptyList(),
    val documentsLoading: Boolean = false,
    val documentsProjectId: Int? = null,
    val documentsError: String? = null,
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
    val materials: List<TimeMaterialPosition> = listOf(TimeMaterialPosition()),
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

    var reportState by mutableStateOf(ReportState())
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

    fun openProjectDocuments(project: ProjectDto) {
        dataState = dataState.copy(selectedProject = project)
        screen = Screen.DOCUMENTS
        loadProjectDocuments(project.id)
    }

    fun loadProjectDocuments(projectId: Int) {
        viewModelScope.launch {
            dataState = dataState.copy(
                documentsLoading = true,
                documentsProjectId = projectId,
                documentsError = null
            )
            val result = authorizedRequest { token ->
                NextErpApi.projectDocuments(loginState.server, token, projectId)
            }
            dataState = if (result.isSuccess) {
                dataState.copy(
                    documents = result.getOrNull().orEmpty(),
                    documentsLoading = false,
                    documentsProjectId = projectId,
                    documentsError = null
                )
            } else {
                dataState.copy(
                    documents = emptyList(),
                    documentsLoading = false,
                    documentsProjectId = projectId,
                    documentsError = result.exceptionOrNull()?.message
                        ?: "Dokumente konnten nicht geladen werden."
                )
            }
        }
    }

    fun openTimeEntry() {
        timeEntryState = TimeEntryState()
        screen = Screen.TIME_ENTRY
        loadMaterials("")
    }

    fun openReport() {
        val project = dataState.selectedProject ?: return
        reportState = ReportState(
            title = "Arbeitsrapport ${project.projectNo}",
            technicianSignedBy = loginState.displayName.ifBlank { loginState.username }
        )
        screen = Screen.REPORT
        loadMaterials("")
        loadImportableTimes(project.id)
    }

    fun updateReportDate(value: String) { reportState = reportState.copy(reportDate = value, success = null, error = null) }
    fun updateReportTitle(value: String) { reportState = reportState.copy(title = value, success = null, error = null) }
    fun updateReportNotes(value: String) { reportState = reportState.copy(notes = value, success = null, error = null) }
    fun updateReportCustomerNote(value: String) { reportState = reportState.copy(customerNote = value, success = null, error = null) }
    fun updateCustomerSignedBy(value: String) { reportState = reportState.copy(customerSignedBy = value, success = null, error = null) }
    fun updateCustomerSignature(value: String) { reportState = reportState.copy(customerSignatureData = value, success = null, error = null) }
    fun updateTechnicianSignedBy(value: String) { reportState = reportState.copy(technicianSignedBy = value, success = null, error = null) }
    fun updateTechnicianSignature(value: String) { reportState = reportState.copy(technicianSignatureData = value, success = null, error = null) }
    fun updateReportInvoiceReady(value: Boolean) { reportState = reportState.copy(invoiceReady = value, success = null, error = null) }

    fun addReportPhoto(category: String, uri: String, fileName: String) {
        reportState = reportState.copy(
            photos = reportState.photos + ReportPhoto(
                localUri = uri,
                fileName = fileName,
                category = category
            ),
            success = null,
            error = null
        )
    }

    fun removeReportPhoto(index: Int) {
        val rows = reportState.photos.toMutableList()
        if (index !in rows.indices) return
        val removed = rows.removeAt(index)
        runCatching {
            val uri = Uri.parse(removed.localUri)
            if (uri.scheme == "file") File(uri.path.orEmpty()).delete()
            else getApplication<Application>().contentResolver.delete(uri, null, null)
        }
        reportState = reportState.copy(photos = rows, success = null, error = null)
    }

    fun addReportHour() { reportState = reportState.copy(hours = reportState.hours + ReportHourPosition(), success = null, error = null) }
    fun removeReportHour(index: Int) {
        val rows = reportState.hours.toMutableList()
        if (index !in rows.indices) return
        rows.removeAt(index)
        if (rows.isEmpty()) rows += ReportHourPosition()
        reportState = reportState.copy(hours = rows, success = null, error = null)
    }
    fun updateReportHour(index: Int, transform: (ReportHourPosition) -> ReportHourPosition) {
        val rows = reportState.hours.toMutableList()
        if (index !in rows.indices) return
        rows[index] = transform(rows[index])
        reportState = reportState.copy(hours = rows, success = null, error = null)
    }
    fun addReportMaterial() { reportState = reportState.copy(materials = reportState.materials + TimeMaterialPosition(), success = null, error = null) }
    fun removeReportMaterial(index: Int) {
        val rows = reportState.materials.toMutableList()
        if (index !in rows.indices) return
        rows.removeAt(index)
        if (rows.isEmpty()) rows += TimeMaterialPosition()
        reportState = reportState.copy(materials = rows, success = null, error = null)
    }
    fun selectReportMaterial(index: Int, material: MaterialDto) {
        val rows = reportState.materials.toMutableList()
        if (index !in rows.indices) return
        rows[index] = TimeMaterialPosition(material.id, material.articleNo, material.name, rows[index].quantity.ifBlank { "1" }, material.unit.ifBlank { "Stk." }, material.salePrice)
        reportState = reportState.copy(materials = rows, success = null, error = null)
    }
    fun updateReportMaterialQuantity(index: Int, value: String) {
        val normalized = value.filter { it.isDigit() || it == ',' || it == '.' }.replace(',', '.')
        val rows = reportState.materials.toMutableList()
        if (index !in rows.indices) return
        rows[index] = rows[index].copy(quantity = normalized)
        reportState = reportState.copy(materials = rows, success = null, error = null)
    }
    fun toggleReportTime(id: Int) {
        val selected = reportState.selectedTimeIds.toMutableSet()
        if (!selected.add(id)) selected.remove(id)
        reportState = reportState.copy(selectedTimeIds = selected, success = null, error = null)
    }
    fun loadImportableTimes(projectId: Int) {
        viewModelScope.launch {
            reportState = reportState.copy(loadingTimes = true, error = null)
            val result = authorizedRequest { token -> NextErpApi.projectTimes(loginState.server, token, projectId) }
            reportState = if (result.isSuccess) reportState.copy(availableTimes = result.getOrNull().orEmpty(), loadingTimes = false)
            else reportState.copy(availableTimes = emptyList(), loadingTimes = false, error = result.exceptionOrNull()?.message ?: "Zeiten konnten nicht geladen werden.")
        }
    }
    fun saveReport() {
        val project = dataState.selectedProject ?: run { reportState = reportState.copy(error = "Kein Projekt ausgewählt."); return }
        if (reportState.title.isBlank()) { reportState = reportState.copy(error = "Bitte einen Titel eingeben."); return }
        val ownHours = reportState.hours.mapNotNull { row ->
            val h = calculateHours(row.fromTime, row.toTime, row.breakMinutes)
            if (h == null || h <= 0) null else JSONObject().put("workDate", row.workDate).put("hours", h).put("activity", row.activity)
        }
        val materials = reportState.materials.mapNotNull { row ->
            val id=row.materialId ?: return@mapNotNull null
            val qty=row.quantity.toDoubleOrNull() ?: return@mapNotNull null
            if(qty<=0) return@mapNotNull null
            JSONObject().put("materialId",id).put("description",row.name).put("quantity",qty).put("unit",row.unit).put("unitPrice",row.unitPrice)
        }
        if (ownHours.isEmpty() && reportState.selectedTimeIds.isEmpty()) {
            reportState = reportState.copy(error = "Mindestens eine eigene oder übernommene Zeit ist erforderlich.")
            return
        }
        if (reportState.customerSignatureData.isNotBlank() && reportState.customerSignedBy.isBlank()) {
            reportState = reportState.copy(error = "Bitte den Namen des unterschreibenden Kunden eintragen.")
            return
        }
        if (reportState.technicianSignatureData.isNotBlank() && reportState.technicianSignedBy.isBlank()) {
            reportState = reportState.copy(error = "Bitte den Namen des Monteurs eintragen.")
            return
        }
        reportState = reportState.copy(saving = true, error = null, success = null)
        viewModelScope.launch {
            val uploadedPhotoIds = mutableListOf<Int>()
            val updatedPhotos = reportState.photos.toMutableList()

            for (index in updatedPhotos.indices) {
                val photo = updatedPhotos[index]
                if (photo.uploadedDocumentId != null) {
                    uploadedPhotoIds += photo.uploadedDocumentId
                    continue
                }

                updatedPhotos[index] = photo.copy(uploading = true, error = null)
                reportState = reportState.copy(photos = updatedPhotos.toList())

                val bytes = runCatching {
                    getApplication<Application>().contentResolver
                        .openInputStream(Uri.parse(photo.localUri))
                        ?.use { it.readBytes() }
                        ?: error("Foto konnte nicht gelesen werden.")
                }

                if (bytes.isFailure) {
                    val message = bytes.exceptionOrNull()?.message ?: "Foto konnte nicht gelesen werden."
                    updatedPhotos[index] = photo.copy(uploading = false, error = message)
                    reportState = reportState.copy(
                        photos = updatedPhotos.toList(),
                        saving = false,
                        error = message
                    )
                    return@launch
                }

                val uploadResult = authorizedRequest { token ->
                    NextErpApi.uploadPhoto(
                        server = loginState.server,
                        token = token,
                        projectId = project.id,
                        category = photo.category,
                        fileName = photo.fileName,
                        bytes = bytes.getOrThrow()
                    )
                }

                if (uploadResult.isFailure) {
                    val message = uploadResult.exceptionOrNull()?.message ?: "Foto konnte nicht hochgeladen werden."
                    updatedPhotos[index] = photo.copy(uploading = false, error = message)
                    reportState = reportState.copy(
                        photos = updatedPhotos.toList(),
                        saving = false,
                        error = message
                    )
                    return@launch
                }

                val uploaded = uploadResult.getOrThrow()
                val documentId = uploaded.optInt("id")
                if (documentId > 0) uploadedPhotoIds += documentId
                updatedPhotos[index] = photo.copy(
                    uploadedDocumentId = documentId.takeIf { it > 0 },
                    uploading = false,
                    error = null
                )
                reportState = reportState.copy(photos = updatedPhotos.toList())
            }

            val result = authorizedRequest { token ->
                NextErpApi.createReport(
                    loginState.server,
                    token,
                    project.id,
                    reportState.reportDate,
                    reportState.title.trim(),
                    reportState.notes.trim(),
                    reportState.customerNote.trim(),
                    reportState.invoiceReady,
                    ownHours,
                    materials,
                    reportState.selectedTimeIds.toList(),
                    reportState.customerSignedBy.trim(),
                    reportState.customerSignatureData,
                    reportState.technicianSignedBy.trim(),
                    reportState.technicianSignatureData,
                    uploadedPhotoIds
                )
            }
            reportState = if(result.isSuccess) {
                val r=result.getOrThrow()
                reportState.copy(saving=false, success="Rapport ${r.optString("reportNo")} gespeichert.", error=null)
            } else reportState.copy(saving=false, error=result.exceptionOrNull()?.message ?: "Rapport konnte nicht gespeichert werden.")
            if(result.isSuccess) refresh()
        }
    }

    fun updateTimeDate(value: String) { timeEntryState = timeEntryState.copy(workDate = value, success = null, error = null) }
    fun updateFromTime(value: String) { timeEntryState = timeEntryState.copy(fromTime = value, success = null, error = null) }
    fun updateToTime(value: String) { timeEntryState = timeEntryState.copy(toTime = value, success = null, error = null) }
    fun updateBreakMinutes(value: String) { timeEntryState = timeEntryState.copy(breakMinutes = value.filter(Char::isDigit), success = null, error = null) }
    fun updateActivity(value: String) { timeEntryState = timeEntryState.copy(activity = value, success = null, error = null) }
    fun updateTimeNote(value: String) { timeEntryState = timeEntryState.copy(note = value, success = null, error = null) }

    fun addTimeMaterialPosition() {
        timeEntryState = timeEntryState.copy(
            materials = timeEntryState.materials + TimeMaterialPosition(),
            success = null,
            error = null
        )
    }

    fun removeTimeMaterialPosition(index: Int) {
        val updated = timeEntryState.materials.toMutableList()
        if (index !in updated.indices) return
        updated.removeAt(index)
        if (updated.isEmpty()) updated += TimeMaterialPosition()
        timeEntryState = timeEntryState.copy(materials = updated, success = null, error = null)
    }

    fun selectTimeMaterial(index: Int, material: MaterialDto) {
        val updated = timeEntryState.materials.toMutableList()
        if (index !in updated.indices) return
        updated[index] = TimeMaterialPosition(
            materialId = material.id,
            articleNo = material.articleNo,
            name = material.name,
            quantity = updated[index].quantity.ifBlank { "1" },
            unit = material.unit.ifBlank { "Stk." },
            unitPrice = material.salePrice
        )
        timeEntryState = timeEntryState.copy(materials = updated, success = null, error = null)
    }

    fun updateTimeMaterialQuantity(index: Int, value: String) {
        val normalized = value.filter { it.isDigit() || it == ',' || it == '.' }.replace(',', '.')
        val updated = timeEntryState.materials.toMutableList()
        if (index !in updated.indices) return
        updated[index] = updated[index].copy(quantity = normalized)
        timeEntryState = timeEntryState.copy(materials = updated, success = null, error = null)
    }

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
            val materialPositions = timeEntryState.materials.mapNotNull { position ->
                val materialId = position.materialId ?: return@mapNotNull null
                val quantity = position.quantity.toDoubleOrNull() ?: return@mapNotNull null
                if (quantity <= 0.0) return@mapNotNull null
                TimeMaterialPosition(
                    materialId = materialId,
                    articleNo = position.articleNo,
                    name = position.name,
                    quantity = quantity.toString(),
                    unit = position.unit,
                    unitPrice = position.unitPrice
                )
            }
            val result = authorizedRequest { token ->
                NextErpApi.createTime(
                    loginState.server,
                    token,
                    project.id,
                    timeEntryState.workDate,
                    hours,
                    timeEntryState.activity.ifBlank { "Arbeit" },
                    details,
                    materialPositions
                )
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
            materials = dataState.materials,
            materialsLoading = dataState.materialsLoading,
            selectedProject = dataState.selectedProject,
            error = null
        )
    }

    fun loadMaterials(query: String = "") {
        if (!loginState.loggedIn || accessToken.isBlank()) return
        viewModelScope.launch {
            dataState = dataState.copy(materialsLoading = true, error = null)
            val result = authorizedRequest { token -> NextErpApi.materials(loginState.server, token, query) }
            dataState = if (result.isSuccess) {
                dataState.copy(materials = result.getOrNull().orEmpty(), materialsLoading = false, error = null)
            } else {
                dataState.copy(materialsLoading = false, error = result.exceptionOrNull()?.message ?: "Material konnte nicht geladen werden.")
            }
        }
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

    suspend fun materials(server: String, token: String, query: String = ""): Result<List<MaterialDto>> = runCatching {
        val suffix = if (query.isBlank()) "" else "?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
        when (val data = request(server, "/material$suffix", token = token)) {
            is org.json.JSONArray -> data.toMaterials()
            is JSONObject -> data.optJSONArray("materials").toMaterials()
            else -> emptyList()
        }
    }


    suspend fun projectTimes(server: String, token: String, projectId: Int): Result<List<ImportableTimeDto>> = runCatching {
        when(val data=request(server, "/project/$projectId/times", token=token)) {
            is JSONArray -> data.toImportableTimes()
            is JSONObject -> data.optJSONArray("times").toImportableTimes()
            else -> emptyList()
        }
    }

    suspend fun createReport(
        server: String, token: String, projectId: Int, reportDate: String, title: String,
        description: String, customerNote: String, invoiceReady: Boolean,
        hours: List<JSONObject>, materials: List<JSONObject>, importEntryIds: List<Int>,
        customerSignedBy: String, customerSignatureData: String,
        technicianSignedBy: String, technicianSignatureData: String,
        photoDocumentIds: List<Int>
    ): Result<JSONObject> = runCatching {
        val body=JSONObject()
            .put("projectId",projectId).put("reportDate",reportDate).put("title",title)
            .put("description",description).put("customerNote",customerNote).put("invoiceReady",invoiceReady)
            .put("hours",JSONArray(hours)).put("materials",JSONArray(materials)).put("importEntryIds",JSONArray(importEntryIds))
            .put("customerSignedBy",customerSignedBy).put("customerSignatureData",customerSignatureData)
            .put("technicianSignedBy",technicianSignedBy).put("technicianSignatureData",technicianSignatureData)
            .put("photoDocumentIds", JSONArray(photoDocumentIds))
        request(server,"/report","POST",token,body) as? JSONObject ?: error("Ungültige Rapport-Antwort.")
    }

    suspend fun uploadPhoto(
        server: String,
        token: String,
        projectId: Int,
        category: String,
        fileName: String,
        bytes: ByteArray
    ): Result<JSONObject> = runCatching {
        withContext(Dispatchers.IO) {
            val boundary = "----NextERP${System.currentTimeMillis()}"
            val safeCategory = java.net.URLEncoder.encode(category, "UTF-8")
            val url = URL(
                apiBase(server) +
                    "/upload?projectId=$projectId&type=photo&category=$safeCategory"
            )
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }

            try {
                connection.outputStream.use { output ->
                    val header = buildString {
                        append("--$boundary\r\n")
                        append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                        append(fileName.replace("\"", "_"))
                        append("\"\r\n")
                        append("Content-Type: image/jpeg\r\n\r\n")
                    }.toByteArray(Charsets.UTF_8)
                    output.write(header)
                    output.write(bytes)
                    output.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
                }

                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val json = if (body.isBlank()) JSONObject() else JSONObject(body)
                if (code == 401) throw ApiUnauthorizedException(apiMessage(json, "Anmeldung abgelaufen."))
                if (code !in 200..299) error(apiMessage(json, "Foto-Upload fehlgeschlagen (HTTP $code)."))
                if (!json.optBoolean("success", false)) error(apiMessage(json, "Foto-Upload fehlgeschlagen."))
                json.optJSONObject("data") ?: error("Ungültige Upload-Antwort.")
            } finally {
                connection.disconnect()
            }
        }
    }

    suspend fun projectDocuments(
        server: String,
        token: String,
        projectId: Int
    ): Result<List<DocumentDto>> = runCatching {
        when (val data = request(server, "/project/$projectId/documents", token = token)) {
            is org.json.JSONArray -> data.toDocuments()
            is JSONObject -> data.optJSONArray("documents").toDocuments()
            else -> emptyList()
        }
    }

    suspend fun createTime(
        server: String,
        token: String,
        projectId: Int,
        workDate: String,
        hours: Double,
        activity: String,
        note: String,
        materials: List<TimeMaterialPosition>
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
                .put("note", note)
                .put("materials", org.json.JSONArray().apply {
                    materials.forEach { position ->
                        put(JSONObject()
                            .put("materialId", position.materialId)
                            .put("description", position.name)
                            .put("quantity", position.quantity.toDoubleOrNull() ?: 0.0)
                            .put("unit", position.unit)
                            .put("unitPrice", position.unitPrice)
                        )
                    }
                })
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

    private fun org.json.JSONArray?.toMaterials(): List<MaterialDto> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                optJSONObject(i)?.let { json ->
                    add(MaterialDto(
                        id = json.optInt("id"),
                        articleNo = json.optString("article_no", json.optString("articleNo")),
                        name = json.optString("name", "Material"),
                        barcode = json.optString("barcode"),
                        unit = json.optString("unit", "Stk."),
                        stockQuantity = json.optDouble("stock_quantity", json.optDouble("stockQuantity", 0.0)),
                        minimumStock = json.optDouble("minimum_stock", json.optDouble("minimumStock", 0.0)),
                        salePrice = json.optDouble("sale_price", json.optDouble("salePrice", 0.0))
                    ))
                }
            }
        }
    }

    private fun JSONArray?.toImportableTimes(): List<ImportableTimeDto> {
        if(this==null)return emptyList()
        return buildList { for(i in 0 until length()) optJSONObject(i)?.let { j -> add(ImportableTimeDto(j.optInt("id"),j.optString("workDate"),j.optString("userId"),j.optString("displayName",j.optString("userId")),j.optDouble("hours"),j.optString("activity"),j.optInt("materialCount"),j.optDouble("materialTotal"))) } }
    }

    private fun org.json.JSONArray?.toDocuments(): List<DocumentDto> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                optJSONObject(i)?.let { json ->
                    add(
                        DocumentDto(
                            id = json.optInt("id"),
                            projectId = json.optInt("project_id", json.optInt("projectId")),
                            documentType = json.optString(
                                "document_type",
                                json.optString("documentType", "other")
                            ),
                            fileName = json.optString(
                                "file_name",
                                json.optString("fileName", "Dokument")
                            ),
                            filePath = json.optString(
                                "file_path",
                                json.optString("filePath")
                            ),
                            mimeType = json.optString(
                                "mime_type",
                                json.optString("mimeType", "application/octet-stream")
                            ),
                            status = json.optString("status"),
                            createdBy = json.optString(
                                "created_by",
                                json.optString("createdBy")
                            ),
                            createdAt = json.optString(
                                "created_at",
                                json.optString("createdAt")
                            )
                        )
                    )
                }
            }
        }
    }

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
                Screen.PROJECT -> ProjectScreen(
                    project = vm.dataState.selectedProject,
                    openTimeEntry = vm::openTimeEntry,
                    openDocuments = {
                        vm.dataState.selectedProject?.let(vm::openProjectDocuments)
                    },
                    openReport = vm::openReport
                )
                Screen.TIME_ENTRY -> TimeEntryScreen(vm.dataState.selectedProject, vm.timeEntryState, vm.dataState.materials, vm.dataState.materialsLoading, vm)
                Screen.REPORT -> ReportScreen(vm.dataState.selectedProject, vm.reportState, vm.dataState.materials, vm.dataState.materialsLoading, vm)
                Screen.SCANNER -> PlaceholderScreen("Scanner", "Dokumente und QR-Codes folgen im nächsten Ausbau.", Icons.Default.QrCodeScanner)
                Screen.MATERIAL -> MaterialScreen(vm.dataState, vm::loadMaterials)
                Screen.DOCUMENTS -> DocumentsScreen(
                    state = vm.dataState,
                    server = vm.loginState.server,
                    selectProject = vm::openProjectDocuments,
                    refresh = {
                        vm.dataState.selectedProject?.let { vm.loadProjectDocuments(it.id) }
                    }
                )
                Screen.MORE -> MoreScreen(vm.loginState, vm::logout)
            }
        }
    }
}

@Composable
private fun AppHeader(name: String, role: String, screen: Screen, onRefresh: () -> Unit, onLogout: () -> Unit) {
    val title = when (screen) {
        Screen.TODAY -> "Heute"; Screen.PROJECTS -> "Projekte"; Screen.PROJECT -> "Projekt"; Screen.TIME_ENTRY -> "Zeiten"; Screen.REPORT -> "Rapport"; Screen.SCANNER -> "Scanner"; Screen.MATERIAL -> "Material"; Screen.DOCUMENTS -> "Dokumente"; Screen.MORE -> "Mehr"
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
private fun ProjectScreen(
    project: ProjectDto?,
    openTimeEntry: () -> Unit,
    openDocuments: () -> Unit,
    openReport: () -> Unit
) {
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
        PrimaryAction("Arbeitszeit eintragen", Icons.Default.Schedule, openTimeEntry)
        PrimaryAction("Material", Icons.Default.Inventory2) {}
        PrimaryAction("Dokumente", Icons.Default.Description, openDocuments)
        PrimaryAction("Fotos", Icons.Default.PhotoCamera) {}
        PrimaryAction("Rapport erstellen", Icons.Default.EditNote, openReport)
    }
}

@Composable
private fun TimeEntryScreen(
    project: ProjectDto?,
    state: TimeEntryState,
    availableMaterials: List<MaterialDto>,
    materialsLoading: Boolean,
    vm: AppViewModel
) {
    if (project == null) { ScreenColumn { EmptyState("Kein Projekt ausgewählt.") }; return }
    val hours = calculateHours(state.fromTime, state.toTime, state.breakMinutes)
    val activities = listOf("Montage", "Service", "Reparatur", "Aufmaß", "Werkstatt", "Planung", "Anfahrt", "Sonstiges")
    var pickerIndex by remember { mutableStateOf<Int?>(null) }
    var materialQuery by remember { mutableStateOf("") }

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

        Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Inventory2, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Material für die Abrechnung", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Jede Position wird zusammen mit der Arbeitszeit gespeichert.", style = MaterialTheme.typography.bodySmall)
                    }
                }

                state.materials.forEachIndexed { index, position ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Position ${index + 1}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                if (state.materials.size > 1 || position.materialId != null) {
                                    IconButton(onClick = { vm.removeTimeMaterialPosition(index) }) {
                                        Icon(Icons.Default.Delete, "Position entfernen")
                                    }
                                }
                            }
                            OutlinedButton(
                                onClick = { pickerIndex = index; materialQuery = "" },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.Search, null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (position.materialId == null) "Material auswählen"
                                    else listOf(position.articleNo, position.name).filter { it.isNotBlank() }.joinToString(" · "),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (position.materialId != null) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = position.quantity,
                                        onValueChange = { vm.updateTimeMaterialQuantity(index, it) },
                                        label = { Text("Menge") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(position.unit.ifBlank { "Stk." }, fontWeight = FontWeight.SemiBold)
                                }
                                val quantity = position.quantity.toDoubleOrNull() ?: 0.0
                                if (position.unitPrice > 0.0) {
                                    Text(
                                        "Abrechnungswert: ${formatMoney(quantity * position.unitPrice)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                FilledTonalButton(
                    onClick = vm::addTimeMaterialPosition,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Weitere Materialposition")
                }
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
            else { Icon(Icons.Default.Save, null); Spacer(Modifier.width(10.dp)); Text("Zeit und Material speichern", fontWeight = FontWeight.Bold) }
        }
    }

    pickerIndex?.let { index ->
        AlertDialog(
            onDismissRequest = { pickerIndex = null },
            title = { Text("Material auswählen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = materialQuery,
                        onValueChange = { materialQuery = it },
                        label = { Text("Suche") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (materialsLoading) {
                        LoadingState()
                    } else {
                        val filtered = availableMaterials.filter { material ->
                            materialQuery.isBlank() || listOf(material.name, material.articleNo, material.barcode)
                                .any { it.contains(materialQuery, ignoreCase = true) }
                        }.take(30)
                        Column(
                            Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (filtered.isEmpty()) Text("Kein Material gefunden.")
                            filtered.forEach { material ->
                                Card(
                                    onClick = { vm.selectTimeMaterial(index, material); pickerIndex = null },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(material.name, fontWeight = FontWeight.Bold)
                                        val details = listOf(material.articleNo, material.unit).filter { it.isNotBlank() }.joinToString(" · ")
                                        if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.bodySmall)
                                        Text("Bestand ${material.stockQuantity} ${material.unit}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickerIndex = null }) { Text("Schließen") } }
        )
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
            Text("Tippen für Navigation, Arbeitszeit und Material", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ReportScreen(
    project: ProjectDto?, state: ReportState, availableMaterials: List<MaterialDto>,
    materialsLoading: Boolean, vm: AppViewModel
) {
    if(project==null){ ScreenColumn { EmptyState("Kein Projekt ausgewählt.") }; return }
    var pickerIndex by remember { mutableStateOf<Int?>(null) }
    var materialQuery by remember { mutableStateOf("") }
    val activities=listOf("Montage","Service","Reparatur","Aufmaß","Werkstatt","Anfahrt","Sonstiges")
    ScreenColumn {
        Text("Rapport erstellen",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
        Text(project.projectName,fontWeight=FontWeight.Bold)
        Text(project.projectNo,style=MaterialTheme.typography.bodySmall)

        Card(shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(state.reportDate,vm::updateReportDate,label={Text("Datum")},leadingIcon={Icon(Icons.Default.CalendarMonth,null)},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(state.title,vm::updateReportTitle,label={Text("Titel")},singleLine=true,modifier=Modifier.fillMaxWidth())
        }}

        Card(shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("Ausgeführte Arbeiten",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                OutlinedTextField(
                    state.notes,
                    vm::updateReportNotes,
                    label={Text("Was wurde gemacht?")},
                    placeholder={Text("Zum Beispiel: Haustür montiert, Beschläge eingestellt und Funktion geprüft.")},
                    minLines=5,
                    modifier=Modifier.fillMaxWidth()
                )
            }
        }

        Card(shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Zeiten im Rapport",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text("Eigene Zeiten eintragen",fontWeight=FontWeight.SemiBold)
            state.hours.forEachIndexed { index,row ->
                Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant),shape=RoundedCornerShape(18.dp)) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically){Text("Zeit ${index+1}",fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));if(state.hours.size>1)IconButton(onClick={vm.removeReportHour(index)}){Icon(Icons.Default.Delete,"Entfernen")}}
                    OutlinedTextField(row.workDate,{v->vm.updateReportHour(index){it.copy(workDate=v)}},label={Text("Datum")},singleLine=true,modifier=Modifier.fillMaxWidth())
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(row.fromTime,{v->vm.updateReportHour(index){it.copy(fromTime=v)}},label={Text("Von")},singleLine=true,modifier=Modifier.weight(1f));OutlinedTextField(row.toTime,{v->vm.updateReportHour(index){it.copy(toTime=v)}},label={Text("Bis")},singleLine=true,modifier=Modifier.weight(1f))}
                    OutlinedTextField(row.breakMinutes,{v->vm.updateReportHour(index){it.copy(breakMinutes=v.filter(Char::isDigit))}},label={Text("Pause Minuten")},singleLine=true,modifier=Modifier.fillMaxWidth())
                    activities.chunked(2).forEach { group -> Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){group.forEach { a->FilterChip(selected=row.activity==a,onClick={vm.updateReportHour(index){it.copy(activity=a)}},label={Text(a)},modifier=Modifier.weight(1f))};if(group.size==1)Spacer(Modifier.weight(1f))}}
                    val h=calculateHours(row.fromTime,row.toTime,row.breakMinutes)
                    Text("${h?.let(::formatHours) ?: "–"} Stunden",fontWeight=FontWeight.Bold)
                }}
            }
            FilledTonalButton(vm::addReportHour,Modifier.fillMaxWidth()){Icon(Icons.Default.Add,null);Spacer(Modifier.width(8.dp));Text("Weitere eigene Zeit")}
            HorizontalDivider()
            Text("Erfasste Zeiten übernehmen",fontWeight=FontWeight.SemiBold)
            if(state.loadingTimes) LoadingState() else if(state.availableTimes.isEmpty()) Text("Keine noch nicht übernommenen Zeiten vorhanden.",style=MaterialTheme.typography.bodySmall) else state.availableTimes.forEach { t ->
                Card(onClick={vm.toggleReportTime(t.id)},shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Checkbox(checked=t.id in state.selectedTimeIds,onCheckedChange={vm.toggleReportTime(t.id)});Column(Modifier.weight(1f)){Text("${t.workDate} · ${t.displayName}",fontWeight=FontWeight.Bold);Text(t.activity)};Text("${formatHours(t.hours)} h",fontWeight=FontWeight.Bold)} }
            }
            if(state.selectedTimeIds.isNotEmpty()) Text("${state.selectedTimeIds.size} Zeiten werden übernommen.",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
        }}

        Card(shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Material im Rapport",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            state.materials.forEachIndexed { index,p -> Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant),shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){Text("Position ${index+1}",fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));if(state.materials.size>1||p.materialId!=null)IconButton(onClick={vm.removeReportMaterial(index)}){Icon(Icons.Default.Delete,"Entfernen")}}
                OutlinedButton(onClick={pickerIndex=index;materialQuery=""},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Search,null);Spacer(Modifier.width(8.dp));Text(if(p.materialId==null)"Material auswählen" else listOf(p.articleNo,p.name).filter{it.isNotBlank()}.joinToString(" · "),modifier=Modifier.weight(1f))}
                if(p.materialId!=null){Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){OutlinedTextField(p.quantity,{vm.updateReportMaterialQuantity(index,it)},label={Text("Menge")},singleLine=true,modifier=Modifier.weight(1f));Text(p.unit,fontWeight=FontWeight.Bold)};val qty=p.quantity.toDoubleOrNull()?:0.0;if(p.unitPrice>0)Text("Abrechnungswert: ${formatMoney(qty*p.unitPrice)}",fontWeight=FontWeight.SemiBold)}
            }}}
            FilledTonalButton(vm::addReportMaterial,Modifier.fillMaxWidth()){Icon(Icons.Default.Add,null);Spacer(Modifier.width(8.dp));Text("Weitere Materialposition")}
            Text("Material aus übernommenen Zeiten wird serverseitig automatisch mit übernommen.",style=MaterialTheme.typography.bodySmall)
        }}


        ReportPhotosSection(
            project = project,
            photos = state.photos,
            addPhoto = vm::addReportPhoto,
            removePhoto = vm::removeReportPhoto
        )


        Card(shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Unterschriften",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                Text("Kunde / Auftraggeber",fontWeight=FontWeight.SemiBold)
                OutlinedTextField(
                    state.customerSignedBy,
                    vm::updateCustomerSignedBy,
                    label={Text("Name des Kunden")},
                    leadingIcon={Icon(Icons.Default.Person,null)},
                    singleLine=true,
                    modifier=Modifier.fillMaxWidth()
                )
                SignaturePad(
                    label="Kunde unterschreibt hier",
                    signatureData=state.customerSignatureData,
                    onSignatureChanged=vm::updateCustomerSignature
                )
                HorizontalDivider()
                Text("Monteur",fontWeight=FontWeight.SemiBold)
                OutlinedTextField(
                    state.technicianSignedBy,
                    vm::updateTechnicianSignedBy,
                    label={Text("Name des Monteurs")},
                    leadingIcon={Icon(Icons.Default.Engineering,null)},
                    singleLine=true,
                    modifier=Modifier.fillMaxWidth()
                )
                SignaturePad(
                    label="Monteur unterschreibt hier",
                    signatureData=state.technicianSignatureData,
                    onSignatureChanged=vm::updateTechnicianSignature
                )
                Text(
                    "Ohne Unterschrift kann der Rapport als Entwurf gespeichert werden. Mit Kundenunterschrift wird er serverseitig abgeschlossen.",
                    style=MaterialTheme.typography.bodySmall
                )
            }
        }

        Card(shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(state.customerNote,vm::updateReportCustomerNote,label={Text("Hinweis für den Kunden")},minLines=2,modifier=Modifier.fillMaxWidth())
            Row(verticalAlignment=Alignment.CenterVertically){Switch(state.invoiceReady,vm::updateReportInvoiceReady);Spacer(Modifier.width(10.dp));Column{Text("Für Rechnung vormerken",fontWeight=FontWeight.Bold);Text("Rapport im Büro als abrechnungsbereit markieren",style=MaterialTheme.typography.bodySmall)}}
        }}
        state.error?.let{Text(it,color=MaterialTheme.colorScheme.error,fontWeight=FontWeight.Bold)}
        state.success?.let{Text(it,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)}
        Button(vm::saveReport,enabled=!state.saving,modifier=Modifier.fillMaxWidth().height(60.dp),shape=RoundedCornerShape(18.dp)){if(state.saving)CircularProgressIndicator(Modifier.size(24.dp),strokeWidth=2.dp)else{Icon(Icons.Default.Save,null);Spacer(Modifier.width(8.dp));Text("Rapport speichern",fontWeight=FontWeight.Bold)}}
    }
    pickerIndex?.let { index -> AlertDialog(onDismissRequest={pickerIndex=null},title={Text("Material auswählen")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(materialQuery,{materialQuery=it},label={Text("Suche")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true,modifier=Modifier.fillMaxWidth());if(materialsLoading)LoadingState()else Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){availableMaterials.filter{m->materialQuery.isBlank()||listOf(m.name,m.articleNo,m.barcode).any{it.contains(materialQuery,true)}}.take(30).forEach{m->Card(onClick={vm.selectReportMaterial(index,m);pickerIndex=null},modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text(m.name,fontWeight=FontWeight.Bold);Text(listOf(m.articleNo,m.unit,formatMoney(m.salePrice)).filter{it.isNotBlank()}.joinToString(" · "),style=MaterialTheme.typography.bodySmall)}}}}}},confirmButton={TextButton(onClick={pickerIndex=null}){Text("Schließen")}}) }
}


@Composable
private fun ReportPhotosSection(
    project: ProjectDto,
    photos: List<ReportPhoto>,
    addPhoto: (String, String, String) -> Unit,
    removePhoto: (Int) -> Unit
) {
    val context = LocalContext.current
    var pendingCategory by remember { mutableStateOf("Sonstige") }
    var pendingFile by remember { mutableStateOf<File?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingFile
        if (success && file != null && file.exists() && file.length() > 0L) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            addPhoto(pendingCategory, uri.toString(), file.name)
        } else {
            file?.delete()
        }
        pendingFile = null
    }

    fun takePhoto(category: String) {
        val photoDir = File(context.cacheDir, "rapport_photos").apply { mkdirs() }
        val safeProject = project.projectNo.ifBlank { project.id.toString() }
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
        val safeCategory = category.lowercase()
            .replace("ä", "ae")
            .replace("ö", "oe")
            .replace("ü", "ue")
            .replace(Regex("[^a-z0-9_-]"), "_")
        val file = File(
            photoDir,
            "${safeProject}_${safeCategory}_${System.currentTimeMillis()}.jpg"
        )
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        pendingCategory = category
        pendingFile = file
        cameraLauncher.launch(uri)
    }

    val categories = listOf(
        "Vorher" to Icons.Default.History,
        "Nachher" to Icons.Default.DoneAll,
        "Montage" to Icons.Default.Handyman,
        "Schaden" to Icons.Default.Warning,
        "Abnahme" to Icons.Default.Verified,
        "Sonstige" to Icons.Default.AddAPhoto
    )

    Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Fotodokumentation",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Kategorie antippen und Foto aufnehmen. Für weitere Bilder einfach erneut antippen.",
                style = MaterialTheme.typography.bodySmall
            )

            categories.chunked(2).forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { (category, icon) ->
                        FilledTonalButton(
                            onClick = { takePhoto(category) },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(icon, null)
                            Spacer(Modifier.width(6.dp))
                            Text(category)
                        }
                    }
                }
            }

            if (photos.isEmpty()) {
                Text(
                    "Noch keine Fotos aufgenommen.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                HorizontalDivider()
                photos.groupBy { it.category }.forEach { (category, items) ->
                    Text(
                        "$category · ${items.size}",
                        fontWeight = FontWeight.SemiBold
                    )
                    items.forEach { photo ->
                        val originalIndex = photos.indexOf(photo)
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = photo.localUri,
                                    contentDescription = photo.category,
                                    modifier = Modifier.size(82.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(photo.category, fontWeight = FontWeight.Bold)
                                    Text(
                                        when {
                                            photo.uploading -> "Wird hochgeladen …"
                                            photo.uploadedDocumentId != null -> "Hochgeladen"
                                            photo.error != null -> photo.error
                                            else -> "Wird beim Speichern hochgeladen"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = when {
                                            photo.error != null -> MaterialTheme.colorScheme.error
                                            photo.uploadedDocumentId != null -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                IconButton(
                                    onClick = { removePhoto(originalIndex) },
                                    enabled = !photo.uploading
                                ) {
                                    Icon(Icons.Default.Delete, "Foto löschen")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SignaturePad(
    label: String,
    signatureData: String,
    onSignatureChanged: (String) -> Unit
) {
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }
    val strokes = remember { mutableStateListOf<MutableList<Offset>>() }

    fun exportSignature() {
        if (strokes.none { it.isNotEmpty() } || canvasSize.width <= 1 || canvasSize.height <= 1) {
            onSignatureChanged("")
            return
        }
        val bitmap = Bitmap.createBitmap(canvasSize.width, canvasSize.height, Bitmap.Config.ARGB_8888)
        val androidCanvas = android.graphics.Canvas(bitmap)
        androidCanvas.drawColor(android.graphics.Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            style = Paint.Style.STROKE
        }
        strokes.forEach { stroke ->
            if (stroke.size == 1) {
                val point = stroke.first()
                androidCanvas.drawPoint(point.x, point.y, paint)
            } else {
                for (i in 1 until stroke.size) {
                    val from = stroke[i - 1]
                    val to = stroke[i]
                    androidCanvas.drawLine(from.x, from.y, to.x, to.y, paint)
                }
            }
        }
        val bytes = ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
        bitmap.recycle()
        onSignatureChanged("data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { point -> strokes.add(mutableListOf(point)) },
                            onDrag = { change, _ ->
                                change.consume()
                                if (strokes.isNotEmpty()) strokes.last().add(change.position)
                            },
                            onDragEnd = { exportSignature() },
                            onDragCancel = { exportSignature() }
                        )
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    strokes.forEach { stroke ->
                        if (stroke.size == 1) {
                            drawCircle(Color.Black, radius = 3f, center = stroke.first())
                        } else {
                            for (i in 1 until stroke.size) {
                                drawLine(
                                    color = Color.Black,
                                    start = stroke[i - 1],
                                    end = stroke[i],
                                    strokeWidth = 6f,
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    }
                }
                if (strokes.isEmpty() && signatureData.isBlank()) {
                    Text(
                        label,
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.Gray
                    )
                }
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (signatureData.isBlank()) "Noch nicht unterschrieben" else "Unterschrift erfasst",
                    modifier = Modifier.weight(1f),
                    color = if (signatureData.isBlank()) Color.Gray else Color(0xFF2E7D32),
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = {
                    strokes.clear()
                    onSignatureChanged("")
                }) {
                    Icon(Icons.Default.DeleteSweep, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Löschen")
                }
            }
        }
    }
}

@Composable
private fun MaterialScreen(state: DataState, loadMaterials: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { loadMaterials("") }
    ScreenColumn {
        Text("Material", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Artikel, Bezeichnung oder Barcode") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { IconButton(onClick = { loadMaterials(query) }) { Icon(Icons.Default.Search, "Suchen") } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { loadMaterials(query) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text("Material suchen")
        }
        when {
            state.materialsLoading -> LoadingState()
            state.error != null -> ErrorState(state.error) { loadMaterials(query) }
            state.materials.isEmpty() -> EmptyState("Kein Material gefunden.")
            else -> state.materials.forEach { material -> MaterialCard(material) }
        }
    }
}

@Composable
private fun MaterialCard(material: MaterialDto) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(material.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (material.articleNo.isNotBlank()) Text("Artikel ${material.articleNo}", style = MaterialTheme.typography.bodySmall)
            if (material.barcode.isNotBlank()) Text("Barcode ${material.barcode}", style = MaterialTheme.typography.bodySmall)
            val stockText = String.format(java.util.Locale.GERMANY, "%.2f %s", material.stockQuantity, material.unit)
            Text("Bestand: $stockText", fontWeight = FontWeight.SemiBold, color = if (material.stockQuantity <= material.minimumStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
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
private fun DocumentsScreen(
    state: DataState,
    server: String,
    selectProject: (ProjectDto) -> Unit,
    refresh: () -> Unit
) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    val project = state.selectedProject

    ScreenColumn {
        Text("Dokumente", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        if (project == null) {
            Text("Zuerst ein Projekt auswählen.", style = MaterialTheme.typography.bodyLarge)
            if (state.projects.isEmpty()) {
                EmptyState("Keine Projekte verfügbar.")
            } else {
                state.projects.forEach { item ->
                    Card(
                        onClick = { selectProject(item) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, null, tint = parseColor(item.color))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.projectName, fontWeight = FontWeight.Bold)
                                if (item.projectNo.isNotBlank()) {
                                    Text(item.projectNo, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Icon(Icons.Default.ChevronRight, null)
                        }
                    }
                }
            }
            return@ScreenColumn
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.FolderOpen, null, tint = parseColor(project.color))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(project.projectName, fontWeight = FontWeight.Bold)
                    if (project.projectNo.isNotBlank()) {
                        Text(project.projectNo, style = MaterialTheme.typography.bodySmall)
                    }
                }
                IconButton(onClick = refresh) {
                    Icon(Icons.Default.Refresh, "Dokumente aktualisieren")
                }
            }
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Dokument suchen") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true
        )

        when {
            state.documentsLoading -> LoadingState()
            state.documentsError != null -> ErrorState(state.documentsError, refresh)
            else -> {
                val filtered = state.documents.filter { document ->
                    search.isBlank() ||
                        document.fileName.contains(search, ignoreCase = true) ||
                        document.documentType.contains(search, ignoreCase = true) ||
                        document.mimeType.contains(search, ignoreCase = true)
                }

                if (filtered.isEmpty()) {
                    EmptyState(
                        if (search.isBlank()) "Keine Dokumente in diesem Projekt."
                        else "Keine passenden Dokumente gefunden."
                    )
                } else {
                    Text(
                        "${filtered.size} Dokumente",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    filtered.forEach { document ->
                        DocumentCard(
                            document = document,
                            onOpen = {
                                val cleanServer = server.trim().trimEnd('/')
                                val directory = document.filePath
                                    .substringBeforeLast('/', "")
                                    .let { if (it.startsWith("/")) it else "/$it" }
                                val target = buildString {
                                    append(cleanServer)
                                    append("/index.php/apps/files/?dir=")
                                    append(Uri.encode(directory))
                                    if (document.fileName.isNotBlank()) {
                                        append("&scrollto=")
                                        append(Uri.encode(document.fileName))
                                    }
                                }
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentCard(document: DocumentDto, onOpen: () -> Unit) {
    val icon = when {
        document.mimeType == "application/pdf" ||
            document.fileName.endsWith(".pdf", ignoreCase = true) -> Icons.Default.PictureAsPdf
        document.mimeType.startsWith("image/") -> Icons.Default.Image
        else -> Icons.Default.Description
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        icon,
                        null,
                        Modifier.padding(12.dp).size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        document.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(documentTypeLabel(document), style = MaterialTheme.typography.bodySmall)
                }
            }

            val details = listOf(
                document.createdAt.takeIf { it.isNotBlank() },
                document.createdBy.takeIf { it.isNotBlank() }
            ).filterNotNull().joinToString(" · ")

            if (details.isNotBlank()) {
                Text(details, style = MaterialTheme.typography.bodySmall)
            }

            FilledTonalButton(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.OpenInNew, null)
                Spacer(Modifier.width(8.dp))
                Text("In Nextcloud öffnen")
            }
        }
    }
}

private fun documentTypeLabel(document: DocumentDto): String = when {
    document.mimeType == "application/pdf" ||
        document.fileName.endsWith(".pdf", ignoreCase = true) -> "PDF"
    document.mimeType.startsWith("image/") -> "Bild"
    document.documentType == "photo" -> "Foto"
    document.documentType == "scan" -> "Scan"
    else -> document.documentType.ifBlank { "Dokument" }
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
        Text("NextERP Mobile 1.5.0 · API v1")
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
private fun formatMoney(value: Double): String = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.GERMANY).format(value)

private fun roleLabel(role: String): String = when (role.lowercase()) {
    "administrator", "admin" -> "Administrator"; "office" -> "Büro"; "manager" -> "Projektleiter"; "employee" -> "Monteur"; else -> role
}

private fun parseColor(value: String): Color = runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(Color(0xFF546E7A))
