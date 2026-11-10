# Reconocimiento local de animales

## Alcance y arquitectura

Versión 1.6, en la rama existente `codex/android-compatibilidad`. Se añade una Activity independiente para no cambiar el guardado de mascotas, reportes, roles ni las pruebas anteriores. El botón **Reconocer animal con foto** aparece antes de ingresar, en Inicio y en los formularios de mascota y reporte. Al volver, los borradores existentes permanecen en su formulario.

La cámara instalada en Android toma una foto mediante `ActivityResultContracts.TakePicture`, con un URI temporal de FileProvider. La app no declara permiso CAMERA ni permisos generales de archivos. Solo se comparte la captura privada con la cámara elegida. Si no hay cámara disponible, cancelar o abrir falla, se muestra un mensaje y es posible volver a intentar.

`AnimalPhotoStore` reduce el lado mayor a un máximo de 1280 píxeles mediante muestreo, corrige los ocho valores de orientación EXIF y escribe JPEG sin los metadatos originales. Conserva únicamente `last.jpg`, con escritura atómica, en almacenamiento privado. La captura original se retira después de procesarla. La última imagen, válida o rechazada, se mantiene para revisar el resultado y se analiza nuevamente al abrir la pantalla. El usuario puede eliminarla. La carpeta se excluye del respaldo de Android y de transferencia entre dispositivos; no se guarda en la galería ni en Firebase. Se comparte entre quienes utilicen esta herramienta local en ese dispositivo, incluso antes de iniciar sesión; la pantalla lo identifica como última foto de la app.

`AnimalRecognitionViewModel` conserva estado durante recreaciones, ejecuta lectura/compresión en un hilo de trabajo y bloquea capturas repetidas mientras procesa. Usa ML Kit Image Labeling 17.0.9 con el modelo incluido en el APK. La inferencia no requiere descargar un modelo ni conectarse a Firebase. No se incorporan Storage, Functions, Realtime Database, claves de servicios ni facturación.

## Decisión y límites

La política usa coincidencias exactas de etiquetas de la lista oficial: perros, gatos, aves, caballo, bovinos, pato, tortuga, jerbo, una familia de peces, insectos y algunos animales adicionales. Algunas etiquetas de razas se agrupan como perro o gato, pero la UI no presenta una raza identificada. No se usan subcadenas: `Hot dog` no se interpreta como `Dog`.

Se acepta una etiqueta animal con puntuación de al menos 0,75. Dos tipos diferentes con puntuaciones altas y diferencia menor que 0,15 generan resultado no concluyente; se permiten etiquetas compatibles como ave/pato y bovino/toro. Juguete, peluche o estatua con puntuación de al menos 0,70 rechazan la imagen. Son umbrales iniciales de producto, no una garantía de exactitud calibrada.

Resultado aceptado: **Parece un perro**, por ejemplo, y foto válida para esta comprobación. Sin evidencia suficiente: **Foto no válida**, explicando que no se reconoció un animal y sugiriendo repetir. Un error técnico dice **No se pudo analizar la foto** y no se presenta como prueba de que no hay un animal. Las imágenes borrosas, especies no incluidas, varios animales y juguetes pueden producir errores. El modelo no verifica vida ni diferencia de forma fiable un animal de su imagen en papel o pantalla. No es un control de seguridad ni identifica individuos o razas.

El reconocimiento no adjunta la foto a Firestore, no modifica automáticamente el campo Especie, no bloquea los formularios históricos y no valida documentos en el servidor. Las fotos de las fichas siguen siendo recursos demostrativos. El taller Offline First con RTDB manual continúa pendiente.

## Pruebas reproducibles

```powershell
.\gradlew.bat -PanimalRecognitionTests :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
adb devices -l
adb -s SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s SERIAL install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s SERIAL shell am instrument -w com.mascotasmunicipales.test/com.mascotasmunicipales.AnimalRecognitionInstrumentation
```

