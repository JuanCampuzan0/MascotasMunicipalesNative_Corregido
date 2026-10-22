# Compatibilidad Android — trabajo de Juan David

## Punto de partida comprobado el 20 de octubre de 2026

Estado: mejora de memoria implementada, compilada y comprobada en Android 7 y 15. Los tres recorridos funcionales completos siguen pendientes del equipo.

El usuario confirmó que el profesor asignó **Android 7.0 / API 24** y **Android 15 / API 35**. También confirmó las tres funciones que deben conservarse: inicio de sesión, registro/consulta de mascota y creación/consulta de reporte.

- Rama: `codex/android-compatibilidad`, creada desde `4764e19`.
- Antes de empezar no había cambios versionados ni preparados para commit. Se conservaron los documentos, herramientas y carpetas `_qa_*` sin seguimiento.
- Aplicación 1.5, versionCode 6, paquete `com.mascotasmunicipales`.
- SDK del proyecto y APK comprobados: mínimo 24 (Android 7.0), objetivo 33, compilación 35.
- AGP 9.3.1 y Gradle 9.5.0. El lanzador usa JDK 21.0.6; `gradle/gradle-daemon-jvm.properties` solicita Java 25 para el daemon. Java 11 en `compileOptions` es el nivel de compilación del código, no la versión del JDK que ejecuta Gradle.
- Dependencias resueltas comprobadas en el informe de fusión del manifest: Authentication 24.2.0, Firestore 26.6.0 y Code Scanner 16.1.0.
- `gradlew.bat :app:assembleDebug :app:lintDebug --offline`: BUILD SUCCESSFUL. El informe nuevo de lint registra **0 errores y 43 advertencias**; las 44 advertencias citadas en README son un resultado histórico.
- En la consulta inicial no aparecieron dispositivos conectados ni AVD en el entorno utilizado. Al configurar explícitamente las herramientas se localizó un Pixel 7 Pro de Android 13 en `D:/AndroidAVD/avd`; no se modificó. Se crearon dos AVD exclusivos del taller y se comprobó instalación y arranque como se detalla más adelante.

El APK inicial se conserva localmente en `app/build/compatibility-baseline/app-debug-inicial.apk`, junto con `lint-inicial.txt`. Esta carpeta está excluida de Git y puede desaparecer con `clean`; no es el APK final de la tarea. El commit inicial permite recuperar el código de partida.

SHA-256 del APK inicial: `B0D9460140B9582D3706A647C51C361A2FE676AD52451A5D594C4D8A0EE9C030`.

## Riesgo investigado antes de adaptar

La consulta QR tiene una dependencia de ejecución adicional: Google Play Services y la descarga de su módulo. Un Android instalable no demuestra que el lector con cámara esté disponible. Ya existe una alternativa manual en `MainActivity`; todavía debe probarse en ambos equipos.

