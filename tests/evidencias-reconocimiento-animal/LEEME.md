# Evidencias del reconocimiento local

- `api24-test.txt`: ejecución final aprobada en Android 7 con monitor clásico corregido.
- `api35-test-final.txt`: ejecución final aprobada en Android 15, sin Gradle activo, con red deshabilitada temporalmente.
- `api24-dog.png`, `api35-dog.png` y `api35-invalid.png`: capturas inspeccionadas de la interfaz real con imágenes ficticias y control QR.
- Archivos `intento`, `sistema-anr` y `transicion`: incidencias conservadas; no se usan como evidencia visual final.
- `../animal-recognition-build.log`: última compilación de app, lint e instrumentación, aprobada en 29 segundos.

Se usó el mismo APK normal en ambas API. SHA-256:

`2936D0533D1DB1616AD311995F48146391349C759D8A33B99C8022009C7D0C78`

El modelo de ML Kit se ejecutó realmente. El evento de cámara se sustituyó por una imagen controlada mediante un monitor de instrumentación; no se prueba el sensor de un teléfono. Las puntuaciones de las etiquetas son salidas del modelo, no porcentajes de precisión general. Los archivos del emulador pueden contener avisos ajenos al proceso de la app.

La preparación de API 35 inicialmente esperaba el serial 5554, pero el dispositivo recibió 5556. Se corrigió la selección tras consultar `adb devices`; la prueba final corresponde a ese dispositivo, cuya API se verificó. Se restauraron conectividad y escalas de animación al finalizar y se cerraron los AVD de prueba. No se cambiaron sus configuraciones persistentes de GPU ni los datos de las cuentas.

El protocolo y los límites completos están en [android-animal-recognition.md](../android-animal-recognition.md).
