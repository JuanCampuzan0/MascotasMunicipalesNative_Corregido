package com.mascotasmunicipales

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import java.text.DateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val repo = MunicipalRepository()
    private val teal = Color.rgb(20, 127, 149)
    private val bg = Color.rgb(241, 246, 248)
    private val dark = Color.rgb(32, 49, 58)
    private val gray = Color.rgb(105, 120, 128)
    private lateinit var content: FrameLayout
    private lateinit var nav: LinearLayout
    private val listeners = mutableListOf<ListenerRegistration>()
    private var screenGeneration = 0
    private var tab = 0
    private var syncMessage = ""
    private val authListener = FirebaseAuth.AuthStateListener { showApp() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo.auth.addAuthStateListener(authListener)
    }
    override fun onDestroy() {
        clearListeners(); repo.auth.removeAuthStateListener(authListener); super.onDestroy()
    }
    private fun clearListeners() { listeners.forEach { it.remove() }; listeners.clear() }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun tv(value: String, size: Float = 14f, bold: Boolean = false, color: Int = dark) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setPadding(dp(2), dp(2), dp(2), dp(2))
    }
    private fun shape() = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(16).toFloat() }
    private fun root() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; background = shape(); setPadding(dp(14), dp(12), dp(14), dp(12))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(10), dp(6), dp(10), dp(6)) }
    }
    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 13f; setTextColor(Color.WHITE); setBackgroundColor(teal)
        typeface = Typeface.DEFAULT_BOLD; setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(50)).apply { setMargins(dp(10), dp(7), dp(10), dp(7)) }
    }
    private fun field(hintValue: String, maxLength: Int = 2000) = EditText(this).apply {
        hint = hintValue; background = shape(); setPadding(dp(14), dp(10), dp(14), dp(10))
        filters = arrayOf(InputFilter.LengthFilter(maxLength))
        layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { setMargins(dp(10), dp(5), dp(10), dp(5)) }
    }
    private fun select(values: List<String>) = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, values)
        layoutParams = LinearLayout.LayoutParams(-1, dp(50))
    }
    private fun scroll(v: View) = ScrollView(this).apply { addView(v); isFillViewport = true }
    private fun header(title: String, subtitle: String = "") = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setBackgroundColor(teal); setPadding(dp(20), dp(18), dp(20), dp(18))
        addView(tv(title, 21f, true, Color.WHITE))
        if (subtitle.isNotBlank()) addView(tv(subtitle, 12f, false, Color.rgb(220, 240, 245)))
    }
    private fun show(v: View) { screenGeneration++; clearListeners(); content.removeAllViews(); content.addView(v) }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    private fun date(t: com.google.firebase.Timestamp?) = t?.toDate()?.let {
        DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.forLanguageTag("es-CO")).format(it)
    } ?: "Pendiente"
    private fun state(pending: Boolean, cache: Boolean) = when {
        pending -> "Pendiente de sincronización"
        cache -> "Dato en caché · sin confirmación reciente"
        else -> "Sincronizado"
    }
    private fun showApp() {
        clearListeners()
        val r = root(); content = FrameLayout(this)
        r.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        nav = LinearLayout(this).apply { setBackgroundColor(Color.WHITE); setPadding(dp(4), dp(4), dp(4), dp(4)) }
        listOf("Inicio", "Reportes", "Mascotas", "Territorio", "Perfil").forEachIndexed { i, label ->
            nav.addView(tv("${listOf("⌂", "▣", "♥", "⌖", "⚙")[i]}\n$label", 11f, false, if (i == tab) teal else gray).apply {
                gravity = Gravity.CENTER; setOnClickListener { tab = i; renderTab() }
            }, LinearLayout.LayoutParams(0, dp(60), 1f))
        }
        r.addView(nav); setContentView(r)
        if (repo.uid == null) authScreen() else renderTab()
    }
    private fun renderTab() {
        if (repo.uid == null) return authScreen()
        (0 until nav.childCount).forEach { (nav.getChildAt(it) as TextView).setTextColor(if (it == tab) teal else gray) }
        when (tab) { 0 -> home(); 1 -> reports(); 2 -> pets(); 3 -> territory(); else -> profile() }
    }
    private fun authScreen() {
        val c = root(); c.addView(header("🐾 Mascotas Municipales", "Registro e ingreso · Zipaquirá"))
        val email = field("Correo electrónico"); c.addView(email)
        val password = field("Contraseña (mínimo 6 caracteres)").apply { inputType = 129 }; c.addView(password)
        val message = tv("Firebase Authentication protege la contraseña; la aplicación no la guarda.", 12f, false, gray)
        c.addView(message)
        c.addView(button("INGRESAR") {
            repo.login(email.text.toString(), password.text.toString()) { error ->
                if (error == null) showApp() else message.text = error
            }
        })
        c.addView(button("CREAR CUENTA") {
            if (password.text.length < 6) message.text = "La contraseña debe tener al menos 6 caracteres."
            else repo.register(email.text.toString(), password.text.toString()) { error ->
                if (error == null) showApp() else message.text = error
            }
        })
        show(scroll(c))
    }
    private fun home() {
        val c = root(); c.addView(header("🐾 Mascotas Municipales", "Prototipo académico · Zipaquirá"))
        c.addView(tv("Firestore conserva cambios locales sin conexión. Una escritura se confirma al responder el servidor.", 12f, false, Color.WHITE).apply {
            setBackgroundColor(teal); setPadding(dp(20), dp(10), dp(20), dp(10))
        })
        c.addView(tv("Panorama del prototipo", 16f, true))
        val petsNumber = tv("Cargando…", 22f, true, teal)
        val reportsNumber = tv("Cargando…", 22f, true, teal)
        c.addView(card().apply { addView(petsNumber); addView(tv("Mascotas registradas · hasta 1000")) })
        c.addView(card().apply { addView(reportsNumber); addView(tv("Mis reportes abiertos")) })
        repo.petCount { count, _ -> petsNumber.text = count?.toString() ?: "Sin conexión" }
        repo.activeReportCount { count, _ -> reportsNumber.text = count?.toString() ?: "Sin conexión" }
        c.addView(button("VER MASCOTAS") { tab = 2; renderTab() })
        c.addView(button("NUEVO REPORTE") { newReport() })
        c.addView(tv("Registros recientes · máximo 30", 15f, true))
        val list = root(); c.addView(list)
        show(scroll(c))
        listeners.add(repo.observePets(3) { pets, cache, error ->
            list.removeAllViews()
            if (error != null) list.addView(tv("Error: $error"))
            else if (pets.isEmpty()) list.addView(tv(if (cache) "Sin registros en caché; conecta para verificar." else "Todavía no hay mascotas registradas."))
            else pets.forEach { list.addView(petCard(it, cache)) }
        })
    }
    private fun petCard(p: Pet, cache: Boolean) = card().apply {
        orientation = LinearLayout.HORIZONTAL; setOnClickListener { petDetail(p.id) }
        val res = photoResource(p.photoKey)
        if (res != 0) addView(ImageView(this@MainActivity).apply {
            setImageResource(res); scaleType = ImageView.ScaleType.CENTER_CROP
        }, LinearLayout.LayoutParams(dp(72), dp(72)))
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0)
            addView(tv(p.name, 16f, true))
            addView(tv("${p.species} · ${p.breed}", 12f, false, gray))
            addView(tv(territoryLabel(p.territoryId), 11f, false, gray))
            addView(tv(state(p.pending, cache), 11f, false, teal))
        })
    }
    private fun pets() {
        val c = root(); c.addView(header("Directorio de mascotas", "Registros públicos · 30 más recientes"))
        c.addView(button("+ REGISTRAR MASCOTA") { newPet() })
        val list = root(); c.addView(list); show(scroll(c))
        listeners.add(repo.observePets { pets, cache, error ->
            list.removeAllViews()
            if (error != null) list.addView(tv("Error: $error"))
            else if (pets.isEmpty()) list.addView(tv(if (cache) "Sin registros en caché; conecta para verificar." else "Todavía no hay mascotas registradas."))
            else pets.forEach { list.addView(petCard(it, cache)) }
        })
    }
    private fun newPet() {
        val c = root(); c.addView(header("Registrar mascota", "Datos públicos mínimos · sin subir fotografías"))
        val name = field("Nombre", 80); c.addView(name)
        val species = select(listOf("Perro", "Gato")); c.addView(tv("Especie")); c.addView(species)
        val breed = field("Raza", 80); c.addView(breed)
        val sex = select(listOf("Hembra", "Macho", "No determinado")); c.addView(tv("Sexo")); c.addView(sex)
        val age = field("Edad aproximada", 40); c.addView(age)
        val color = field("Color", 80); c.addView(color)
        val territory = select(TERRITORIES.map { it.label }); c.addView(tv("Comuna")); c.addView(territory)
        val message = tv("Las fotos incluidas son solo demostrativas.", 12f, false, gray); c.addView(message)
        var saving = false
        var formGeneration = 0
        c.addView(button("GUARDAR MASCOTA") {
            if (saving) return@button
            if (name.text.isBlank() || breed.text.isBlank() || age.text.isBlank() || color.text.isBlank()) {
                message.text = "Completa nombre, raza, edad y color."; return@button
            }
            saving = true
            message.text = "Pendiente de sincronización…"
            repo.createPet(Pet(name = name.text.toString(), species = species.selectedItem.toString(),
                breed = breed.text.toString(), sex = sex.selectedItem.toString(), age = age.text.toString(),
                color = color.text.toString(), territoryId = TERRITORIES[territory.selectedItemPosition].id)) { error ->
                if (error == null) {
                    toast("Mascota confirmada por el servidor")
                    if (screenGeneration == formGeneration) pets()
                } else {
                    saving = false
                    if (screenGeneration == formGeneration) message.text = "Falló el guardado: $error"
                    toast("Falló el guardado: $error")
                }
            }
        })
        show(scroll(c))
        formGeneration = screenGeneration
    }
    private fun petDetail(id: String) {
        val c = root(); c.addView(header("Detalle Mascota", "Ficha pública"))
        val body = root(); c.addView(body); show(scroll(c))
        listeners.add(repo.observePet(id) { p, cache, error ->
            body.removeAllViews()
            if (error != null || p == null) {
                body.addView(tv(error ?: if (cache) "Sin datos en caché; conecta para consultar." else "No existe la mascota"))
                return@observePet
            }
            val res = photoResource(p.photoKey)
            if (res != 0) body.addView(ImageView(this).apply {
                setImageResource(res); scaleType = ImageView.ScaleType.CENTER_CROP
            }, LinearLayout.LayoutParams(-1, dp(190)))
            body.addView(card().apply {
                addView(tv(p.name, 24f, true)); addView(tv("${p.species} · ${p.breed}"))
                addView(tv("Sexo: ${p.sex}\nEdad: ${p.age}\nColor: ${p.color}\nTerritorio: ${territoryLabel(p.territoryId)}\nEstado: ${p.status}\nCódigo: ${p.qrCode}"))
                addView(tv(state(p.pending, cache), 12f, true, teal))
            })
            body.addView(card().apply { addView(tv("🔒 Datos del responsable protegidos")); addView(tv("Esta ficha no contiene teléfono, dirección ni correo.")) })
            body.addView(button("← VOLVER") { pets() })
        })
    }
    private fun reports() {
        val c = root(); c.addView(header("Reportes", "Propios o de funcionario · 30 más recientes"))
        c.addView(button("+ NUEVO REPORTE") { newReport() })
        val list = root(); c.addView(list); show(scroll(c))
        val generation = screenGeneration
        val requestedUid = repo.uid
        repo.role { role ->
            if (generation == screenGeneration && requestedUid == repo.uid) {
                val display: (List<Report>, Boolean, String?) -> Unit = { reports, cache, error ->
                    list.removeAllViews()
                    if (error != null) list.addView(tv("Error: $error"))
                    else if (reports.isEmpty()) list.addView(tv(if (cache) "Sin reportes en caché; conecta para verificar." else "Todavía no hay reportes visibles."))
                    else reports.forEach { r -> list.addView(card().apply {
                        setOnClickListener { reportDetail(r.id) }
                        addView(tv("${r.petName.ifBlank { "Mascota sin identificar" }} · ${r.type}", 15f, true))
                        addView(tv("${r.status} · ${date(r.createdAt)}", 12f, false, gray))
                        addView(tv(state(r.pending, cache), 11f, false, teal))
                    }) }
                }
                listeners.add(if (role == "staff") repo.observeStaffReports(display) else repo.observeMyReports(display))
            }
        }
    }
    private fun newReport() {
        val c = root(); c.addView(header("Nuevo Reporte", "Los datos se guardan en Firestore"))
        c.addView(tv("📷 Foto: función simulada. No se suben imágenes.", 12f, false, gray))
        val name = field("Nombre de mascota (si se conoce)", 80); c.addView(name)
        val type = select(listOf("Pérdida", "Encontrado", "Avistamiento")); c.addView(tv("Tipo de reporte")); c.addView(type)
        val species = select(listOf("Perro", "Gato")); c.addView(tv("Especie")); c.addView(species)
        val territory = select(TERRITORIES.map { it.label }); c.addView(tv("Ubicación (comuna)")); c.addView(territory)
        val desc = field("Describe lo que observaste", 2000).apply { minLines = 4; layoutParams.height = dp(110); gravity = Gravity.TOP }; c.addView(desc)
        c.addView(tv("No incluyas datos personales de terceros.", 11f, false, gray))
        val message = tv("", 12f, false, teal); c.addView(message)
        var saving = false
        var formGeneration = 0
        c.addView(button("ENVIAR REPORTE") {
            if (saving) return@button
            if (desc.text.isBlank()) { message.text = "Escribe una descripción."; return@button }
            saving = true
            message.text = "Pendiente de sincronización…"
            repo.createReport(Report(petName = name.text.toString(), type = type.selectedItem.toString(),
                species = species.selectedItem.toString(), territoryId = TERRITORIES[territory.selectedItemPosition].id,
                description = desc.text.toString())) { error ->
                if (error == null) {
                    toast("Reporte confirmado por el servidor")
                    if (screenGeneration == formGeneration) reports()
                } else {
                    saving = false
                    if (screenGeneration == formGeneration) message.text = "Falló el envío: $error"
                    toast("Falló el envío: $error")
                }
            }
        })
        show(scroll(c))
        formGeneration = screenGeneration
    }
    private fun reportDetail(id: String) {
        val c = root(); c.addView(header("Detalle del reporte", "Seguimiento"))
        val body = root(); c.addView(body); show(scroll(c))
        listeners.add(repo.observeReport(id) { r, cache, error ->
            body.removeAllViews()
            if (error != null || r == null) {
                body.addView(tv(error ?: if (cache) "Sin datos en caché; conecta para consultar." else "No existe el reporte"))
                return@observeReport
            }
            body.addView(card().apply {
                addView(tv("${r.petName.ifBlank { "Mascota sin identificar" }} · ${r.type}", 20f, true))
                addView(tv("Especie: ${r.species}\nComuna: ${territoryLabel(r.territoryId)}\nDescripción: ${r.description}\nEstado: ${r.status}\nFecha: ${date(r.createdAt)}"))
                addView(tv(state(r.pending, cache), 12f, true, teal))
            })
            if (r.status == "Abierto") body.addView(button("MARCAR RESUELTO") {
                syncMessage = "Pendiente de sincronización"
                repo.updateReportStatus(r.id, "Resuelto") { failure ->
                    syncMessage = if (failure == null) "Sincronizado" else "Falló: $failure"
                    toast(syncMessage)
                }
            })
            body.addView(button("← VOLVER") { reports() })
        })
    }
    private fun territory() {
        val c = root(); c.addView(header("Territorio", "Comunas del prototipo"))
        c.addView(card().apply { addView(tv("Zipaquirá", 20f, true)); addView(tv("División usada para clasificar registros. Sin cifras municipales verificadas.")) })
        TERRITORIES.forEach { c.addView(card().apply { addView(tv(it.label, 14f, true)) }) }
        show(scroll(c))
    }
    private fun profile() {
        val c = root(); c.addView(header("Perfil y ajustes", "Cuenta y sincronización"))
        val account = card(); account.addView(tv(repo.email, 16f, true))
        val role = tv("Consultando rol…", 12f, false, gray); account.addView(role); c.addView(account)
        repo.role { role.text = when (it) {
            "staff" -> "Rol: Funcionario"
            "citizen" -> "Rol: Ciudadano"
            else -> "Rol no disponible; conecta para verificar."
        } }
        c.addView(card().apply {
            addView(tv("🔄 Datos y sincronización", 14f, true))
            addView(tv("Los registros indican pendiente, sincronizado o caché. Una escritura sin red sigue pendiente hasta que responda el servidor. $syncMessage", 12f, false, gray))
        })
        c.addView(card().apply { addView(tv("Jornadas, vacunación, adopciones y lectura real de QR: funciones simuladas, fuera del backend actual.", 12f, false, gray)) })
        c.addView(button("CERRAR SESIÓN") { repo.logout(); tab = 0; showApp() })
        show(scroll(c))
    }
}
