package com.mascotasmunicipales

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.widget.ImageView

/** Comprueba bitmaps reales y la caché de la Activity sin escribir en Firebase. */
class PhotoCompatibilityInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        var activity: Activity? = null
        var resultCode = Activity.RESULT_OK
        var result = Bundle()
        try {
            activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val screen = activity as MainActivity
            val load = MainActivity::class.java.getDeclaredMethod("setPhoto",
                ImageView::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .apply { isAccessible = true }
            var baselineBytes = 0L
            var thumbnailBytes = 0L
            val side = (76 * targetContext.resources.displayMetrics.density).toInt()
            var mainError: Throwable? = null
            runOnMainSync {
                try {
                for (resource in listOf(R.drawable.luna, R.drawable.michi, R.drawable.nina, R.drawable.rocco)) {
                    val baseline = checkNotNull(BitmapFactory.decodeResource(targetContext.resources, resource,
                        BitmapFactory.Options().apply { inSampleSize = 2 }))
                    baselineBytes += baseline.byteCount
                    val card = ImageView(screen)
                    load.invoke(screen, card, resource, side)
                    val small = (card.drawable as BitmapDrawable).bitmap
                    check(small.width >= minOf(side, baseline.width) && small.height >= minOf(side, baseline.height))
                    check(small.byteCount <= baseline.byteCount)
                    thumbnailBytes += small.byteCount
                    val repeated = ImageView(screen)
                    load.invoke(screen, repeated, resource, side)
                    check((repeated.drawable as BitmapDrawable).bitmap === small) { "La tarjeta no reutiliza la caché" }
                    val detail = ImageView(screen)
                    load.invoke(screen, detail, resource, 0)
                    val large = (detail.drawable as BitmapDrawable).bitmap
                    check(large.width == baseline.width && large.height == baseline.height) { "El detalle perdió resolución" }
                    baseline.recycle()
                }
                } catch (error: Throwable) { mainError = error }
            }
            mainError?.let { throw it }
            result = Bundle().apply {
                putString("resultado", "APROBADO")
                putInt("api", Build.VERSION.SDK_INT)
                putInt("tarjetaPx", side)
                putLong("bytesAntesCuatroFotos", baselineBytes)
                putLong("bytesDespuesCuatroMiniaturas", thumbnailBytes)
                putString("alcance", "Bitmaps decodificados; no mide memoria total, CPU ni fluidez del dispositivo")
            }
        } catch (error: Throwable) {
            resultCode = Activity.RESULT_CANCELED
            result = Bundle().apply { putString("error", error.stackTraceToString()) }
        } finally {
            activity?.let { screen -> runOnMainSync { screen.finish() } }
        }
        finish(resultCode, result)
    }
}
