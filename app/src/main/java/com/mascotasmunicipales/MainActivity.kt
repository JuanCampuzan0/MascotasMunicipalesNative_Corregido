package com.mascotasmunicipales

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.widget.*
import android.util.LruCache
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.activity.OnBackPressedCallback
import androidx.core.widget.doAfterTextChanged
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.firebase.firestore.ListenerRegistration
import java.text.DateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var model: AppViewModel
    private val repo get() = model.repo
    private val teal get() = when {
        highContrast -> Color.BLACK
        colorblindPalette -> Color.rgb(0, 82, 155)
        else -> Color.rgb(20, 127, 149)
    }
    private val bg get() = if (highContrast) Color.WHITE else Color.rgb(241, 246, 248)
    private val dark get() = if (highContrast) Color.BLACK else Color.rgb(32, 49, 58)
    private val gray get() = if (highContrast) Color.BLACK else Color.rgb(85, 100, 109)
    private val border get() = if (highContrast) Color.BLACK else Color.rgb(214, 227, 232)
    private val softTeal get() = when {
        highContrast -> Color.WHITE
        colorblindPalette -> Color.rgb(229, 239, 249)
        else -> Color.rgb(226, 241, 244)
    }
    private val tealDark get() = when {
        highContrast -> Color.BLACK
        colorblindPalette -> Color.rgb(0, 69, 120)
        else -> Color.rgb(14, 97, 114)
    }
    private val textScale get() = if (largeText) 1.3f else 1f
    private val accessibilityPreferences by lazy { getSharedPreferences("accessibility", MODE_PRIVATE) }
    private var largeText = false
    private var highContrast = false
    private var colorblindPalette = false
    private var accessibilityOpen = false
    private var accessibilityScrollY = 0
    private lateinit var content: FrameLayout
    private lateinit var nav: LinearLayout
    private val listeners = mutableListOf<ListenerRegistration>()
    private var screenGeneration = 0
    private var tab: Int
        get() = model.tab
        set(value) { model.tab = value }
    private var syncMessage = ""
    private var updateDraftUi: (() -> Unit)? = null
    private var updateAuthUi: ((SessionState) -> Unit)? = null
    private val work = WorkRepository()
    private val photoCache = object : LruCache<Int, Bitmap>(4096) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount / 1024
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        largeText = accessibilityPreferences.getBoolean("large_text", false)
        highContrast = accessibilityPreferences.getBoolean("high_contrast", false)
        colorblindPalette = accessibilityPreferences.getBoolean("colorblind_palette", false)
        accessibilityOpen = savedInstanceState?.getBoolean("accessibilityOpen") ?: false
        accessibilityScrollY = savedInstanceState?.getInt("accessibilityScrollY") ?: 0
        model = ViewModelProvider(this)[AppViewModel::class.java]
        model.session.observe(this) { session ->
            val authUi = updateAuthUi
            if (session.phase in listOf("signedOut", "authenticating") && authUi != null) authUi(session)
            else showApp()
        }
        model.draftsChanged.observe(this) { updateDraftUi?.invoke() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (accessibilityOpen) {
                    accessibilityOpen = false
                    if (model.ready) profileMenu() else authScreen()
                    return
                }
                if (!model.ready || model.screen == "home") finish()
                else renderRoute(when {
                    model.screen == "profile" -> model.previousScreen
                    model.screen == "newPet" || model.screen.startsWith("pet:") -> "pets"
                    model.screen == "newReport" || model.screen.startsWith("report:") -> "reports"
                    else -> "home"
                })
            }
        })
    }
    override fun onPause() { model.flush(); super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) {
        model.flush()
        outState.putBoolean("accessibilityOpen", accessibilityOpen)
        outState.putInt("accessibilityScrollY", (content.getChildAt(0) as? ScrollView)?.scrollY ?: 0)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() { clearListeners(); super.onDestroy() }
    private fun remember(route: String, selectedTab: Int = tab) {
        accessibilityOpen = route == "accessibility"
        tab = selectedTab; model.navigate(route)
        updateNavigation()
    }
    private fun updateNavigation() {
        if (!::nav.isInitialized) return
        (0 until nav.childCount).forEach {
            (nav.getChildAt(it) as LinearLayout).apply {
                val selected = model.ready && it == tab && !accessibilityOpen
                background = if (selected) GradientDrawable().apply {
                    setColor(if (highContrast) Color.BLACK else softTeal)
                    cornerRadius = dp(14).toFloat()
                } else null
                val icon = getChildAt(0) as ImageView
                val label = getChildAt(1) as TextView
                val color = if (selected && highContrast) Color.WHITE else if (selected) teal else gray
                icon.imageTintList = ColorStateList.valueOf(color)
                label.setTextColor(color); label.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                contentDescription = if (selected) "${label.text}, pestaña seleccionada" else "${label.text}, abrir pestaña"
            }
        }
    }
    private fun renderRoute(route: String) {
        if (!model.ready) return
        when {
            route == "work" -> workHome()
            route.startsWith("workcase:") -> workCase(route.removePrefix("workcase:"))
            route == "accessibility" -> accessibilityScreen()
            route == "newPet" -> newPet()
            route == "newReport" -> newReport()
            route.startsWith("pet:") -> petDetail(route.removePrefix("pet:"))
            route.startsWith("report:") -> reportDetail(route.removePrefix("report:"))
            route == "pets" -> pets()
            route == "reports" -> reports()
            route == "territory" -> territory()
            route == "profile" -> profileMenu()
            else -> home()
        }
    }
    private fun clearListeners() { listeners.forEach { it.remove() }; listeners.clear() }
    private fun setPhoto(view: ImageView, resource: Int) {
        val bitmap = photoCache[resource] ?: BitmapFactory.decodeResource(resources, resource,
            BitmapFactory.Options().apply { inSampleSize = 2 })?.also { photoCache.put(resource, it) }
        if (bitmap != null) view.setImageBitmap(bitmap) else view.setImageResource(resource)
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun tv(value: String, size: Float = 14f, bold: Boolean = false, color: Int = dark) = TextView(this).apply {
        text = value; textSize = size * textScale; setTextColor(color)
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setPadding(dp(2), dp(3), dp(2), dp(3))
        includeFontPadding = true
    }
    private fun heading(value: String, size: Float = 16f, color: Int = dark) = tv(value, size, true, color).apply {
        ViewCompat.setAccessibilityHeading(this, true)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(16), dp(18), dp(16), dp(6)) }
    }
    private fun rounded(fill: Int, radius: Int = 16, stroke: Int = border, strokeWidth: Int = 1) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(radius).toFloat()
        if (strokeWidth > 0) setStroke(dp(if (highContrast) 2 else strokeWidth), stroke)
    }
    private fun shape() = rounded(Color.WHITE)
    private fun root() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setBackgroundColor(bg); setPadding(0, 0, 0, dp(16))
    }
    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; background = shape(); setPadding(dp(16), dp(14), dp(16), dp(14))
        elevation = if (highContrast) 0f else dp(2).toFloat()
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(12), dp(6), dp(12), dp(6)) }
    }
    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 13f * textScale; setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD; setOnClickListener { action() }
        isAllCaps = false; minHeight = dp(52); stateListAnimator = null
        val colors = ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(if (highContrast) Color.LTGRAY else Color.rgb(139, 184, 193), teal)
        )
        background = RippleDrawable(ColorStateList.valueOf(Color.argb(55, 255, 255, 255)),
            GradientDrawable().apply { setColor(colors); cornerRadius = dp(14).toFloat() }, rounded(Color.WHITE, 14, Color.TRANSPARENT, 0))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(12), dp(7), dp(12), dp(7)) }
    }
    private fun secondaryButton(label: String, action: () -> Unit) = button(label, action).apply {
        setTextColor(teal)
        background = RippleDrawable(ColorStateList.valueOf(Color.argb(35, 20, 127, 149)),
            rounded(if (highContrast) Color.WHITE else softTeal, 14, teal), rounded(Color.WHITE, 14, Color.TRANSPARENT, 0))
    }
    private fun field(hintValue: String, maxLength: Int = 2000) = EditText(this).apply {
        hint = hintValue; background = rounded(Color.WHITE, 13); setPadding(dp(14), dp(11), dp(14), dp(11))
        textSize = 16f * textScale; minHeight = dp(52); setTextColor(dark); setHintTextColor(gray)
        filters = arrayOf(InputFilter.LengthFilter(maxLength))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(12), dp(5), dp(12), dp(5)) }
    }
    private fun select(values: List<String>) = Spinner(this).apply {
        id = View.generateViewId()
        adapter = object : ArrayAdapter<String>(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, values) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
                super.getView(position, convertView, parent).apply {
                    (this as? TextView)?.textSize = 16f * textScale
                    minimumHeight = dp(48)
                }
            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
                super.getDropDownView(position, convertView, parent).apply {
                    (this as? TextView)?.textSize = 16f * textScale
                    minimumHeight = dp(48)
                }
        }
        minimumHeight = dp(52); background = rounded(Color.WHITE, 13)
        setPadding(dp(8), 0, dp(8), 0)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(12), dp(4), dp(12), dp(7)) }
    }
    private fun spinnerLabel(label: String, spinner: Spinner) = tv(label, 13f, true, gray).apply {
        labelFor = spinner.id
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(14), dp(8), dp(14), 0) }
    }
    private fun scroll(v: View) = ScrollView(this).apply {
        addView(v); isFillViewport = true; clipToPadding = false; overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
    }
    private fun header(title: String, subtitle: String = "") = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(tealDark, teal)).apply {
            cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, dp(22).toFloat(), dp(22).toFloat(), dp(22).toFloat(), dp(22).toFloat())
        }
        elevation = if (highContrast) 0f else dp(3).toFloat()
        setPadding(dp(20), dp(20), dp(16), dp(20))
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(heading(title, 22f, Color.WHITE).apply { layoutParams = LinearLayout.LayoutParams(-1, -2) })
            if (subtitle.isNotBlank()) addView(tv(subtitle, 12f, false, Color.WHITE).apply { alpha = .88f })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (model.ready) {
            val inProfile = model.screen in listOf("profile", "accessibility")
            addView(tv(if (inProfile) "Volver" else "Perfil", 12f, true, teal).apply {
                gravity = Gravity.CENTER
                minHeight = dp(56); minWidth = dp(64)
                contentDescription = if (accessibilityOpen) "Volver al menú de perfil" else if (inProfile) "Volver a la pantalla anterior" else "Abrir menú de perfil"
                background = RippleDrawable(ColorStateList.valueOf(Color.argb(35, 20, 127, 149)), shape(), rounded(Color.WHITE, 14, Color.TRANSPARENT, 0))
                setOnClickListener {
                    if (accessibilityOpen) profileMenu()
                    else if (inProfile) renderRoute(model.previousScreen)
                    else { model.previousScreen = model.screen; profileMenu() }
                }
            }, LinearLayout.LayoutParams(-2, -2))
        }
    }
    private fun show(v: View) {
        screenGeneration++; clearListeners(); updateDraftUi = null; updateAuthUi = null
        content.removeAllViews(); content.addView(v)
        v.alpha = 0f; v.translationY = dp(8).toFloat()
        v.animate().alpha(1f).translationY(0f).setDuration(180L).start()
    }

    private fun pill(value: String, positive: Boolean = true) = tv(value, 11f, true, if (highContrast) Color.BLACK else tealDark).apply {
        gravity = Gravity.CENTER_VERTICAL
        val fill = if (highContrast) Color.WHITE else if (positive) softTeal else Color.rgb(235, 240, 242)
        background = rounded(fill, 30,
            if (highContrast) Color.BLACK else Color.TRANSPARENT, if (highContrast) 1 else 0)
        setPadding(dp(10), dp(5), dp(10), dp(5))
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { setMargins(0, dp(7), 0, 0) }
    }

    private fun infoCard(title: String, detail: String, symbol: String = "i") = card().apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(tv(symbol, 18f, true, teal).apply {
            gravity = Gravity.CENTER; background = rounded(softTeal, 24, Color.TRANSPARENT, 0)
            minWidth = dp(42); minHeight = dp(42)
        })
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0)
            addView(tv(title, 14f, true)); addView(tv(detail, 12f, false, gray))
        }, LinearLayout.LayoutParams(0, -2, 1f))
    }

    private fun metricCard(value: TextView, label: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; background = shape(); setPadding(dp(16), dp(14), dp(16), dp(14))
        elevation = if (highContrast) 0f else dp(2).toFloat()
        addView(value); addView(tv(label, 12f, false, gray))
    }

    private fun messageView(value: String) = tv(value, 12f, false, gray).apply {
        background = rounded(if (highContrast) Color.WHITE else softTeal, 12, if (highContrast) Color.BLACK else Color.TRANSPARENT,
            if (highContrast) 1 else 0)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(12), dp(7), dp(12), dp(7)) }
    }
    @Suppress("DEPRECATION")
    private fun applySystemBars() {
        window.statusBarColor = teal
        window.navigationBarColor = bg
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = true
        }
    }
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
        applySystemBars()
        val r = root(); content = FrameLayout(this)
        r.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        nav = LinearLayout(this).apply {
            setBackgroundColor(Color.WHITE); setPadding(dp(5), dp(6), dp(5), dp(6))
            elevation = dp(8).toFloat()
            visibility = if (model.ready) View.VISIBLE else View.GONE
        }
        val navItems = listOf(
            Triple("Inicio", R.drawable.ic_nav_home, 0),
            Triple("Reportes", R.drawable.ic_nav_report, 1),
            Triple("Mascotas", R.drawable.ic_nav_pets, 2),
            Triple("Territorio", R.drawable.ic_nav_map, 3)
        )
        navItems.forEach { (label, icon, i) ->
            nav.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; minimumHeight = dp(62)
                setPadding(dp(2), dp(7), dp(2), dp(5)); isClickable = true; isFocusable = true
                addView(ImageView(this@MainActivity).apply {
                    setImageResource(icon); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(dp(23), dp(23)))
                addView(tv(label, 10f, i == tab, if (i == tab) teal else gray).apply {
                    gravity = Gravity.CENTER; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                })
                contentDescription = if (i == tab) "$label, pestaña seleccionada" else "$label, abrir pestaña"
                setOnClickListener { accessibilityOpen = false; tab = i; renderTab() }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        r.addView(nav); setContentView(r); updateNavigation()
        when (model.session.value?.phase) {
            "ready" -> renderRoute(model.screen)
            "signedOut", "authenticating" -> if (accessibilityOpen) accessibilityScreen() else authScreen()
            else -> sessionScreen()
        }
    }
    private fun renderTab() {
        if (!model.ready) return
        when (tab) { 0 -> home(); 1 -> reports(); 2 -> pets(); 3 -> territory(); else -> profileMenu() }
    }
    private fun sessionScreen() {
        val session = model.session.value ?: SessionState("starting")
        val c = root(); c.addView(header("Tu cuenta", "Mascotas Municipales"))
        c.addView(infoCard("Preparando tu espacio", session.message.ifBlank { "Preparando sesión…" }, "⋯"))
        if (session.phase == "profileError") c.addView(button("REINTENTAR PERFIL") { model.prepareProfile() })
        if (repo.uid != null) c.addView(secondaryButton("CERRAR SESIÓN") { model.logout() })
        show(scroll(c))
    }
    private fun authScreen() {
        val c = root(); c.addView(header("🐾 Mascotas Municipales", "Registro e ingreso · Zipaquirá"))
        c.addView(heading("Bienvenido"))
        c.addView(tv("Ingresa para registrar mascotas, crear reportes y consultar su seguimiento.", 14f, false, gray).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(16), 0, dp(16), dp(10)) }
        })
        val form = card()
        form.addView(tv("Datos de acceso", 16f, true))
        val email = field("Correo electrónico").apply { inputType = 33 }; form.addView(email)
        val password = field("Contraseña (mínimo 6 caracteres)").apply {
            inputType = 129; isSaveEnabled = false
        }; form.addView(password)
        val message = messageView(""); form.addView(message)
        val workspace = select(listOf("Ciudadano", "Veterinario", "Administrador de la dependencia"))
        form.addView(spinnerLabel("Ingresar como", workspace)); form.addView(workspace)
        form.addView(tv("Elegir una opción no concede permisos. Las cuentas profesionales requieren aprobación.", 12f, false, gray).apply {
            setPadding(dp(14), dp(5), dp(14), dp(8))
        })
        val login = button("INGRESAR") {
            model.requestedWorkspace = listOf("citizen", "vet", "admin")[workspace.selectedItemPosition]
            model.authenticate(email.text.toString(), password.text.toString(), false)
        }
        val register = button("CREAR CUENTA CIUDADANA") {
            model.requestedWorkspace = "citizen"
            model.authenticate(email.text.toString(), password.text.toString(), true)
        }
        form.addView(login); form.addView(register); c.addView(form)
        val accessibility = secondaryButton("AJUSTES DE ACCESIBILIDAD") { accessibilityOpen = true; accessibilityScreen() }
        c.addView(accessibility)
        message.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        show(scroll(c))
        updateAuthUi = { session ->
            val busy = session.phase == "authenticating"
            login.isEnabled = !busy; register.isEnabled = !busy; accessibility.isEnabled = !busy
            email.isEnabled = !busy; password.isEnabled = !busy
            workspace.isEnabled = !busy
            message.text = session.message.ifBlank { "Ingresa con tu correo y contraseña." }
        }
        updateAuthUi?.invoke(model.session.value ?: SessionState("signedOut"))
    }
    private fun bind(field: EditText, draft: Draft, key: String) {
        field.setText(draft.value(key)); field.isEnabled = !draft.locked
        field.doAfterTextChanged { if (draft.set(key, it.toString())) model.draftEdited() }
    }
    private fun bind(spinner: Spinner, draft: Draft, key: String, values: List<String>) {
        spinner.setSelection(values.indexOf(draft.value(key, values.first())).coerceAtLeast(0))
        spinner.isEnabled = !draft.locked
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (draft.set(key, values[position])) model.draftEdited()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }
    private fun attachSubmission(kind: String, message: TextView, submit: Button, another: Button, inputs: List<View>) {
        message.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        updateDraftUi = {
            val d = model.draft(kind)
            message.text = if (model.storageError.isNotBlank()) model.storageError else when (d.status) {
                "pending" -> "Pendiente de sincronización. Conservamos este envío al cerrar la app."
                "confirmed" -> "Sincronizado · confirmado por el servidor."
                "failed" -> "Sin confirmar: ${d.error}"
                else -> "Borrador guardado en este dispositivo."
            }
            inputs.forEach { it.isEnabled = !d.locked }
            submit.isEnabled = d.status !in listOf("pending", "confirmed") && model.storageError.isEmpty()
            if (d.status == "failed") submit.text = "REINTENTAR MISMO ENVÍO"
            another.visibility = if (d.status == "confirmed") View.VISIBLE else View.GONE
        }
        updateDraftUi?.invoke()
    }
    private fun home() {
        remember("home", 0)
        val c = root(); c.addView(header("🐾 Mascotas Municipales", "Prototipo académico · Zipaquirá"))
        c.addView(infoCard("Tus datos, incluso sin conexión", "Los cambios ciudadanos se conservan en este dispositivo y se confirman cuando responde Firebase.", "✓"))
        c.addView(heading("Resumen"))
        val petsNumber = tv("Cargando…", 22f, true, teal)
        val reportsNumber = tv("Cargando…", 22f, true, teal)
        c.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(dp(8), 0, dp(8), 0)
            addView(metricCard(petsNumber, "Mascotas registradas\nhasta 1000"), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
            addView(metricCard(reportsNumber, "Mis reportes\nabiertos"), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
        })
        repo.petCount { count, _ -> petsNumber.text = count?.toString() ?: "Sin conexión" }
        repo.activeReportCount { count, _ -> reportsNumber.text = count?.toString() ?: "Sin conexión" }
        c.addView(heading("Acciones rápidas"))
        c.addView(button("VER MASCOTAS") { tab = 2; renderTab() })
        c.addView(button("NUEVO REPORTE") { newReport() })
        c.addView(secondaryButton("ACCESO PROFESIONAL") { workHome() })
        c.addView(heading("Registros recientes · máximo 3", 15f))
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
        orientation = LinearLayout.HORIZONTAL; isFocusable = true
        contentDescription = "Abrir ficha de ${p.name}, ${p.species}, raza ${p.breed}, ${territoryLabel(p.territoryId)}, ${p.status}. ${state(p.pending, cache)}"
        setOnClickListener { petDetail(p.id) }
        val res = photoResource(p.photoKey)
        if (res != 0) addView(ImageView(this@MainActivity).apply {
            setPhoto(this, res); scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(softTeal, 13, Color.TRANSPARENT, 0); clipToOutline = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(76), dp(76)))
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            addView(tv(p.name, 16f, true))
            addView(tv("${p.species} · ${p.breed}", 12f, false, gray))
            addView(tv(territoryLabel(p.territoryId), 11f, false, gray))
            addView(pill(state(p.pending, cache), !p.pending && !cache))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(tv("›", 26f, false, teal).apply { gravity = Gravity.CENTER; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO })
    }
    private fun pets() {
        remember("pets", 2)
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
        remember("newPet", 2)
        val c = root(); c.addView(header("Registrar mascota", "Datos públicos mínimos · sin subir fotografías"))
        c.addView(infoCard("Ficha pública", "Incluye únicamente datos descriptivos. No se publican datos de contacto.", "🐾"))
        val form = card(); form.addView(tv("Información de la mascota", 16f, true))
        val name = field("Nombre", 80); form.addView(name)
        val species = select(listOf("Perro", "Gato")); form.addView(spinnerLabel("Especie", species)); form.addView(species)
        val breed = field("Raza", 80); form.addView(breed)
        val sex = select(listOf("Hembra", "Macho", "No determinado")); form.addView(spinnerLabel("Sexo", sex)); form.addView(sex)
        val age = field("Edad aproximada", 40); form.addView(age)
        val color = field("Color", 80); form.addView(color)
        val territory = select(TERRITORIES.map { it.label }); form.addView(spinnerLabel("Comuna", territory)); form.addView(territory)
        val message = messageView("Las fotos incluidas son solo demostrativas."); form.addView(message)
        val draft = model.petDraft
        bind(name, draft, "name"); bind(breed, draft, "breed"); bind(age, draft, "age"); bind(color, draft, "color")
        bind(species, draft, "species", listOf("Perro", "Gato"))
        bind(sex, draft, "sex", listOf("Hembra", "Macho", "No determinado"))
        bind(territory, draft, "territory", TERRITORIES.map { it.id })
        val submit = button("GUARDAR MASCOTA") {
            if (name.text.isBlank() || breed.text.isBlank() || age.text.isBlank() || color.text.isBlank()) {
                message.text = "Completa nombre, raza, edad y color."
            } else model.submit("pet")
        }
        val another = button("REGISTRAR OTRA MASCOTA") { model.startAnother("pet"); newPet() }
        form.addView(submit); form.addView(another); c.addView(form)
        show(scroll(c))
        attachSubmission("pet", message, submit, another, listOf(name, breed, age, color, species, sex, territory))
    }

    private fun petDetail(id: String) {
        remember("pet:$id", 2)
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
                setPhoto(this, res); scaleType = ImageView.ScaleType.CENTER_CROP
                background = rounded(softTeal, 18, Color.TRANSPARENT, 0); clipToOutline = true
                contentDescription = "Fotografía ilustrativa de ${p.name}"
            }, LinearLayout.LayoutParams(-1, dp(210)).apply { setMargins(dp(12), dp(12), dp(12), dp(4)) })
            body.addView(card().apply {
                addView(tv(p.name, 24f, true)); addView(tv("${p.species} · ${p.breed}", 14f, false, gray))
                addView(pill(p.status)); addView(tv("Sexo: ${p.sex}\nEdad: ${p.age}\nColor: ${p.color}\nTerritorio: ${territoryLabel(p.territoryId)}\nCódigo: ${p.qrCode}").apply { setLineSpacing(0f, 1.2f) })
                addView(pill(state(p.pending, cache), !p.pending && !cache))
            })
            body.addView(infoCard("Datos del responsable protegidos", "Esta ficha no contiene teléfono, dirección ni correo.", "🔒"))
            body.addView(secondaryButton("← VOLVER") { pets() })
        })
    }
    private fun reports() {
        remember("reports", 1)
        val c = root(); c.addView(header("Mis reportes", "30 más recientes"))
        c.addView(button("+ NUEVO REPORTE") { newReport() })
        val list = root(); c.addView(list); show(scroll(c))
        val generation = screenGeneration
        val requestedUid = repo.uid
        run {
            if (generation == screenGeneration && requestedUid == repo.uid) {
                val display: (List<Report>, Boolean, String?) -> Unit = { reports, cache, error ->
                    list.removeAllViews()
                    if (error != null) list.addView(tv("Error: $error"))
                    else if (reports.isEmpty()) list.addView(tv(if (cache) "Sin reportes en caché; conecta para verificar." else "Todavía no hay reportes visibles."))
                    else reports.forEach { r -> list.addView(card().apply {
                        isFocusable = true
                        contentDescription = "Abrir reporte de ${r.petName.ifBlank { "mascota sin identificar" }}, ${r.type}, ${r.status}, ${date(r.createdAt)}. ${state(r.pending, cache)}"
                        setOnClickListener { reportDetail(r.id) }
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                        addView(LinearLayout(this@MainActivity).apply {
                            gravity = Gravity.CENTER_VERTICAL
                            addView(tv(r.petName.ifBlank { "Mascota sin identificar" }, 16f, true).apply {
                                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                            }, LinearLayout.LayoutParams(0, -2, 1f))
                            addView(pill(r.type))
                        })
                        addView(tv("${r.status} · ${date(r.createdAt)}", 12f, false, gray).apply {
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        })
                        addView(pill(state(r.pending, cache), !r.pending && !cache).apply {
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        })
                    }) }
                }
                listeners.add(repo.observeMyReports(display))
            }
        }
    }
    private fun newReport() {
        remember("newReport", 1)
        val c = root(); c.addView(header("Nuevo Reporte", "Los datos se guardan en Firestore"))
        c.addView(infoCard("Reporte ciudadano", "Describe el caso con claridad. La función de fotografía sigue siendo simulada y no sube archivos.", "!"))
        val form = card(); form.addView(tv("Información del reporte", 16f, true))
        val name = field("Nombre de mascota (si se conoce)", 80); form.addView(name)
        val type = select(listOf("Pérdida", "Encontrado", "Avistamiento")); form.addView(spinnerLabel("Tipo de reporte", type)); form.addView(type)
        val species = select(listOf("Perro", "Gato")); form.addView(spinnerLabel("Especie", species)); form.addView(species)
        val territory = select(TERRITORIES.map { it.label }); form.addView(spinnerLabel("Ubicación (comuna)", territory)); form.addView(territory)
        val desc = field("Describe lo que observaste", 2000).apply {
            minLines = 4; minimumHeight = dp(110); gravity = Gravity.TOP
        }; form.addView(desc)
        form.addView(tv("No incluyas datos personales de terceros.", 11f, false, gray).apply { setPadding(dp(14), dp(2), dp(14), dp(6)) })
        val message = messageView(""); form.addView(message)
        val draft = model.reportDraft
        bind(name, draft, "name"); bind(desc, draft, "description")
        bind(type, draft, "type", listOf("Pérdida", "Encontrado", "Avistamiento"))
        bind(species, draft, "species", listOf("Perro", "Gato"))
        bind(territory, draft, "territory", TERRITORIES.map { it.id })
        val submit = button("ENVIAR REPORTE") {
            if (desc.text.isBlank()) message.text = "Escribe una descripción."
            else model.submit("report")
        }
        val another = button("CREAR OTRO REPORTE") { model.startAnother("report"); newReport() }
        form.addView(submit); form.addView(another); c.addView(form)
        show(scroll(c))
        attachSubmission("report", message, submit, another, listOf(name, desc, type, species, territory))
    }

    private fun reportDetail(id: String) {
        remember("report:$id", 1)
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
                addView(pill(r.status)); addView(tv("Especie: ${r.species}\nComuna: ${territoryLabel(r.territoryId)}\nDescripción: ${r.description}\nFecha: ${date(r.createdAt)}").apply { setLineSpacing(0f, 1.2f) })
                addView(pill(state(r.pending, cache), !r.pending && !cache))
            })
            if (r.status == "Abierto") body.addView(button("MARCAR RESUELTO") {
                syncMessage = "Pendiente de sincronización"
                repo.updateReportStatus(r.id, "Resuelto") { failure ->
                    syncMessage = if (failure == null) "Sincronizado" else "Falló: $failure"
                    toast(syncMessage)
                }
            })
            body.addView(secondaryButton("← VOLVER") { reports() })
            body.addView(button("VER SEGUIMIENTO DEL CASO") { citizenCase(id) })
        })
    }
    private fun territory() {
        remember("territory", 3)
        val c = root(); c.addView(header("Territorio", "Comunas del prototipo"))
        c.addView(infoCard("Zipaquirá", "División usada para clasificar registros. No se muestran cifras municipales sin verificar.", "⌖"))
        c.addView(heading("Comunas disponibles"))
        TERRITORIES.forEachIndexed { index, territory -> c.addView(card().apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(tv("${index + 1}", 15f, true, Color.WHITE).apply {
                gravity = Gravity.CENTER; background = rounded(teal, 30, Color.TRANSPARENT, 0)
            }, LinearLayout.LayoutParams(dp(42), dp(42)))
            addView(tv(territory.label.substringAfter("· ", territory.label), 15f, true).apply { setPadding(dp(14), 0, 0, 0) })
        }) }
        show(scroll(c))
    }
    private fun workHome() {
        remember("work", 4)
        val c = root(); c.addView(header("Acceso profesional", "Dependencia municipal · Zipaquirá"))
        val message = messageView("Verificando permisos con el servidor…"); c.addView(message)
        c.addView(secondaryButton("VOLVER A INICIO") { model.requestedWorkspace = "citizen"; home() })
        show(scroll(c))
        val generation = screenGeneration; val account = repo.uid
        work.access { access, error ->
            if (generation != screenGeneration || account != repo.uid) return@access
            if (access == null) { message.text = error; return@access }
            if (model.requestedWorkspace != "citizen" && model.requestedWorkspace != access.role) {
                message.text = "La cuenta no tiene el rol elegido. Tu acceso aprobado es ${if (access.role == "vet") "Veterinario" else "Administrador"}."
                c.addView(button("ABRIR MI ACCESO APROBADO") { model.requestedWorkspace = access.role; workHome() })
                return@access
            }
            message.text = if (access.role == "admin") "Administrador de la dependencia" else "Veterinario"
            c.addView(infoCard("Operación conectada", "Los cambios se confirman únicamente cuando responde el servidor. Usa solo datos ficticios.", "↻"))
            if (access.role == "admin") {
                c.addView(button("REVISAR REPORTES") { workList("reports", access) })
                c.addView(button("EQUIPO VETERINARIO") { workList("access", access) })
            }
            c.addView(button(if (access.role == "admin") "CASOS DE LA DEPENDENCIA" else "MIS CASOS Y SEGUIMIENTOS") { workList("cases", access) })
        }
    }

    private fun workList(kind: String, access: WorkAccess, cursor: com.google.firebase.firestore.DocumentSnapshot? = null) {
        remember("work", 4)
        val title = when (kind) { "reports" -> "Reportes para revisión"; "access" -> "Equipo veterinario"; else -> "Casos y seguimientos" }
        val c = root(); c.addView(header(title, "20 por página · lectura del servidor"))
        c.addView(secondaryButton("VOLVER AL PANEL") { workHome() })
        val message = messageView("Cargando…"); c.addView(message); show(scroll(c))
        val generation = screenGeneration; val account = repo.uid
        work.page(kind, access, cursor) { docs, error ->
            if (generation != screenGeneration || account != repo.uid) return@page
            message.text = error ?: if (docs.isNullOrEmpty()) "No hay más registros." else "${docs.size} registros en esta página."
            docs.orEmpty().forEach { doc ->
                c.addView(card().apply {
                    if (kind == "access") {
                        addView(tv(doc.getString("displayName") ?: "Veterinario de demostración", 16f, true))
                        addView(pill(if (doc.getBoolean("active") == true) "Activo" else "Suspendido", doc.getBoolean("active") == true))
                        addView(tv("UID: ${doc.id}", 12f, false, gray).apply { setTextIsSelectable(true) })
                        addView(tv("Aprobaciones y revocaciones: responsable de Firebase, mediante procedimiento documentado.", 12f))
                    } else {
                        addView(tv(doc.getString("petName").orEmpty().ifBlank { "Mascota sin identificar" }, 16f, true))
                        addView(tv("${doc.getString("status")} · ${date(doc.getTimestamp("updatedAt"))}"))
                        if (kind == "reports") {
                            addView(tv("${doc.getString("type")} · ${doc.getString("species")}\n${territoryLabel(doc.getString("territoryId").orEmpty())}\n${doc.getString("description")}"))
                            val result = tv(""); addView(result)
                            lateinit var review: Button
                            review = button("REVISAR / ABRIR CASO") {
                                review.isEnabled = false; result.text = "Esperando confirmación…"
                                work.review(doc.id) { failure ->
                                    if (generation == screenGeneration && account == repo.uid) {
                                        review.isEnabled = true
                                        if (failure == null) workCase(doc.id) else result.text = "No confirmado: $failure"
                                    }
                                }
                            }
                            addView(review)
                        } else addView(button("ABRIR CASO") { workCase(doc.id) })
                    }
                })
            }
            if (docs?.size == 20) c.addView(button("SIGUIENTE PÁGINA") { workList(kind, access, docs.last()) })
            c.addView(button("ACTUALIZAR DESDE EL INICIO") { workList(kind, access) })
        }
    }

    private fun workCase(id: String) {
        remember("workcase:$id", 4)
        val c = root(); c.addView(header("Caso municipal", "Información operativa · datos ficticios"))
        val message = messageView("Comprobando acceso y versión…"); c.addView(message)
        c.addView(secondaryButton("VOLVER AL PANEL") { workHome() }); show(scroll(c))
        val generation = screenGeneration; val account = repo.uid
        fun current() = generation == screenGeneration && account == repo.uid
        work.access { access, error ->
            if (!current()) return@access
            if (access == null) { message.text = error; return@access }
            work.getCase(id) caseResult@ { doc, failure ->
                if (!current()) return@caseResult
                if (doc == null) { message.text = failure ?: "No existe el caso."; return@caseResult }
                val status = doc.getString("status").orEmpty()
                val version = doc.getLong("version") ?: 0
                message.text = "${doc.getString("petName").orEmpty().ifBlank { "Mascota sin identificar" }} · ${doc.getString("species")}\nEstado: $status\n${territoryLabel(doc.getString("territoryId").orEmpty())}\nVeterinario: ${doc.getString("vetId").orEmpty().ifBlank { "Sin asignar" }}\nResultado para el ciudadano: ${doc.getString("outcome").orEmpty().ifBlank { "En proceso" }}"
                val petId = doc.getString("petId").orEmpty()
                if (petId.isNotBlank()) c.addView(button("VER FICHA PÚBLICA DE LA MASCOTA") { petDetail(petId) })
                c.addView(button("ACTUALIZAR CASO") { workCase(id) })
                val result = tv(""); c.addView(result)
                var busy = false
                fun submit(next: String, vetId: String = "", outcome: String = "", examination: String = "", care: String = "", followUp: String = "") {
                    if (busy) return
                    busy = true; result.text = "Esperando confirmación del servidor…"
                    work.change(id, version, next, vetId, outcome, examination, care, followUp) { e ->
                        if (current()) {
                            busy = false
                            if (e == null) { model.workDrafts.remove(id); toast("Cambio confirmado por el servidor"); workCase(id) }
                            else result.text = "No confirmado: $e. Actualiza para comprobar el estado antes de reintentar."
                        }
                    }
                }
                if (access.role == "admin") {
                    if (status in listOf("Revisado", "Asignado")) {
                        val vet = field("UID del veterinario aprobado", 128); c.addView(vet)
                        c.addView(tv("Copia el UID del menú Equipo veterinario. Se valida su rol, dependencia y estado activo.", 12f))
                        c.addView(button("ASIGNAR VETERINARIO") {
                            if (vet.text.isBlank()) result.text = "Escribe el UID del veterinario." else submit("Asignado", vetId = vet.text.toString())
                        })
                    }
                    if (status in listOf("Revisado", "Atendido")) {
                        val outcome = field("Resultado para el ciudadano, sin datos clínicos ni contactos", 1000); c.addView(outcome)
                        c.addView(button("CERRAR CASO Y REPORTE") {
                            if (outcome.text.isBlank()) result.text = "Escribe el resultado." else submit("Cerrado", outcome = outcome.text.toString())
                        })
                    }
                    c.addView(tv("La historia clínica está reservada al veterinario asignado.", 12f))
                } else if (doc.getString("vetId") == account) {
                    if (status in listOf("Asignado", "Atendido")) {
                        val values = model.workDrafts.getOrPut(id) { mutableMapOf() }
                        fun clinicalField(key: String, label: String, max: Int): EditText {
                            c.addView(tv(label, 14f, true))
                            return field(label, max).apply {
                                setText(values[key].orEmpty()); doAfterTextChanged { values[key] = it.toString() }
                                c.addView(this)
                            }
                        }
                        c.addView(tv("Borrador temporal: se conserva al girar la pantalla, pero no al cerrar el proceso. Cada envío es definitivo; corrige mediante una nueva entrada.", 12f))
                        val exam = clinicalField("examination", "Valoración / motivo", 2000)
                        val care = clinicalField("care", "Atención realizada", 2000)
                        val follow = clinicalField("followUp", "Seguimiento recomendado", 1000)
                        c.addView(button("GUARDAR ATENCIÓN DEFINITIVA") {
                            if (exam.text.isBlank() || care.text.isBlank()) result.text = "Completa valoración y atención."
                            else submit("Atendido", examination = exam.text.toString(), care = care.text.toString(), followUp = follow.text.toString())
                        })
                    }
                    c.addView(heading("Atenciones registradas · privadas"))
                    fun records(cursor: com.google.firebase.firestore.DocumentSnapshot? = null) {
                        work.records(id, cursor) { notes, noteError ->
                            if (current()) {
                                if (noteError != null) c.addView(tv("No se pudieron leer las atenciones: $noteError"))
                                notes.orEmpty().forEach { n -> c.addView(card().apply {
                                    addView(tv("${date(n.getTimestamp("createdAt"))}\nValoración: ${n.getString("examination")}\nAtención: ${n.getString("care")}\nSeguimiento: ${n.getString("followUp")}"))
                                }) }
                                if (notes?.size == 20) {
                                    lateinit var more: Button
                                    more = button("MÁS ATENCIONES") { more.isEnabled = false; records(notes.last()) }; c.addView(more)
                                }
                            }
                        }
                    }
                    records()
                }
            }
        }
    }

    private fun citizenCase(id: String) {
        remember("report:$id", 1)
        val c = root(); c.addView(header("Seguimiento municipal"))
        val message = messageView("Consultando servidor…"); c.addView(message)
        c.addView(secondaryButton("VOLVER AL REPORTE") { reportDetail(id) }); show(scroll(c))
        val generation = screenGeneration; val account = repo.uid
        work.getCase(id) { doc, error ->
            if (generation == screenGeneration && account == repo.uid) message.text = error ?: if (doc == null)
                "Tu reporte todavía no tiene un caso municipal."
            else "Estado: ${doc.getString("status")}\nResultado: ${doc.getString("outcome").orEmpty().ifBlank { "En proceso" }}\nActualizado: ${date(doc.getTimestamp("updatedAt"))}"
        }
    }

    private fun upcomingOption(title: String, detail: String = "") = card().apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; alpha = .78f
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(tv(title, 15f, true))
            if (detail.isNotBlank()) addView(tv(detail, 12f, false, gray))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(pill("Próximamente", false))
    }
    private fun accessibilityToggle(title: String, detail: String, key: String, checked: Boolean, update: (Boolean) -> Unit) = card().apply {
        addView(SwitchCompat(this@MainActivity).apply {
            text = title; isChecked = checked; textSize = 16f * textScale; setTextColor(dark)
            minHeight = dp(56)
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            trackTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(teal, if (highContrast) Color.DKGRAY else Color.GRAY)
            )
            setOnCheckedChangeListener { _, value ->
                accessibilityScrollY = (content.getChildAt(0) as? ScrollView)?.scrollY ?: 0
                update(value)
                accessibilityPreferences.edit().putBoolean(key, value).apply()
                showApp()
            }
        })
        addView(tv(detail, 13f, false, gray))
    }
    private fun accessibilityScreen() {
        accessibilityOpen = true
        if (model.ready) remember("accessibility", 4)
        applySystemBars()
        val c = root(); c.addView(header("Accesibilidad", "Ajustes guardados en este dispositivo"))
        c.addView(infoCard("Una app que se adapta a ti", "Estos cambios solo se guardan en este dispositivo y no se envían a Firebase.", "A"))
        c.addView(heading("Lectura y colores"))
        c.addView(accessibilityToggle("Texto más grande",
            "Aumenta el texto de la app y deja espacio para el tamaño de fuente configurado en Android.",
            "large_text", largeText) { largeText = it })
        c.addView(accessibilityToggle("Contraste alto",
            "Usa texto negro, fondo blanco y bordes definidos para distinguir los controles.",
            "high_contrast", highContrast) { highContrast = it })
        c.addView(accessibilityToggle("Paleta para daltonismo",
            "Usa azul como color principal. Los estados también se muestran con palabras. Con contraste alto activo se prioriza el blanco y negro.",
            "colorblind_palette", colorblindPalette) { colorblindPalette = it })
        c.addView(heading("Lector de pantalla", 16f))
        c.addView(card().apply {
            addView(tv("La app tiene etiquetas para TalkBack en navegación, formularios, fotos y registros. TalkBack se activa desde los ajustes de Android.", 13f))
        })
        c.addView(button("ABRIR ACCESIBILIDAD DE ANDROID") {
            try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            catch (_: ActivityNotFoundException) { toast("Este dispositivo no tiene ajustes de accesibilidad disponibles.") }
        })
        c.addView(secondaryButton("← VOLVER") {
            accessibilityOpen = false; accessibilityScrollY = 0
            if (!model.ready) authScreen() else profileMenu()
        })
        val settingsScroll = scroll(c)
        show(settingsScroll)
        if (accessibilityScrollY > 0) settingsScroll.post { settingsScroll.scrollTo(0, accessibilityScrollY) }
    }
    private fun profileMenu() {
        remember("profile", 4)
        val c = root(); c.addView(header("Mi perfil", "Cuenta y preferencias"))
        c.addView(card().apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(tv(repo.email.take(1).uppercase(), 20f, true, Color.WHITE).apply {
                gravity = Gravity.CENTER; background = rounded(teal, 40, Color.TRANSPARENT, 0)
            }, LinearLayout.LayoutParams(dp(52), dp(52)))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0)
                addView(tv(repo.email, 16f, true)); addView(tv("Cuenta ciudadana", 12f, false, gray))
            }, LinearLayout.LayoutParams(0, -2, 1f))
        })
        c.addView(card().apply {
            isFocusable = true
            contentDescription = "Abrir ajustes de accesibilidad: texto más grande, contraste alto, paleta para daltonismo y TalkBack"
            setOnClickListener { accessibilityOpen = true; accessibilityScreen() }
            addView(heading("Accesibilidad").apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO })
            addView(tv("Texto grande, contraste alto, colores accesibles y lector de pantalla.", 12f, false, gray).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
        })
        c.addView(upcomingOption("Información de la aplicación"))
        c.addView(secondaryButton("ACCESO PROFESIONAL") { workHome() })
        c.addView(upcomingOption("Información de tu cuenta"))
        c.addView(upcomingOption("Cambiar correo electrónico"))
        c.addView(upcomingOption("Cambiar contraseña"))
        c.addView(upcomingOption("Cambiar información de tus mascotas"))
        c.addView(secondaryButton("CERRAR SESIÓN") {
            accessibilityOpen = false; model.logout()
        })
        show(scroll(c))
    }
}
