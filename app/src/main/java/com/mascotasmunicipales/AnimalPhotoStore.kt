package com.mascotasmunicipales

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.AtomicFile
import java.io.File
import java.io.IOException

/** Una sola imagen reducida, privada, sin EXIF ni copia de seguridad. */
class AnimalPhotoStore(context: Context) {
    val directory = File(context.filesDir, "animal-recognition")
    val capture = File(directory, "capture.jpg")
    private val last = AtomicFile(File(directory, "last.jpg"))

    fun prepareCapture(): File {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("No se pudo preparar el almacenamiento local")
        capture.outputStream().use { }
        return capture
    }

    fun readLast(): Bitmap? = if (last.baseFile.exists() || File(directory, "last.jpg.bak").exists())
        last.openRead().use { BitmapFactory.decodeStream(it) ?: throw IOException("Imagen guardada ilegible") } else null

    fun save(bitmap: Bitmap) {
        val stream = last.startWrite()
        try {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 88, stream)) throw IOException("No se pudo guardar la foto")
            last.finishWrite(stream)
        } catch (error: Exception) {
            last.failWrite(stream)
            throw error
        }
    }

    fun clear() {
        if (capture.exists() && !capture.delete()) throw IOException("No se pudo eliminar la captura")
        last.delete()
        if (last.baseFile.exists() || File(directory, "last.jpg.bak").exists()) throw IOException("No se pudo eliminar la foto")
    }

    fun decodeCapture(): Bitmap {
        if (capture.length() !in 1L..32L * 1024 * 1024) throw IOException("Foto vacía o demasiado grande")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(capture.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Imagen ilegible")
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1280) sample *= 2
        val bitmap = BitmapFactory.decodeFile(capture.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IOException("No se pudo abrir la foto")
        val transform = Matrix()
        when (ExifInterface(capture.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> transform.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> transform.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> transform.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { transform.setRotate(90f); transform.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> transform.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { transform.setRotate(270f); transform.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> transform.setRotate(270f)
        }
        if (transform.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transform, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }
}
