package com.mascotasmunicipales

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query

/** Único punto de acceso a Authentication y Firestore. Los Task terminan al recibir respuesta del servidor. */
class MunicipalRepository {
    val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    val uid: String? get() = auth.currentUser?.uid
    val email: String get() = auth.currentUser?.email.orEmpty()

    fun register(email: String, password: String, done: (String?) -> Unit) {
        auth.createUserWithEmailAndPassword(email.trim(), password).addOnSuccessListener {
            done(null)
        }.addOnFailureListener { done(it.localizedMessage ?: "No se pudo registrar") }
    }

    fun login(email: String, password: String, done: (String?) -> Unit) {
        auth.signInWithEmailAndPassword(email.trim(), password).addOnSuccessListener {
            done(null)
        }.addOnFailureListener { done(it.localizedMessage ?: "No se pudo ingresar") }
    }

    fun ensureProfile(done: (String?) -> Unit) {
        val user = auth.currentUser ?: return done("Inicia sesión")
        val ref = db.collection("users").document(user.uid)
        ref.get().addOnSuccessListener { snap ->
            if (snap.exists() && !snap.metadata.hasPendingWrites()) done(null)
            else db.runTransaction { transaction ->
                // Dos dispositivos pueden iniciar la misma cuenta simultáneamente.
                if (!transaction.get(ref).exists()) transaction.set(ref, mapOf(
                    "email" to user.email.orEmpty(), "role" to "citizen",
                    "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
                ))
                null
            }.addOnSuccessListener { done(null) }
                .addOnFailureListener { done(it.localizedMessage ?: "No se pudo crear el perfil") }
        }.addOnFailureListener { done(it.localizedMessage ?: "No se pudo consultar el perfil") }
    }

    fun logout() = auth.signOut()

