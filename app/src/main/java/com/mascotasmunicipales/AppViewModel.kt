package com.mascotasmunicipales

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import org.json.JSONObject
import java.util.UUID

data class SessionState(val phase: String, val message: String = "")

/** Conserva solicitudes fuera de la Activity y recupera borradores/envíos por cuenta. */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    val repo = MunicipalRepository()
    private val sessionValue = MutableLiveData(SessionState("starting"))
    val session: LiveData<SessionState> = sessionValue
    private val changedValue = MutableLiveData(0)
    val draftsChanged: LiveData<Int> = changedValue
    private var account: String? = null
    private var store: DraftStore? = null
    private var root = JSONObject()
    private var authenticating = false
    private var generation = 0
    private val handler = Handler(Looper.getMainLooper())
    private val saveDraft = Runnable { flush() }
    private val watches = mutableMapOf<String, ListenerRegistration>()
    private val watchVersions = mutableMapOf<String, Int>()
    private val activeWrites = mutableSetOf<String>()
    var petDraft = Draft(); private set
    var reportDraft = Draft(); private set
    var storageError = ""; private set
    var tab: Int
        get() = root.optInt("tab", 0)
        set(value) { root.put("tab", value) }
    val screen: String get() = root.optString("screen", "home")
    var previousScreen: String
        get() = root.optString("previous", "home")
        set(value) { root.put("previous", value) }
    val ready: Boolean get() = sessionValue.value?.phase == "ready"
    private val authListener = FirebaseAuth.AuthStateListener {
        if (!authenticating) acceptSession()
    }

    init { repo.auth.addAuthStateListener(authListener) }

    fun authenticate(email: String, password: String, register: Boolean) {
        if (authenticating || sessionValue.value?.phase == "preparing") return
        if (email.isBlank() || password.length < 6) {
            sessionValue.value = SessionState("signedOut", "Escribe un correo y una contraseña de al menos 6 caracteres.")
            return
        }
        authenticating = true
        sessionValue.value = SessionState("authenticating", "Comprobando cuenta…")
        val done: (String?) -> Unit = { error ->
            authenticating = false
            if (error == null) acceptSession()
            else sessionValue.value = SessionState("signedOut", error)
        }
        if (register) repo.register(email, password, done) else repo.login(email, password, done)
    }

    private fun acceptSession() {
        val uid = repo.uid
        if (uid == account && sessionValue.value?.phase in listOf("ready", "preparing", "profileError")) return
        if (uid != account) {
            flush()
            generation++
            watches.values.forEach { it.remove() }; watches.clear(); activeWrites.clear()
            account = uid; store = null; root = JSONObject(); storageError = ""
            petDraft = Draft(); reportDraft = Draft()
            if (uid != null) {
                store = DraftStore(getApplication(), uid)
                try {
                    root = store!!.read()
                    petDraft = Draft(root.optJSONObject("pet") ?: JSONObject())
                    reportDraft = Draft(root.optJSONObject("report") ?: JSONObject())
                } catch (_: Exception) {
                    storageError = "No se pudo recuperar el borrador local. No se enviarán nuevos registros."
                }
            }
        }
        if (uid == null) sessionValue.value = SessionState("signedOut") else prepareProfile()
    }

    fun prepareProfile() {
        if (repo.uid == null || sessionValue.value?.phase == "preparing") return
        val requestGeneration = generation
        sessionValue.value = SessionState("preparing", "Preparando tu perfil… Si estás sin conexión, espera a reconectarte.")
        repo.ensureProfile { error ->
            if (requestGeneration == generation && repo.uid == account) {
                if (error != null) sessionValue.value = SessionState("profileError", "No se pudo preparar tu perfil: $error")
                else {
                    recover("pet"); recover("report")
                    sessionValue.value = SessionState("ready")
                }
            }
        }
    }

    fun logout() { flush(); repo.logout() }

    fun navigate(route: String) { root.put("screen", route); flush() }
    fun draft(kind: String) = if (kind == "pet") petDraft else reportDraft
    fun draftEdited() {
        handler.removeCallbacks(saveDraft)
        handler.postDelayed(saveDraft, 250)
    }

    fun flush(): Boolean {
        handler.removeCallbacks(saveDraft)
        val target = store ?: return true
        if (storageError.isNotEmpty()) return false
        return try {
            root.put("pet", petDraft.json).put("report", reportDraft.json)
            target.write(root); true
        } catch (_: Exception) {
            storageError = "No se pudo guardar el borrador en este dispositivo. El envío está bloqueado para evitar duplicados."
            changedValue.value = (changedValue.value ?: 0) + 1
            false
        }
    }

    fun startAnother(kind: String) {
        if (draft(kind).status != "confirmed") return
        watches.remove(kind)?.remove()
        if (kind == "pet") petDraft = Draft() else reportDraft = Draft()
        flush(); changed()
    }

    fun submit(kind: String) {
        val d = draft(kind)
        if (!ready || d.status in listOf("pending", "confirmed") || storageError.isNotEmpty()) return
        if (d.id.isEmpty()) d.id = UUID.randomUUID().toString().replace("-", "")
        d.status = "pending"; d.error = ""
        // El ID y el contenido quedan en disco ANTES de encolar la escritura de Firestore.
        if (!flush()) { d.status = "failed"; changed(); return }
        val uid = account; val id = d.id; val requestGeneration = generation
        activeWrites.add(kind)
        val done: (String?) -> Unit = { error ->
            if (generation == requestGeneration && account == uid && draft(kind).id == id) {
                activeWrites.remove(kind)
                if (error == null) finish(kind, "confirmed")
                else if (draft(kind).status != "confirmed") finish(kind, "failed", error)
            }
        }
        if (kind == "pet") repo.createPet(d.id, Pet(
            name = d.value("name"), species = d.value("species", "Perro"), breed = d.value("breed"),
            sex = d.value("sex", "Hembra"), age = d.value("age"), color = d.value("color"),
            territoryId = d.value("territory", "comuna-1")
        ), done)
        else repo.createReport(d.id, Report(
            petName = d.value("name"), type = d.value("type", "Pérdida"),
            species = d.value("species", "Perro"), territoryId = d.value("territory", "comuna-1"),
            description = d.value("description")
        ), done)
        recover(kind); changed()
    }

    private fun recover(kind: String) {
        val d = draft(kind)
        if (d.id.isEmpty() || d.status == "draft" || d.status == "confirmed") return
        watches.remove(kind)?.remove()
        val requestGeneration = generation; val id = d.id
        val watchVersion = (watchVersions[kind] ?: 0) + 1
        watchVersions[kind] = watchVersion
        // Tras reiniciar, la lectura puede llegar antes que la escritura recuperada.
        // Esperar la cola evita que las reglas rechacen un reporte todavía inexistente.
        repo.afterPendingWrites { queueError ->
            if (requestGeneration != generation || draft(kind).id != id ||
                watchVersions[kind] != watchVersion || draft(kind).status == "confirmed") return@afterPendingWrites
            if (queueError != null) {
                finish(kind, "failed", "No se pudo comprobar el envío: $queueError")
                return@afterPendingWrites
            }
            watches[kind] = repo.observeSubmission(if (kind == "pet") "pets" else "reports", id) { snap, error ->
                if (requestGeneration != generation || draft(kind).id != id || draft(kind).status == "confirmed") return@observeSubmission
                when {
                    snap?.metadata?.hasPendingWrites() == true -> finish(kind, "pending")
                    snap?.exists() == true && !snap.metadata.isFromCache && snap.getString("ownerId") == account -> finish(kind, "confirmed")
                    // Un fallo transitorio de lectura no demuestra que la escritura haya fallado.
                    error != null && kind !in activeWrites -> finish(kind, "failed", "No se pudo confirmar el envío. Reintenta con el mismo registro: $error")
                    snap != null && !snap.exists() && !snap.metadata.isFromCache && kind !in activeWrites ->
                        finish(kind, "failed", "El servidor no tiene este envío. Puedes reintentarlo sin crear otro ID.")
                }
            }
        }
    }

    private fun finish(kind: String, status: String, error: String = "") {
        val d = draft(kind)
        if (d.status == status && d.error == error) return
        d.status = status; d.error = error
        if (status == "confirmed") watches.remove(kind)?.remove()
        flush(); changed()
    }
    private fun changed() { changedValue.value = (changedValue.value ?: 0) + 1 }

    override fun onCleared() {
        flush(); generation++
        watches.values.forEach { it.remove() }
        repo.auth.removeAuthStateListener(authListener)
    }
}
