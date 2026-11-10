package com.mascotasmunicipales

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions

data class AnimalLabel(val text: String, val confidence: Float)
data class AnimalDecision(val valid: Boolean, val title: String, val detail: String)

/** Lista explícita del modelo base; nunca se busca "dog" como subcadena de "hot dog". */
object AnimalRecognitionPolicy {
    private val animals = mapOf(
        "Dog" to "un perro", "Cat" to "un gato", "Bird" to "un ave",
        "Horse" to "un caballo", "Cattle" to "un bovino", "Bull" to "un toro",
        "Duck" to "un pato", "Waterfowl" to "un ave acuática", "Turtle" to "una tortuga",
        "Gerbil" to "un jerbo", "Pomacentridae" to "un pez", "Butterfly" to "una mariposa",
        "Insect" to "un insecto", "Bear" to "un oso", "Penguin" to "un pingüino",
        "Crocodile" to "un cocodrilo", "Seal" to "una foca",
        "Shetland sheepdog" to "un perro", "Basset hound" to "un perro",
        "Dalmatian" to "un perro", "Cairn terrier" to "un perro", "Shikoku" to "un perro",
        "Sphynx" to "un gato", "Pixie-bob" to "un gato", "Ragdoll" to "un gato"
    )

    fun decide(labels: List<AnimalLabel>): AnimalDecision {
        val candidates = labels.filter { it.confidence.isFinite() && it.confidence in 0f..1f }
        val animal = candidates.filter { it.text in animals }.maxByOrNull { it.confidence }
        val toy = candidates.any { it.text in setOf("Toy", "Stuffed toy", "Plush", "Statue") && it.confidence >= .7f }
        if (toy) return AnimalDecision(false, "Foto no válida", "Parece un juguete o una figura. Toma una foto del animal.")
        if (animal != null && animal.confidence >= .75f) {
            val other = candidates.any {
                it.text in animals && animals[it.text] != animals[animal.text] &&
                    it.confidence >= .75f && animal.confidence - it.confidence < .15f &&
                    // Un pato también es un ave; no se trata como conflicto de especie.
                    !(setOf(it.text, animal.text).all { name -> name in setOf("Bird", "Duck", "Waterfowl", "Penguin") }) &&
                    !(setOf(it.text, animal.text).all { name -> name in setOf("Cattle", "Bull") }) &&
                    !(setOf(it.text, animal.text).all { name -> name in setOf("Insect", "Butterfly") })
            }
            if (other) return AnimalDecision(false, "Resultado no concluyente", "Aparecen varios animales o hay duda. Fotografía uno solo y vuelve a intentar.")
            return AnimalDecision(true, "Parece ${animals.getValue(animal.text)}", "Animal reconocido. Foto válida para esta comprobación.")
        }
        return AnimalDecision(false, "Foto no válida", "No se reconoció un animal con suficiente confianza. Acércate, mejora la luz y vuelve a intentar. El modelo no reconoce todas las especies.")
    }
}

/** Modelo incluido en el APK; las imágenes se procesan en este dispositivo. */
class AnimalRecognizer : AutoCloseable {
    private val labeler: ImageLabeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder().setConfidenceThreshold(.3f).build())

    fun recognize(bitmap: Bitmap): Task<List<AnimalLabel>> = labeler.process(InputImage.fromBitmap(bitmap, 0))
        .continueWith { task -> task.result.map { AnimalLabel(it.text, it.confidence) } }

    override fun close() = labeler.close()
}
