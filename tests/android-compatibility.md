# Compatibilidad Android — trabajo de Juan David

## Punto de partida comprobado el 20 de octubre de 2026

Estado actualizado el 22 de octubre: mejora de memoria y contraste comprobados; tres recorridos completos aprobados en Android 7 y 15 con el mismo APK QA contra Firebase local. El APK normal también pasó instalación, arranque y prueba de fotos. Las evidencias del 20 de octubre que siguen describiendo pendientes son antecedentes históricos; los resultados nuevos y sus límites aparecen al final y en `android-functional-compatibility.md`.

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

## Pendientes registrados el 20 de octubre de 2026

La parte de implementación y la comprobación específica de memoria de Juan David quedan preparadas. El equipo debe completar lo siguiente antes de declarar terminado el taller:

1. Ejecutar en ambos Android el recorrido de las tres funciones confirmadas, con cuentas y datos ficticios y evidencia de confirmación del servidor. Utilizar un entorno local de Firebase como describen README y los protocolos existentes; no sembrar ni desplegar automáticamente en remoto.
2. Comprobar recuperación de borradores y reintentos según `android-recovery.md`, y revisar visualmente las miniaturas y el detalle tras abrir un directorio con fotos de demostración.
3. Observar mapa y directorio durante el uso real para responder qué operación consume más recursos. La prueba aislada de bitmaps no responde por sí sola esa comparación.
4. Completar las seis respuestas técnicas, matriz del grupo y reflexión individual de Juan David con sus propias observaciones. No inventar un error de compilación: se investigó un riesgo y se aplicó una optimización verificable.

Ricardo dispone del APK identificado, las evidencias iniciales y finales y `scripts/verify-android-compatibility.ps1` para repetir instalación, arranque y memoria. Esta prueba parcial no sustituye sus recorridos funcionales.

## Registro de cambios y apoyo de IA

En esta preparación se creó la rama, se ejecutó compilación y lint, se inspeccionó el APK y se redactó esta bitácora con apoyo de Codex. No se observaron errores de compilación de la app. Los primeros intentos de Git/Gradle/ADB encontraron restricciones del entorno y se repitieron con permisos; no fueron incompatibilidades Android.

Durante la preparación hubo problemas de herramientas: el descargador Java no avanzaba, se completaron las imágenes desde el servidor oficial y se verificaron sus hashes; se corrigió su registro local en el SDK y se indicó explícitamente el directorio de AVD. Estas incidencias no fueron errores de compilación ni incompatibilidades de la app.

Codex propuso el ajuste del muestreo y preparó código y comprobaciones. Juan David debe revisar la decisión, observar las evidencias y explicar sus consecuencias con sus propias palabras. Al redactar el avance del 20 de octubre quedaban pendientes los recorridos y la reflexión. Los recorridos se completaron el 22 de octubre mediante automatización; las reflexiones personales siguen pendientes. Esta bitácora no reemplaza esas evidencias ni su defensa personal.


## Recorridos completados el 22 de octubre de 2026

Juan David continuó los recorridos inicialmente asignados a Ricardo, con instrumentación de los controles reales y datos ficticios. El protocolo reproducible está en [android-functional-compatibility.md](android-functional-compatibility.md).

Se añadió una variante QA que comparte `src/main`, pero usa paquete `com.mascotasmunicipales.compatqa` y proyecto `demo-mascotas-compat` con Authentication y Firestore locales. No se alteró la conexión de la app normal, las reglas o índices remotos, ni se desplegaron datos. Los dos APK son distintos: los recorridos completos prueban QA local, no el servidor remoto.

También se corrigió el contraste de la barra del sistema: antes de API 26 se usa `tealDark` de la paleta actual con iconos claros; desde API 26 se mantiene fondo claro y solicitud de iconos oscuros. La revisión visual de todas las combinaciones de accesibilidad sigue fuera de estas pruebas.

| Comprobación final | Android 7 API 24 | Android 15 API 35 |
| --- | --- | --- |
| Registro, salida, ingreso y salida final | Aprobado | Aprobado |
| Alta y consulta de mascota con confirmación local del servidor | Aprobado | Aprobado |
| Alta y consulta de reporte con confirmación local del servidor | Aprobado | Aprobado |
| Rechazo de lectura de reporte de otra cuenta | PERMISSION_DENIED | PERMISSION_DENIED |
| Pendiente ausente del servidor mientras Firestore está sin red | Comprobado | Comprobado |
| Mismo ID tras detener el proceso y reabrir | Comprobado | Comprobado |
| Reconexion y recuento propio final | 1 mascota y 2 reportes | 1 mascota y 2 reportes |
| Mediana de tres muestras PSS del directorio | 63,9 MiB | 116,2 MiB |
| Mediana de tres muestras PSS del mapa | 67,7 MiB | 121,4 MiB |
| APK normal: instalación, arranque y bitmaps | Aprobado | Aprobado |

El mapa mostró teselas en las capturas de ambos. En este recorrido tuvo más PSS que el directorio. Las muestras incluyen instrumentación, caché y efectos del orden de navegación; no prueban CPU, batería, rendimiento en teléfonos físicos ni el máximo consumo de toda la app. El 75 % sigue refiriéndose únicamente a los cuatro bitmaps, no a toda la memoria del proceso.

Mismo APK QA en ambas APIs: `apk/MascotasMunicipales-QA-local-2026-10-22.apk`, SHA-256 `3E1EC328C23B95E5B765D2ED94B435CFD14D0A93410ECD1414B0A796B87EC7B1`. APK normal actualizado, también idéntico entre sus dos comprobaciones: `apk/MascotasMunicipales-compatibilidad-2026-10-22.apk`, SHA-256 `2ABB17D05AEF2A0624C9E364658CAAB7BA6AF2D45938636FBFADE744956AC0BF`.

Evidencia final: `evidencias-compatibilidad/funcionales-2026-10-22/taller22d/api24` y `api35`, con 12 capturas por API, resultados JSON y comprobaciones externas del servidor. `normal-api24` y `normal-api35` identifican el APK normal. Se conservan registros de los intentos previos; el Logcat histórico de API 24 incluye el fallo inicial de configuración de QA y no debe confundirse con un fallo del recorrido final. `android-runtime.txt` de taller22d está delimitado por hora de inicio; los mensajes anteriores quedan aparte.

Validación final: `assembleDebug`, `lintDebug`, `assembleDebugAndroidTest`, `assembleCompatibility`, `assembleCompatibilityAndroidTest`, `lintCompatibility` aprobados; ambos lint tienen 0 errores y 40 advertencias. Las 33 pruebas de reglas pasaron en el servidor local antes de sembrar las fichas de recorrido. No se repitieron contra producción.

Quedan las cuatro reflexiones individuales de 100 a 150 palabras, revisión y defensa del grupo, y entrega del proyecto o su enlace junto con el informe/APK. QR con cámara, todos los modos de accesibilidad y roles profesionales no se recorrieron de nuevo. El taller Offline First con persistencia propia y sincronización manual a RTDB sigue pendiente y separado.
