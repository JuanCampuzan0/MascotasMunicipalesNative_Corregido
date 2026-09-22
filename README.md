# Mascotas Municipales — proyecto corregido

Proyecto Android nativo escrito en Kotlin. Esta versión corrige la sincronización con versiones recientes de Android Studio y mantiene compatibilidad con Android 13 Tiramisu.

## Correcciones realizadas

- Se agregó el Gradle Wrapper que faltaba en el ZIP recibido.
- Se actualizó Android Gradle Plugin a una versión compatible con el Wrapper incluido.
- Se conservó `compileSdk 35` y se restauró `targetSdk 33` para Android 13.
- `MainActivity` utiliza `AppCompatActivity` con un tema compatible.
- Las fotografías pasaron a `drawable-nodpi` para evitar que Android las amplíe según la densidad y provoque consumo excesivo de memoria o pantallas vacías.
- La pestaña seleccionada ahora cambia de color correctamente.

## Cómo abrirlo

1. Descomprime el ZIP.
2. En Android Studio usa **File > Open**.
3. Selecciona la carpeta `MascotasMunicipalesNative`, donde está `settings.gradle.kts`.
4. Espera a que termine **Gradle Sync**.
5. Ejecuta con el botón verde en un emulador o celular Android.

Las pantallas se construyen directamente desde `MainActivity.kt`; por eso no aparecen como archivos XML en el editor de diseño. Se observan al ejecutar la aplicación.