La [documentación oficial de Google Code Scanner](https://developers.google.com/ml-kit/vision/barcode-scanning/code-scanner) indica API 23 o superior y explica la descarga del módulo. El manifest del AAR 16.1.0 local declara API 21; este dato aislado no demuestra el soporte efectivo de todas sus dependencias ni del servicio. Los manifests locales de Authentication 24.2.0 y Firestore 26.6.0 declaran API 23. El APK completo declara API 24 y fusionó el manifest correctamente, pero falta la prueba de ejecución.

Alternativas para discutir con Juan Pablo y el equipo:

1. Conservar bibliotecas y probar lector y consulta manual. Mantiene funciones y evita introducir otra biblioteca; requiere comprobar Play Services y explicar las condiciones de la cámara.
2. Integrar otro lector con motor incluido si se exige cámara en un equipo sin Play Services. Aumentaría tamaño, permisos y mantenimiento, y exigiría pruebas adicionales. No se ha seleccionado ni implementado esta alternativa.

No hay evidencia que justifique bajar `targetSdk`, `compileSdk`, AGP o dependencias. Se conservan esas versiones y Firebase sin cambios.

## Cambio de Juan David

Observación del código y recursos: las cuatro fotografías tienen 1024 × 1024 píxeles. La implementación previa decodificaba siempre con `inSampleSize = 2`, incluso para una tarjeta de 76 dp. Eso produce bitmaps de 512 × 512 píxeles aunque la tarjeta necesite menos. Se identificó un riesgo de memoria; no se observó ni se afirma un cierre por falta de memoria.

En `MainActivity.setPhoto`, las tarjetas ahora seleccionan un muestreo en potencias de dos según el tamaño físico que necesitan. La caché diferencia recurso y muestreo; el detalle conserva la resolución anterior. No se eliminaron fotos ni funciones, ni se cambiaron permisos, colores, datos o dependencias de la app.

Alternativas consideradas para esta optimización:

- Ajustar el muestreo existente: cambio pequeño, sin dependencia nueva y sin reducir la calidad del detalle. Es la implementación aplicada; falta revisar visualmente el resultado en ambos Android.
- Añadir una biblioteca de imágenes: puede aportar carga asíncrona y gestión de caché, pero aumenta alcance, dependencias y mantenimiento para cuatro recursos locales. Se descartó para este cambio concreto.

La decisión sigue la [guía oficial de carga eficiente de bitmaps](https://developer.android.com/topic/performance/graphics/load-bitmap), que recomienda adaptar la resolución a la vista. El mapa también puede consumir recursos: no se ha medido qué operación consume más memoria en el recorrido completo y no debe presentarse esa comparación como realizada.

Después del cambio pasaron `:app:assembleDebug` y `:app:lintDebug --offline`. Tras incorporar la prueba Android también pasó `:app:assembleDebugAndroidTest`: **0 errores de lint y 40 advertencias**. La diferencia de advertencias no representa correcciones individuales verificadas.

## Resultados obtenidos

Se instalaron y abrieron el APK inicial y el optimizado en ambas versiones. El APK optimizado usado en los dos dispositivos es exactamente el mismo: `apk/MascotasMunicipales-compatibilidad.apk`, SHA-256 `5C7462623551FD6C960C05C0FC4BB3DB8912CA7AB5E21EE18A60537712BD19C8`.

| Comprobación | Android 7.0 / API 24 | Android 15 / API 35 |
| --- | --- | --- |
| Instalación e inicio del APK inicial | Correctos | Correctos |
| Instalación e inicio del APK optimizado | Correctos | Correctos |
| Tarjeta de 76 dp en el emulador de densidad 320 | 152 píxeles | 152 píxeles |
| Cuatro fotos con el muestreo anterior | 4.194.304 bytes (4 MiB) | 4.194.304 bytes (4 MiB) |
| Cuatro miniaturas con el cambio | 1.048.576 bytes (1 MiB) | 1.048.576 bytes (1 MiB) |
| Reducción de memoria de las miniaturas | 75 % | 75 % |
| Reutilización de caché y resolución del detalle | Prueba aprobada | Prueba aprobada |
| Sesión, registro/consulta de mascota y reporte completos | Pendientes | Pendientes |

Evidencias guardadas en `tests/evidencias-compatibilidad`: `api24-inicial`, `api35-inicial`, `api24` y `api35` contienen instalación, arranque, captura, memoria inicial e identificación del APK. Los dos últimos incluyen `fotos.txt` con la salida de la instrumentación. También se conservaron los informes de compilación y lint. Las capturas de inicio se inspeccionaron visualmente; muestran el formulario real sin cuentas ni datos personales. No son evidencia de haber completado el inicio de sesión.

El 75 % describe únicamente esos cuatro bitmaps en la densidad medida. No prueba una reducción del 75 % de la memoria total ni mayor velocidad de toda la app; otros tamaños de pantalla pueden necesitar otro muestreo. La comparación de bitmaps se ejecuta con el decodificador real de cada Android y reproduce el muestreo anterior; no es una medición de toda la navegación antes/después.

Una diferencia visible entre las capturas es el dibujo del símbolo de huella del encabezado: el Android antiguo lo presenta monocromo y el moderno coloreado. Corresponde al renderizado de la fuente/emoji del sistema; el texto y las acciones se conservan. No se diagnosticó un fallo funcional por esa diferencia. Las miniaturas todavía deben revisarse visualmente dentro de los recorridos autenticados.

Los emuladores dedicados son `Mascotas_Compat_API24` y `Mascotas_Compat_API35`, de 720 × 1280 píxeles, densidad 320 y dos núcleos, con 1536 y 2048 MiB de RAM respectivamente. Se guardaron en `D:/AndroidAVD/avd` y se apagaron al terminar para liberar memoria. Son emuladores, no teléfonos físicos de gama baja. Si la herramienta no los encuentra, debe usar ese directorio como `ANDROID_AVD_HOME`.

## Reproducción de la comprobación de Juan David

La instrumentación de `app/src/androidTest` no se incluye en el APK normal. Abre la Activity, compara bitmaps realmente decodificados con el muestreo anterior, comprueba la reutilización de caché y que el detalle conserva su resolución. No crea cuentas ni escribe en Firebase. Sus cifras son memoria de los bitmaps, no memoria total del proceso ni una medida de fluidez.

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest --offline
.\scripts\verify-android-compatibility.ps1 -Serial emulator-5554 -ExpectedApi 24
.\scripts\verify-android-compatibility.ps1 -Serial emulator-5556 -ExpectedApi 35
```

Los seriales anteriores son ejemplos: utilizar los que muestre `adb devices -l`. El script valida la API antes de instalar y genera evidencia en `tests/evidencias-compatibilidad/api24` o `api35`, con SHA-256 del APK, instalación, arranque, captura, memoria inicial y resultado de fotos. Se debe usar el mismo APK, sin recompilar entre ambos dispositivos. No ejecuta los tres recorridos funcionales completos.

## Pendientes y entrega a Ricardo

La parte de implementación y la comprobación específica de memoria de Juan David quedan preparadas. El equipo debe completar lo siguiente antes de declarar terminado el taller:

1. Ejecutar en ambos Android el recorrido de las tres funciones confirmadas, con cuentas y datos ficticios y evidencia de confirmación del servidor. Utilizar un entorno local de Firebase como describen README y los protocolos existentes; no sembrar ni desplegar automáticamente en remoto.
2. Comprobar recuperación de borradores y reintentos según `android-recovery.md`, y revisar visualmente las miniaturas y el detalle tras abrir un directorio con fotos de demostración.
3. Observar mapa y directorio durante el uso real para responder qué operación consume más recursos. La prueba aislada de bitmaps no responde por sí sola esa comparación.
4. Completar las seis respuestas técnicas, matriz del grupo y reflexión individual de Juan David con sus propias observaciones. No inventar un error de compilación: se investigó un riesgo y se aplicó una optimización verificable.

Ricardo dispone del APK identificado, las evidencias iniciales y finales y `scripts/verify-android-compatibility.ps1` para repetir instalación, arranque y memoria. Esta prueba parcial no sustituye sus recorridos funcionales.

## Registro de cambios y apoyo de IA

En esta preparación se creó la rama, se ejecutó compilación y lint, se inspeccionó el APK y se redactó esta bitácora con apoyo de Codex. No se observaron errores de compilación de la app. Los primeros intentos de Git/Gradle/ADB encontraron restricciones del entorno y se repitieron con permisos; no fueron incompatibilidades Android.

Durante la preparación hubo problemas de herramientas: el descargador Java no avanzaba, se completaron las imágenes desde el servidor oficial y se verificaron sus hashes; se corrigió su registro local en el SDK y se indicó explícitamente el directorio de AVD. Estas incidencias no fueron errores de compilación ni incompatibilidades de la app.

Codex propuso el ajuste del muestreo y preparó código y comprobaciones. Juan David debe revisar la decisión, observar las evidencias y explicar sus consecuencias con sus propias palabras. Quedan pendientes los recorridos funcionales completos del equipo y su reflexión individual. Esta bitácora no reemplaza esas evidencias ni su defensa personal.
