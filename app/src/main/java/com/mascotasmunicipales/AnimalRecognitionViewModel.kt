package com.mascotasmunicipales

import android.app.Application
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.Executors

data class AnimalRecognitionState(
    val photo: Bitmap? = null,
    val busy: Boolean = false,
    val title: String = "Fotografía un animal",
    val detail: String = "Colócalo en el centro, con buena luz y sin otros animales alrededor.",
    val valid: Boolean = false
)

class AnimalRecognitionViewModel(application: Application) : AndroidViewModel(application) {
    val photos = AnimalPhotoStore(application)
    private val current = MutableLiveData(AnimalRecognitionState())
    val state: LiveData<AnimalRecognitionState> = current
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: AnimalRecognizer? = null
    private var generation = 0
    private var closed = false

    init { analyze(captured = false) }

    fun beginCapture(): Boolean {
        if (current.value?.busy == true) return false
        current.value = (current.value ?: AnimalRecognitionState()).copy(busy = true, title = "Abriendo cámara…", valid = false)
        return true
    }

    fun finishCapture(success: Boolean) {
        if (success) analyze(captured = true)
        else {
            photos.capture.delete()
            current.value = (current.value ?: AnimalRecognitionState()).copy(busy = false, valid = false,
                title = "Captura cancelada", detail = "No se analizó una foto nueva. Puedes volver a tomarla.")
        }
    }

    fun cameraUnavailable() {
        finishCapture(false)
        current.value = current.value?.copy(title = "No se pudo abrir la cámara",
            detail = "Comprueba que haya una aplicación de cámara disponible y vuelve a intentar.")
    }

    fun retry() { if (current.value?.busy != true) analyze(captured = false, existing = current.value?.photo) }

    fun clearPhoto() {
        if (current.value?.busy == true) return
        generation++
        try {
            photos.clear()
            current.value = AnimalRecognitionState()
        } catch (_: Exception) { failure("No se pudo eliminar la foto del dispositivo.") }
    }

    private fun failure(message: String) {
        current.value = (current.value ?: AnimalRecognitionState()).copy(busy = false, valid = false,
            title = "No se pudo analizar la foto", detail = message)
    }

    private fun analyze(captured: Boolean, existing: Bitmap? = null) {
        val request = ++generation
        current.value = (current.value ?: AnimalRecognitionState()).copy(busy = true, valid = false,
            title = "Analizando foto…", detail = "El reconocimiento se realiza en este dispositivo.")
        executor.execute {
            try {
                val bitmap = if (captured) photos.decodeCapture() else existing ?: photos.readLast()
                // Guardar antes de inferir permite recuperar también un análisis interrumpido.
                val saved = bitmap == null || runCatching { if (captured || existing != null) photos.save(bitmap) }.isSuccess
                if (captured) photos.capture.delete()
                main.post {
                    if (!closed && request == generation) {
                        if (bitmap == null) current.value = AnimalRecognitionState()
                        else {
                            current.value = AnimalRecognitionState(bitmap, true, "Analizando foto…", "Procesando en el dispositivo.")
                            try {
                                val client = recognizer ?: AnimalRecognizer().also { recognizer = it }
                                client.recognize(bitmap).addOnSuccessListener { labels ->
                                    if (!closed && request == generation) {
                                        val decision = AnimalRecognitionPolicy.decide(labels)
                                        current.value = AnimalRecognitionState(bitmap, false, decision.title,
                                            decision.detail + if (saved) "" else " No se pudo conservar la foto al cerrar; vuelve a tomarla cuando haya espacio.",
                                            decision.valid)
                                    }
                                }.addOnFailureListener {
                                    if (!closed && request == generation) {
                                        if (captured) photos.capture.delete()
                                        failure("El reconocimiento no terminó. Reintenta; este error no significa que la foto no contenga un animal.")
                                    }
                                }
                            } catch (_: Exception) {
                                if (captured) photos.capture.delete()
                                failure("No se pudo iniciar el reconocimiento. Vuelve a intentar.")
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                if (captured) photos.capture.delete()
                main.post { if (!closed && request == generation) failure("No se pudo abrir la imagen. Toma otra foto y comprueba el espacio disponible.") }
            }
        }
    }

    override fun onCleared() {
        closed = true
        generation++
        recognizer?.close()
        executor.shutdown()
        super.onCleared()
    }
}
