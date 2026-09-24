package com.mascotasmunicipales

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Un archivo privado por cuenta, excluido de copias de seguridad. Nunca contiene contraseñas. */
class DraftStore(context: Context, uid: String) {
    private val key = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray())
        .joinToString("") { "%02x".format(it) }
    private val file = AtomicFile(File(context.noBackupFilesDir, "drafts-$key.json"))

    fun read(): JSONObject = if (!file.baseFile.exists()) JSONObject()
        else JSONObject(file.openRead().bufferedReader().use { it.readText() })

    fun write(value: JSONObject) {
        val stream = file.startWrite()
        try {
            stream.write(value.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }
}

class Draft(val json: JSONObject = JSONObject()) {
    var id: String
        get() = json.optString("id")
        set(value) { json.put("id", value) }
    var status: String
        get() = json.optString("status", "draft")
        set(value) { json.put("status", value) }
    var error: String
        get() = json.optString("error")
        set(value) { json.put("error", value) }
    val locked: Boolean get() = id.isNotEmpty()
    fun value(key: String, default: String = "") = json.optString("field:$key", default)
    fun set(key: String, newValue: String): Boolean {
        if (locked || value(key) == newValue) return false
        json.put("field:$key", newValue)
        return true
    }
}
