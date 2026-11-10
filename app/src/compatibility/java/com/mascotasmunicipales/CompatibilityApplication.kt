package com.mascotasmunicipales

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/** Solo se compila en la variante compatibility, con datos y paquete independientes. */
class CompatibilityApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        check(packageName == "com.mascotasmunicipales.compatqa")
        check(FirebaseApp.getInstance().options.projectId == "demo-mascotas-compat")
        FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099)
        FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080)
    }
}
