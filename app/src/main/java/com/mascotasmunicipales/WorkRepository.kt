package com.mascotasmunicipales

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*

const val MUNICIPAL_DEPARTMENT = "zipaquira-bienestar-animal"
data class WorkAccess(val role: String, val departmentId: String)

/** Operaciones profesionales en línea. Las transacciones comprueban conflictos y nunca se encolan sin conexión. */
class WorkRepository {
    private val db = FirebaseFirestore.getInstance()
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid ?: error("Inicia sesión")

    fun access(done: (WorkAccess?, String?) -> Unit) {
        db.collection("access").document(uid).get(Source.SERVER).addOnSuccessListener {
            val role = it.getString("role")
            if (it.getBoolean("active") == true && role in listOf("admin", "vet") &&
                it.getString("departmentId") == MUNICIPAL_DEPARTMENT)
                done(WorkAccess(role!!, MUNICIPAL_DEPARTMENT), null)
            else done(null, "Tu cuenta no tiene un acceso profesional activo. Solicita aprobación al responsable del prototipo.")
        }.addOnFailureListener { done(null, "No se pudo verificar el acceso en línea: ${it.localizedMessage}") }
    }

    fun page(kind: String, access: WorkAccess, after: DocumentSnapshot? = null,
             done: (List<DocumentSnapshot>?, String?) -> Unit) {
        var query: Query = when (kind) {
            "reports" -> db.collection("reports").orderBy("createdAt", Query.Direction.DESCENDING)
            "access" -> db.collection("access").whereEqualTo("departmentId", access.departmentId)
                .whereEqualTo("role", "vet").orderBy(FieldPath.documentId())
            "eventRequests" -> db.collection("events").whereEqualTo("departmentId", access.departmentId)
                .whereEqualTo("status", "Pendiente").orderBy("createdAt", Query.Direction.DESCENDING)
            "myEvents" -> db.collection("events").whereEqualTo("departmentId", access.departmentId)
                .whereEqualTo("authorId", uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
            else -> db.collection("cases").whereEqualTo("departmentId", access.departmentId).let {
                if (access.role == "vet") it.whereEqualTo("vetId", uid) else it
            }.orderBy("updatedAt", Query.Direction.DESCENDING)
        }
        if (after != null) query = query.startAfter(after)
        query.limit(20).get(Source.SERVER).addOnSuccessListener { done(it.documents, null) }
            .addOnFailureListener { done(null, it.localizedMessage) }
    }

    fun proposeEvent(title: String, type: String, territoryId: String, location: String,
                     description: String, scheduledAt: com.google.firebase.Timestamp, done: (String?) -> Unit) {
        val actor = uid
        val ref = db.collection("events").document()
        db.runTransaction { tx ->
            val permission = tx.get(db.collection("access").document(actor))
            check(permission.getBoolean("active") == true && permission.getString("role") == "vet" &&
                permission.getString("departmentId") == MUNICIPAL_DEPARTMENT) { "El acceso veterinario ya no está activo" }
            tx.set(ref, mapOf(
                "authorId" to actor, "departmentId" to MUNICIPAL_DEPARTMENT,
                "territoryId" to territoryId, "type" to type, "title" to title.trim(),
                "location" to location.trim(), "description" to description.trim(),
                "scheduledAt" to scheduledAt, "status" to "Pendiente", "reviewedBy" to "",
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
            ))
            null
        }.addOnSuccessListener { done(null) }.addOnFailureListener { done(it.localizedMessage) }
    }

    fun reviewEvent(id: String, approve: Boolean, done: (String?) -> Unit) {
        val actor = uid
        val ref = db.collection("events").document(id)
        db.runTransaction { tx ->
            val current = tx.get(ref)
            check(current.exists() && current.getString("status") == "Pendiente") {
                "La solicitud ya fue revisada. Actualiza la lista."
            }
            tx.update(ref, mapOf(
                "status" to if (approve) "Aprobado" else "Rechazado",
                "reviewedBy" to actor, "updatedAt" to FieldValue.serverTimestamp()
            ))
            null
        }.addOnSuccessListener { done(null) }.addOnFailureListener { done(it.localizedMessage) }
    }

    fun getCase(id: String, done: (DocumentSnapshot?, String?) -> Unit) {
        db.collection("cases").document(id).get(Source.SERVER)
            .addOnSuccessListener { done(it.takeIf { s -> s.exists() }, null) }
            .addOnFailureListener { done(null, it.localizedMessage) }
    }

    fun records(id: String, after: DocumentSnapshot? = null, done: (List<DocumentSnapshot>?, String?) -> Unit) {
        var q: Query = db.collection("cases").document(id).collection("clinicalRecords")
            .orderBy("createdAt", Query.Direction.DESCENDING)
        if (after != null) q = q.startAfter(after)
        q.limit(20).get(Source.SERVER).addOnSuccessListener { done(it.documents, null) }
            .addOnFailureListener { done(null, it.localizedMessage) }
    }

    fun review(reportId: String, done: (String?) -> Unit) {
        val actor = uid
        val ref = db.collection("cases").document(reportId)
        db.runTransaction { tx ->
            val report = tx.get(db.collection("reports").document(reportId))
            val existing = tx.get(ref)
            check(report.exists()) { "El reporte ya no existe" }
            if (!existing.exists()) {
                check(report.getString("status") == "Abierto") { "Solo se revisan reportes abiertos" }
                tx.set(ref, mapOf("reportId" to reportId, "ownerId" to report.getString("ownerId"),
                    "petId" to report.getString("petId"), "petName" to report.getString("petName"),
                    "species" to report.getString("species"), "territoryId" to report.getString("territoryId"),
                    "departmentId" to MUNICIPAL_DEPARTMENT, "vetId" to "", "status" to "Revisado",
                    "outcome" to "", "lastRecordId" to "", "version" to 1,
                    "updatedBy" to actor, "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()))
                tx.set(ref.collection("history").document("1"), event(actor, "Revisado", 1, ""))
            }
            null
        }.addOnSuccessListener { done(null) }.addOnFailureListener { done(it.localizedMessage) }
    }

    private fun event(actor: String, status: String, version: Long, vetId: String) = mapOf(
        "actorId" to actor, "status" to status, "version" to version, "vetId" to vetId, "createdAt" to FieldValue.serverTimestamp())

    /** expectedVersion evita sobrescribir una decisión realizada desde otro dispositivo. */
    fun change(id: String, expectedVersion: Long, status: String, vetId: String = "", outcome: String = "",
               examination: String = "", care: String = "", followUp: String = "", done: (String?) -> Unit) {
        val actor = uid
        val ref = db.collection("cases").document(id)
        val note = ref.collection("clinicalRecords").document()
        db.runTransaction { tx ->
            val old = tx.get(ref)
            check(old.getLong("version") == expectedVersion) { "El caso cambió. Actualiza antes de continuar." }
            val version = expectedVersion + 1
            val changes = mutableMapOf<String, Any>("status" to status, "version" to version,
                "updatedBy" to actor, "updatedAt" to FieldValue.serverTimestamp())
            if (status == "Asignado") changes["vetId"] = vetId.trim()
            if (status == "Atendido") {
                changes["lastRecordId"] = note.id
                tx.set(note, mapOf("authorId" to actor, "examination" to examination.trim(),
                    "care" to care.trim(), "followUp" to followUp.trim(), "createdAt" to FieldValue.serverTimestamp()))
            }
            if (status == "Cerrado") {
                changes["outcome"] = outcome.trim()
                tx.update(db.collection("reports").document(id), mapOf("status" to "Cerrado", "updatedAt" to FieldValue.serverTimestamp()))
            }
            tx.update(ref, changes)
            tx.set(ref.collection("history").document(version.toString()), event(actor, status, version,
                if (status == "Asignado") vetId.trim() else old.getString("vetId").orEmpty()))
            null
        }.addOnSuccessListener { done(null) }.addOnFailureListener { done(it.localizedMessage) }
    }
}