Usar un solo AVD a la vez. La instrumentación no usa cuentas ni escribe en Firebase. Comprueba el modelo real con las fotos demostrativas de perro y gato, y un código QR como control sin animal. La política se prueba adicionalmente con etiquetas de caballo, tortuga, combinaciones ambiguas, juguetes, confianza baja, `NaN` y `Hot dog`. La cámara se sustituye por una imagen de prueba para comprobar entrega por URI, cancelación, rechazo, persistencia al abrir una nueva Activity y eliminación. También se comprueba EXIF y muestreo con una imagen de 2400×1200. Se restaura cualquier última foto que existiera antes de la prueba.

Esta instrumentación no sustituye la prueba física de la cámara ni mide precisión sobre todos los animales. La comparación funcional histórica de Firebase tampoco se da por repetida.

## Prueba manual pendiente en teléfono físico

1. Instalar el APK normal y abrir **Reconocer animal con foto** sin iniciar sesión.
2. Fotografiar por separado perro, gato, ave, caballo o tortuga, con buena luz; comprobar mensaje y vista previa.
3. Fotografiar mesa, botella, persona, planta y peluche; revisar rechazo o incertidumbre y registrar cualquier falso positivo.
4. Cancelar la cámara; probar doble toque, giro de pantalla, segundo plano y falta de espacio.
5. Cerrar completamente la app y reabrir la herramienta; verificar la última foto y que vuelva a analizarse.
6. Probar sin red y eliminar la foto. Confirmar que no se añadió una ficha ni se subió una imagen a Firebase.
7. Desde los formularios históricos, escribir datos ficticios, abrir/cerrar el reconocimiento y verificar conservación del borrador.

## Resultados de esta implementación

Pasaron `assembleDebug`, `lintDebug` y `assembleDebugAndroidTest`; lint terminó sin errores y con 46 advertencias. El APK universal firmado tiene 62.197.365 bytes aproximadamente y conserva minSdk 24, targetSdk 33 y compileSdk 35. El aumento de tamaño permite incluir el modelo y bibliotecas nativas en el APK, sin descarga inicial del modelo. Las salidas y capturas están en `tests/evidencias-reconocimiento-animal`.

| Comprobación | API 24 | API 35 |
| --- | --- | --- |
| Modelo real reconoce perro y gato | Aprobada | Aprobada |
| Modelo real rechaza imagen QR sin animal | Aprobada | Aprobada |
| Umbrales, ambigüedad y etiquetas de control | Aprobada | Aprobada |
| Captura por URI con imagen controlada y cancelación | Aprobada | Aprobada |
| Archivo persistente y nueva Activity/ViewModel | Aprobada | Aprobada |
| Borrado, reducción y orientación EXIF | Aprobada | Aprobada |

En API 35 se deshabilitaron temporalmente Wi-Fi y datos y se restauraron al finalizar. En API 24 se desconectaron los datos desde la consola del emulador y se restauraron; el estado observado fue móvil suspendido, por lo que no se afirma una prueba general de aislamiento de red en Android 7. Las comprobaciones no prueban todas las especies ni la precisión sobre fotografías nuevas.

Se registraron incidencias de preparación: un aviso de sistema no respondiente en el primer arranque de API 35, un monitor de cámara incompatible con API 24 y una intercepción indebida de la reapertura por el monitor clásico. Se liberó Gradle y se corrigió la instrumentación, sin aceptar permisos de cámara ni borrar datos del AVD. Las ejecuciones finales terminaron aprobadas. Las capturas con `sistema-anr` y `transicion` son antecedentes, no comprobaciones visuales finales. Se inspeccionaron las capturas finales de perro y rechazo de API 35, y la de perro de API 24.

## Fuentes

- [Integración oficial Android del modelo incluido](https://developers.google.com/ml-kit/vision/image-labeling/android).
- [Etiquetas oficiales del modelo base](https://developers.google.com/ml-kit/vision/image-labeling/label-map).
- [Captura de fotos mediante una aplicación de cámara](https://developer.android.com/media/camera/camera-deprecated/photobasics).