    fun observePets(limit: Long = 30, done: (List<Pet>, Boolean, String?) -> Unit): ListenerRegistration {
        require(limit in 1L..30L) { "El límite de mascotas debe estar entre 1 y 30" }
        return db.collection("pets").orderBy("createdAt", Query.Direction.DESCENDING).limit(limit)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) done(emptyList(), false, error.localizedMessage)
                else if (snapshot != null) done(snapshot.documents.map(::pet), snapshot.metadata.isFromCache, null)
            }
    }

    fun observeMyReports(done: (List<Report>, Boolean, String?) -> Unit): ListenerRegistration {
        val id = uid ?: error("Inicia sesión")
        return db.collection("reports").whereEqualTo("ownerId", id)
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(30)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) done(emptyList(), false, error.localizedMessage)
                else if (snapshot != null) done(snapshot.documents.map(::report), snapshot.metadata.isFromCache, null)
            }
    }

    fun observePet(id: String, done: (Pet?, Boolean, String?) -> Unit): ListenerRegistration =
        db.collection("pets").document(id).addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
            done(if (snap?.exists() == true) pet(snap) else null, snap?.metadata?.isFromCache ?: false, error?.localizedMessage)
        }

    fun observeReport(id: String, done: (Report?, Boolean, String?) -> Unit): ListenerRegistration =
        db.collection("reports").document(id).addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
            done(if (snap?.exists() == true) report(snap) else null, snap?.metadata?.isFromCache ?: false, error?.localizedMessage)
        }

    fun observeSubmission(collection: String, id: String,
        done: (DocumentSnapshot?, String?) -> Unit): ListenerRegistration =
        db.collection(collection).document(id).addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
            done(snap, error?.let { it.localizedMessage ?: "No se pudo comprobar el envío" })
        }

    fun afterPendingWrites(done: (String?) -> Unit) {
        db.waitForPendingWrites().addOnSuccessListener { done(null) }
            .addOnFailureListener { done(it.localizedMessage ?: "No se pudo comprobar la cola de envíos") }
    }

    fun createPet(id: String, p: Pet, done: (String?) -> Unit) {
        val owner = uid ?: return done("Inicia sesión")
        val ref = db.collection("pets").document(id)
        ref.set(mapOf(
            "ownerId" to owner, "name" to p.name.trim(), "species" to p.species,
            "breed" to p.breed.trim(), "sex" to p.sex, "age" to p.age.trim(),
            "color" to p.color.trim(), "territoryId" to p.territoryId,
            "status" to "Con responsable", "photoKey" to "", "qrCode" to "ZPQ-${ref.id.take(8).uppercase()}",
            "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
        )).addOnSuccessListener { done(null) }.addOnFailureListener { done(it.localizedMessage ?: "No se pudo guardar el registro") }
    }

    fun createReport(id: String, r: Report, done: (String?) -> Unit) {
        val owner = uid ?: return done("Inicia sesión")
        db.collection("reports").document(id).set(mapOf(
            "ownerId" to owner, "petId" to r.petId, "petName" to r.petName.trim(),
            "type" to r.type, "species" to r.species, "territoryId" to r.territoryId,
            "description" to r.description.trim(), "status" to "Abierto",
            "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
        )).addOnSuccessListener { done(null) }.addOnFailureListener { done(it.localizedMessage ?: "No se pudo guardar el registro") }
    }

    fun updateReportStatus(id: String, status: String, done: (String?) -> Unit) {
        db.collection("reports").document(id).update(mapOf(
            "status" to status, "updatedAt" to FieldValue.serverTimestamp()
        )).addOnSuccessListener { done(null) }.addOnFailureListener { done(it.localizedMessage ?: "No se pudo guardar el registro") }
    }

    fun petCount(done: (Long?, String?) -> Unit) {
        db.collection("pets").limit(1000).count().get(AggregateSource.SERVER)
            .addOnSuccessListener { done(it.count, null) }.addOnFailureListener { done(null, it.localizedMessage) }
    }

    fun activeReportCount(done: (Long?, String?) -> Unit) {
        val id = uid ?: return done(null, "Inicia sesión")
        db.collection("reports").whereEqualTo("ownerId", id).whereEqualTo("status", "Abierto").limit(1000)
            .count().get(AggregateSource.SERVER)
            .addOnSuccessListener { done(it.count, null) }.addOnFailureListener { done(null, it.localizedMessage) }
    }

    /** Lectura pública acotada; una consulta al abrir Territorio evita un listener permanente. */
    fun approvedEvents(done: (List<MunicipalEvent>, Boolean, String?) -> Unit) {
        db.collection("events").whereEqualTo("status", "Aprobado")
            .whereGreaterThanOrEqualTo("scheduledAt", com.google.firebase.Timestamp.now())
            .orderBy("scheduledAt", Query.Direction.ASCENDING).limit(20).get()
            .addOnSuccessListener { snapshot ->
                done(snapshot.documents.map { s -> MunicipalEvent(
                    id = s.id, authorId = s.getString("authorId").orEmpty(),
                    territoryId = s.getString("territoryId").orEmpty(), type = s.getString("type").orEmpty(),
                    title = s.getString("title").orEmpty(), location = s.getString("location").orEmpty(),
                    description = s.getString("description").orEmpty(), scheduledAt = s.getTimestamp("scheduledAt"),
                    status = s.getString("status").orEmpty(), createdAt = s.getTimestamp("createdAt")
                ) }, snapshot.metadata.isFromCache, null)
            }.addOnFailureListener { done(emptyList(), false, it.localizedMessage) }
    }

    private fun pet(s: DocumentSnapshot) = Pet(
        id = s.id, ownerId = s.getString("ownerId").orEmpty(), name = s.getString("name").orEmpty(),
        species = s.getString("species").orEmpty(), breed = s.getString("breed").orEmpty(),
        sex = s.getString("sex").orEmpty(), age = s.getString("age").orEmpty(),
        color = s.getString("color").orEmpty(), territoryId = s.getString("territoryId").orEmpty(),
        status = s.getString("status").orEmpty(), photoKey = s.getString("photoKey").orEmpty(),
        qrCode = s.getString("qrCode").orEmpty(), createdAt = s.getTimestamp("createdAt"),
        updatedAt = s.getTimestamp("updatedAt"), pending = s.metadata.hasPendingWrites()
    )

    private fun report(s: DocumentSnapshot) = Report(
        id = s.id, ownerId = s.getString("ownerId").orEmpty(), petId = s.getString("petId").orEmpty(),
        petName = s.getString("petName").orEmpty(), type = s.getString("type").orEmpty(),
        species = s.getString("species").orEmpty(), territoryId = s.getString("territoryId").orEmpty(),
        description = s.getString("description").orEmpty(), status = s.getString("status").orEmpty(),
        createdAt = s.getTimestamp("createdAt"), updatedAt = s.getTimestamp("updatedAt"),
        pending = s.metadata.hasPendingWrites()
    )
}
