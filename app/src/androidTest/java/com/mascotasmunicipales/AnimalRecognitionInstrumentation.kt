package com.mascotasmunicipales

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.core.content.FileProvider
import com.google.android.gms.tasks.Tasks
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Modelo real sin red y cámara sustituida por fixtures; no usa cuentas ni Firebase. */
class AnimalRecognitionInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        var screen: AnimalRecognitionActivity? = null
        var monitor: ActivityMonitor? = null
        val store = AnimalPhotoStore(targetContext)
        val last = File(store.directory, "last.jpg")
        val backup = last.takeIf { it.exists() }?.readBytes()
        var result = Bundle()
        var code = Activity.RESULT_OK
        try {
            val policy = AnimalRecognitionPolicy
            check(!policy.decide(listOf(AnimalLabel("Hot dog", .99f))).valid)
            check(!policy.decide(listOf(AnimalLabel("Dog", .6f))).valid)
            check(!policy.decide(listOf(AnimalLabel("Dog", .95f), AnimalLabel("Stuffed toy", .9f))).valid)
            check(!policy.decide(listOf(AnimalLabel("Dog", .9f), AnimalLabel("Cat", .86f))).valid)
            check(policy.decide(listOf(AnimalLabel("Horse", .9f))).valid)
            check(policy.decide(listOf(AnimalLabel("Turtle", .9f))).valid)
            check(policy.decide(listOf(AnimalLabel("Duck", .9f), AnimalLabel("Bird", .85f))).valid)
            check(!policy.decide(listOf(AnimalLabel("Dog", Float.NaN))).valid)

            val measured = JSONObject()
            AnimalRecognizer().use { recognizer ->
                for ((name, resource, expected) in listOf(
                    Triple("perro", R.drawable.luna, "un perro"),
                    Triple("gato", R.drawable.michi, "un gato"),
                    Triple("codigoQR", R.drawable.qr_luna, ""))) {
                    val bitmap = checkNotNull(BitmapFactory.decodeResource(targetContext.resources, resource))
                    val labels = Tasks.await(recognizer.recognize(bitmap), 45, TimeUnit.SECONDS)
                    val decision = policy.decide(labels)
                    measured.put(name, JSONObject().put("titulo", decision.title).put("valida", decision.valid)
                        .put("etiquetas", labels.joinToString { "${it.text}:${it.confidence}" }))
                    check(if (expected.isEmpty()) !decision.valid else decision.valid && decision.title.contains(expected)) {
                        "$name: $decision; $labels"
                    }
                    bitmap.recycle()
                }
            }

            // EXIF real y reducción de una captura grande.
            val wide = Bitmap.createBitmap(2400, 1200, Bitmap.Config.ARGB_8888)
            store.prepareCapture().outputStream().use { wide.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            wide.recycle()
            ExifInterface(store.capture.path).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes()
            }
            val rotated = store.decodeCapture()
            check(rotated.width == 600 && rotated.height == 1200) { "No respetó reducción/orientación: ${rotated.width}x${rotated.height}" }
            rotated.recycle(); store.capture.delete()

            var cancel = false
            var capturedResource = R.drawable.luna
            screen = startActivitySync(Intent(targetContext, AnimalRecognitionActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as AnimalRecognitionActivity
            fun waitFor(expected: String) {
                val until = System.currentTimeMillis() + 45000
                while (System.currentTimeMillis() < until) {
                    var done = false
                    runOnMainSync {
                        val state = ViewModelProvider(screen!!)[AnimalRecognitionViewModel::class.java].state.value
                        done = state?.busy == false && state.title.contains(expected)
                    }
                    if (done) return
                    Thread.sleep(150)
                }
                var actual = ""
                runOnMainSync { actual = ViewModelProvider(screen!!)[AnimalRecognitionViewModel::class.java].state.value.toString() }
                error("No apareció: $expected. Estado: $actual")
            }
            fun click(label: String) = runOnMainSync {
                fun find(view: View): Button? {
                    if (view is Button && view.text.toString() == label) return view
                    if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
                    return null
                }
                val button = checkNotNull(find(screen!!.window.decorView)) { "Falta botón $label" }
                check(button.isEnabled) { "Botón bloqueado: $label" }
                if (label == "Tomar foto") {
                    monitor?.let { removeMonitor(it) }
                    // Monitor clásico disponible desde API 1. El callback se entrega después de este Runnable.
                    monitor = addMonitor(IntentFilter(MediaStore.ACTION_IMAGE_CAPTURE),
                        ActivityResult(if (cancel) Activity.RESULT_CANCELED else Activity.RESULT_OK, null), true)
                }
                button.performClick()
                if (label == "Tomar foto") {
                    check(monitor!!.hits == 1) { "No se interceptó la cámara" }
                    if (!cancel) {
                        val uri = FileProvider.getUriForFile(targetContext, "${targetContext.packageName}.animalphotos", store.capture)
                        val bitmap = BitmapFactory.decodeResource(targetContext.resources, capturedResource)
                        targetContext.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                        bitmap.recycle()
                    }
                }
            }
            fun captureScreen(name: String) {
                waitForIdleSync()
                uiAutomation.takeScreenshot()?.let { image ->
                    File(targetContext.getExternalFilesDir(null), "recognition-$name.png").outputStream().use {
                        image.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    image.recycle()
                }
            }
            // Quitar únicamente el último fixture previo; el original se restaura al terminar.
            val until = System.currentTimeMillis() + 45000
            while (System.currentTimeMillis() < until) {
                var busy = true
                runOnMainSync { busy = ViewModelProvider(screen!!)[AnimalRecognitionViewModel::class.java].state.value?.busy != false }
                if (!busy) break
                Thread.sleep(150)
            }
            click("Tomar foto"); waitFor("un perro")
            captureScreen("dog")
            check(last.exists())
            val persisted = checkNotNull(AnimalPhotoStore(targetContext).readLast())
            check(maxOf(persisted.width, persisted.height) <= 1280); persisted.recycle()
            monitor?.let { removeMonitor(it) }; monitor = null
            runOnMainSync { screen!!.finish() }; waitForIdleSync()
            screen = startActivitySync(Intent(targetContext, AnimalRecognitionActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as AnimalRecognitionActivity
            waitFor("un perro") // Nueva Activity y nuevo ViewModel leen la foto guardada.
            cancel = true; click("Tomar foto"); waitFor("Captura cancelada")
            cancel = false; capturedResource = R.drawable.qr_luna
            click("Tomar foto"); waitFor("Foto no válida")
            captureScreen("invalid")
            click("Eliminar foto de este dispositivo"); waitFor("Fotografía un animal")
            check(!last.exists())
            result = Bundle().apply {
                putString("resultado", "APROBADO"); putInt("api", Build.VERSION.SDK_INT)
                putString("modeloReal", measured.toString())
                putString("flujo", "Captura con fixture, cancelación, rechazo QR, persistencia, nueva Activity, borrado, EXIF y reducción")
                putString("limite", "Cámara sustituida por fixtures; no prueba cámara física, todas las especies ni exactitud general")
            }
        } catch (error: Throwable) {
            code = Activity.RESULT_CANCELED
            result = Bundle().apply { putString("error", error.stackTraceToString()) }
        } finally {
            screen?.let { runOnMainSync { it.finish() } }; waitForIdleSync()
            monitor?.let { removeMonitor(it) }
            store.clear()
            if (backup != null) { store.directory.mkdirs(); last.writeBytes(backup) }
        }
        finish(code, result)
    }
}
