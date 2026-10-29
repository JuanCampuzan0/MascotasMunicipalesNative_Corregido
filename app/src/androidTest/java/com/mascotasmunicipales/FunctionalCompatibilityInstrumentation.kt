package com.mascotasmunicipales

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Recorre las vistas reales. Se niega a ejecutarse contra el paquete o Firebase normales. */
class FunctionalCompatibilityInstrumentation : Instrumentation() {
    private lateinit var args: Bundle
    private lateinit var activity: MainActivity
    private lateinit var output: File
    private val report = JSONObject()
    private val db get() = FirebaseFirestore.getInstance()
    private val auth get() = FirebaseAuth.getInstance()

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        args = arguments ?: Bundle()
        start()
    }

    override fun onStart() {
        // La Application debe configurar los emuladores antes de que la prueba use los SDK.
        waitForIdleSync()
        var code = Activity.RESULT_OK
        val result = Bundle()
        try {
            check(targetContext.packageName == "com.mascotasmunicipales.compatqa") { "Solo se permite el paquete QA aislado" }
            check(FirebaseApp.getInstance().options.projectId == "demo-mascotas-compat") { "Solo se permite Firebase demo local" }
            val run = args.getString("runId") ?: UUID.randomUUID().toString().take(8)
            check(run.matches(Regex("[A-Za-z0-9_-]{1,40}")))
            output = File(targetContext.getExternalFilesDir(null), "compatibility/$run").apply { mkdirs() }
            report.put("api", Build.VERSION.SDK_INT).put("android", Build.VERSION.RELEASE)
                .put("package", targetContext.packageName).put("project", "demo-mascotas-compat")
                .put("runId", run).put("mode", args.getString("mode", "run"))
            if (args.getString("mode") == "recover") {
                Tasks.await(db.disableNetwork(), 15, TimeUnit.SECONDS)
            } else {
                auth.signOut()
                Tasks.await(db.enableNetwork(), 15, TimeUnit.SECONDS)
            }
            activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            if (args.getString("mode") == "recover") recover() else runFunctions(run)
            report.put("result", "APROBADO")
            result.putString("resultado", "APROBADO")
        } catch (error: Throwable) {
            code = Activity.RESULT_CANCELED
            report.put("result", "FALLO").put("error", error.message ?: error.javaClass.simpleName)
            result.putString("error", error.message ?: error.javaClass.simpleName)
        } finally {
            if (::output.isInitialized) {
                val file = File(output, "${args.getString("mode", "run")}.json")
                file.writeText(report.toString(2))
                result.putString("evidence", output.absolutePath)
            }
            if (::activity.isInitialized) ui { activity.finish() }
        }
        finish(code, result)
    }

    private fun runFunctions(run: String) {
        phase("Registro e inicio de sesión")
        waitText("Bienvenido")
        // Contraseña efímera: solo vive en esta ejecución; no se imprime ni se escribe en archivos.
        val password = UUID.randomUUID().toString().replace("-", "")
        val email = "compat${Build.VERSION.SDK_INT}-$run@example.test"
        input("Correo electrónico", email)
        input("Contraseña (mínimo 6 caracteres)", password)
        click("CREAR CUENTA CIUDADANA")
        waitText("Resumen")
        click("Perfil"); click("CERRAR SESIÓN")
        waitText("Bienvenido")
        input("Correo electrónico", email)
        input("Contraseña (mínimo 6 caracteres)", password)
        click("INGRESAR")
        // El ingreso recupera la última ruta guardada de esta cuenta, que puede ser Perfil.
        waitFor("sesión preparada") { ui { model().ready } }
        click("Inicio")
        waitText("Resumen")
        report.put("uid", checkNotNull(auth.currentUser).uid).put("login", true)
        args.getString("foreignReportId")?.let { foreignId ->
            var denied = false
            try {
                Tasks.await(db.collection("reports").document(foreignId).get(Source.SERVER), 20, TimeUnit.SECONDS)
            } catch (error: java.util.concurrent.ExecutionException) {
                denied = (error.cause as? com.google.firebase.firestore.FirebaseFirestoreException)?.code ==
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED
            }
            check(denied) { "El reporte ajeno no fue rechazado por permisos" }
            report.put("foreignReportDenied", true)
        }
        screenshot("01-sesion")
        report.put("navigationBarColor", ui { @Suppress("DEPRECATION") activity.window.navigationBarColor })

        phase("Registro y consulta de mascota")
        val petName = "Mascota API ${Build.VERSION.SDK_INT} $run"
        report.put("petName", petName)
        click("VER MASCOTAS"); waitText("Directorio de mascotas")
        click("+ REGISTRAR MASCOTA")
        input("Nombre", petName); input("Raza", "Criolla ficticia")
        input("Edad aproximada", "2 años"); input("Color", "Gris")
        select(0, 1); select(1, 1); select(2, 1)
        click("GUARDAR MASCOTA")
        waitText("Sincronizado · confirmado por el servidor.")
        val petId = ui { model().petDraft.id }
        val pet = Tasks.await(db.collection("pets").document(petId).get(Source.SERVER), 20, TimeUnit.SECONDS)
        check(pet.getString("name") == petName && pet.getString("ownerId") == auth.currentUser!!.uid)
        check(pet.getString("species") == "Gato" && pet.getString("territoryId") == "comuna-2")
        report.put("petId", petId).put("petServerConfirmed", true)
        screenshot("02-mascota-confirmada", "Sincronizado · confirmado por el servidor.")
        back(); waitText("Directorio de mascotas"); waitText(petName); click(petName)
        waitText("Detalle Mascota"); waitText(petName)
        check(ui { texts().any { it.contains("Gris") && it.contains("Criolla").not() } })
        screenshot("03-mascota-detalle")
        report.put("petDetail", true)

        phase("Creación y consulta de reporte")
        click("Inicio"); waitText("Resumen"); click("NUEVO REPORTE")
        val reportName = "Reporte API ${Build.VERSION.SDK_INT} $run"
        val description = "Reporte ficticio de compatibilidad $run, sin datos personales."
        input("Nombre de mascota (si se conoce)", reportName)
        input("Describe lo que observaste", description)
        select(0, 1); select(1, 1); select(2, 2)
        click("ENVIAR REPORTE"); waitText("Sincronizado · confirmado por el servidor.")
        val reportId = ui { model().reportDraft.id }
        val saved = Tasks.await(db.collection("reports").document(reportId).get(Source.SERVER), 20, TimeUnit.SECONDS)
        check(saved.getString("description") == description && saved.getString("type") == "Encontrado")
        report.put("reportId", reportId).put("reportServerConfirmed", true)
        screenshot("04-reporte-confirmado", "Sincronizado · confirmado por el servidor.")
        back(); waitText("Mis reportes"); waitContaining(reportName); clickContaining(reportName)
        waitText("Detalle del reporte"); waitContaining(description)
        screenshot("05-reporte-detalle")
        report.put("reportDetail", true)

        phase("Observación de memoria del directorio y mapa")
        click("Mascotas"); waitText("Directorio de mascotas"); waitText("Luna")
        report.put("directoryPssKiB", memorySamples())
        screenshot("06-directorio-fotos", "Luna")
        click("Luna"); waitText("Detalle Mascota"); waitText("Luna")
        screenshot("07-foto-detalle")
        click("Territorio"); waitText("Mapa de Zipaquirá")
        SystemClock.sleep(3000)
        report.put("mapPssKiB", memorySamples())
        screenshot("08-mapa")

        phase("Reporte sin red y doble envío bloqueado")
        click("Inicio"); waitText("Resumen"); click("NUEVO REPORTE")
        click("CREAR OTRO REPORTE")
        Tasks.await(db.disableNetwork(), 15, TimeUnit.SECONDS)
        val pendingName = "Pendiente API ${Build.VERSION.SDK_INT} $run"
        input("Nombre de mascota (si se conoce)", pendingName)
        input("Describe lo que observaste", "Prueba ficticia sin red y reinicio $run.")
        click("ENVIAR REPORTE")
        waitContaining("Pendiente de sincronización.")
        check(ui { views().filterIsInstance<android.widget.Button>().first { it.text.toString() == "ENVIAR REPORTE" }.isEnabled.not() })
        val pendingId = ui { model().reportDraft.id }
        check(pendingId.isNotEmpty())
        report.put("pendingId", pendingId).put("pendingName", pendingName).put("duplicateButtonBlocked", true)
        screenshot("09-pendiente-sin-red", "Pendiente de sincronización.")
        // El host comprobará ausencia en el servidor, detendrá el proceso y ejecutará recover.
        phase("Pendiente conservado; listo para reinicio real del proceso")
    }

    private fun recover() {
        phase("Recuperación tras detener el proceso")
        val expectedId = checkNotNull(args.getString("pendingId"))
        waitContaining("Pendiente de sincronización.")
        check(ui { model().reportDraft.id } == expectedId)
        report.put("pendingId", expectedId).put("uid", checkNotNull(auth.currentUser).uid)
            .put("sameIdAfterProcessRestart", true)
        screenshot("10-pendiente-recuperado", "Pendiente de sincronización.")
        Tasks.await(db.enableNetwork(), 15, TimeUnit.SECONDS)
        waitText("Sincronizado · confirmado por el servidor.")
        val saved = Tasks.await(db.collection("reports").document(expectedId).get(Source.SERVER), 20, TimeUnit.SECONDS)
        check(saved.exists() && saved.getString("ownerId") == auth.currentUser!!.uid)
        report.put("serverConfirmed", true)
        screenshot("11-reconexion-confirmada", "Sincronizado · confirmado por el servidor.")
        back(); waitText("Mis reportes")
        val name = saved.getString("petName").orEmpty()
        waitContaining(name); clickContaining(name); waitText("Detalle del reporte")
        screenshot("12-reporte-recuperado-detalle")
        click("Perfil"); click("CERRAR SESIÓN"); waitText("Bienvenido")
        report.put("logout", true)
    }

    private fun model() = ViewModelProvider(activity)[AppViewModel::class.java]
    private fun phase(message: String) = sendStatus(1, Bundle().apply { putString("fase", message) })
    private fun views(): List<View> {
        val result = mutableListOf<View>()
        fun walk(v: View) { result.add(v); if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i)) }
        walk(activity.window.decorView)
        return result
    }
    private fun texts() = views().filterIsInstance<TextView>().filter { it !is EditText }.map { it.text.toString() }
    private fun waitText(text: String) = waitFor(text) { ui { texts().contains(text) } }
    private fun waitContaining(text: String) = waitFor(text) { ui { texts().any { it.contains(text) } } }
    private fun waitFor(label: String, condition: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + 45000
        while (SystemClock.elapsedRealtime() < until) {
            if (condition()) { waitForIdleSync(); return }
            SystemClock.sleep(150)
        }
        error("No apareció el estado esperado: $label")
    }
    private fun click(text: String) = clickMatching { it == text }
    private fun clickContaining(text: String) = clickMatching { it.contains(text) }
    private fun clickMatching(match: (String) -> Boolean) {
        ui {
            val target = views().filterIsInstance<TextView>().filter { match(it.text.toString()) }
                .mapNotNull { label ->
                    var v: View? = label
                    while (v != null && !v.isClickable) v = v.parent as? View
                    v?.takeIf { it.isEnabled }
                }.firstOrNull() ?: error("No se encontró el control solicitado")
            target.requestRectangleOnScreen(android.graphics.Rect(0, 0, target.width, target.height), true)
            check(target.performClick())
        }
        waitForIdleSync()
    }
    private fun input(hint: String, value: String) {
        ui { views().filterIsInstance<EditText>().first { it.hint.toString() == hint }.setText(value) }
        waitForIdleSync()
    }
    private fun select(index: Int, value: Int) {
        ui { views().filterIsInstance<Spinner>()[index].setSelection(value) }
        waitForIdleSync()
    }
    private fun back() { sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); waitForIdleSync() }
    private fun memorySamples(): JSONArray {
        SystemClock.sleep(1500)
        val samples = JSONArray()
        repeat(3) {
            val info = Debug.MemoryInfo(); Debug.getMemoryInfo(info); samples.put(info.totalPss)
            SystemClock.sleep(700)
        }
        return samples
    }
    private fun screenshot(name: String, focusText: String? = null) {
        if (focusText != null) ui {
            val label = views().filterIsInstance<TextView>()
                .first { it !is EditText && it.text.toString().contains(focusText) }
            label.requestRectangleOnScreen(android.graphics.Rect(0, 0, label.width, label.height), true)
        }
        waitForIdleSync(); SystemClock.sleep(400)
        val bitmap = checkNotNull(uiAutomation.takeScreenshot())
        File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun <T> ui(action: () -> T): T {
        var answer: T? = null
        var failure: Throwable? = null
        runOnMainSync { try { answer = action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST") return answer as T
    }
}
