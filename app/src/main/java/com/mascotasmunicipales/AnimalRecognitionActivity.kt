package com.mascotasmunicipales

import android.content.ActivityNotFoundException
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider

/** Herramienta local independiente de sesión, mascotas, reportes y Firebase. */
class AnimalRecognitionActivity : AppCompatActivity() {
    private val model by lazy { ViewModelProvider(this)[AnimalRecognitionViewModel::class.java] }
    private val camera = registerForActivityResult(ActivityResultContracts.TakePicture()) { model.finishCapture(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = getSharedPreferences("accessibility", MODE_PRIVATE)
        val contrast = preferences.getBoolean("high_contrast", false)
        val scale = if (preferences.getBoolean("large_text", false)) 1.3f else 1f
        val accent = if (contrast) Color.BLACK else if (preferences.getBoolean("colorblind_palette", false)) Color.rgb(0,82,155) else Color.rgb(20,127,149)
        val dark = if (contrast) Color.BLACK else Color.rgb(32,49,58)
        window.statusBarColor = accent
        window.navigationBarColor = if (Build.VERSION.SDK_INT < 26) accent else Color.WHITE
        fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
        fun text(value: String, size: Float = 15f) = TextView(this).apply {
            text = value; textSize = size * scale; setTextColor(dark); setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(24))
            setBackgroundColor(if (contrast) Color.WHITE else Color.rgb(241,246,248))
        }
        val heading = text("Reconocer animal", 23f).apply { setTypeface(typeface, Typeface.BOLD) }
        body.addView(heading)
        body.addView(text("Toma una foto de un perro, gato, ave, caballo, bovino, tortuga u otro animal reconocido por el modelo."))
        val preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Última foto tomada para reconocer el animal"
            setBackgroundColor(Color.WHITE)
        }
        body.addView(preview, LinearLayout.LayoutParams(-1, dp(260)))
        val progress = ProgressBar(this)
        body.addView(progress, LinearLayout.LayoutParams(dp(40), dp(40)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        val result = text("", 20f).apply {
            setTypeface(typeface, Typeface.BOLD); accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val detail = text("")
        body.addView(result); body.addView(detail)
        fun action(label: String, block: () -> Unit) = Button(this).apply {
            text = label; textSize = 14f * scale; isAllCaps = false; minimumHeight = dp(52)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { setColor(accent); cornerRadius = dp(14).toFloat() }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0,dp(6),0,dp(6)) }
            setOnClickListener { block() }; body.addView(this)
        }
        val take = action("Tomar foto") {
            if (model.beginCapture()) {
                try {
                    val file = model.photos.prepareCapture()
                    val uri = FileProvider.getUriForFile(this, "$packageName.animalphotos", file)
                    camera.launch(uri)
                } catch (_: ActivityNotFoundException) { model.cameraUnavailable() }
                catch (_: Exception) { model.cameraUnavailable() }
            }
        }
        val retry = action("Volver a analizar") { model.retry() }
        val clear = action("Eliminar foto de este dispositivo") { model.clearPhoto() }
        body.addView(text("El resultado es una estimación y puede equivocarse. No identifica razas ni todas las especies. No confirma que un animal sea real frente a una foto o una pantalla.", 12f))
        body.addView(text("Solo se conserva la última foto en esta app, sin subirla a Firebase ni agregarla a una ficha. Se elimina al usar el botón anterior o al desinstalar la app. Evita incluir personas y datos privados.", 12f))
        action("Volver") { finish() }
        setContentView(ScrollView(this).apply { addView(body) })
        model.state.observe(this) { state ->
            preview.setImageBitmap(state.photo)
            preview.visibility = if (state.photo == null) View.GONE else View.VISIBLE
            result.text = state.title; detail.text = state.detail
            progress.visibility = if (state.busy) View.VISIBLE else View.GONE
            take.isEnabled = !state.busy
            retry.isEnabled = !state.busy && state.photo != null
            clear.isEnabled = !state.busy && state.photo != null
        }
    }
}
