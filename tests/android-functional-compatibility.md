# Recorridos de compatibilidad Android 7 y Android 15

## Alcance

Prueba automatizada de los controles reales de `MainActivity`: registro de cuenta ciudadana, salida e ingreso; registro y consulta de mascota; creación y consulta de reporte; envío pendiente, reinicio del proceso y confirmación. Las altas se realizan mediante callbacks de los formularios. Lecturas `Source.SERVER` y un verificador externo confirman los documentos en el servidor local.

La variante `compatibility` comparte `src/main` con la app normal, pero usa paquete `com.mascotasmunicipales.compatqa`, sufijo de versión `-qa-local`, Application y configuración Firebase propios. Su proyecto es **demo-mascotas-compat** y solo permite HTTP a `10.0.2.2` para Authentication/Firestore emulados. No requiere ni contiene credenciales administrativas. La contraseña aleatoria de cada cuenta ficticia vive únicamente en memoria durante la prueba.

El APK QA requiere Firebase local. **No es el instalador normal ni prueba el backend remoto.** El APK normal conserva su paquete, configuración, datos y funciones. No se modificaron reglas ni índices remotos ni se habilitaron servicios pagos.

## Preparación reproducible

Desde la raíz, con Node, Java, dependencias npm y herramientas Android instalados:

```powershell
npm install
$env:FIREBASE_PROJECT_ID='demo-mascotas-compat'
$env:FIRESTORE_EMULATOR_HOST='127.0.0.1:8080'
$env:FIREBASE_AUTH_EMULATOR_HOST='127.0.0.1:9099'
node node_modules/firebase-tools/lib/bin/firebase.js emulators:start --only auth,firestore --project demo-mascotas-compat --config firebase.json
```

En otra terminal configure las mismas tres variables. Ejecute, en este orden:

```powershell
node --test tests/firestore.rules.test.mjs
node scripts/compatibility-local-data.mjs seed
.\gradlew.bat -PcompatibilityTests :app:assembleDebug :app:lintDebug :app:assembleCompatibility :app:assembleCompatibilityAndroidTest :app:lintCompatibility --offline
```

Las pruebas de reglas limpian la base local: **no ejecutarlas simultáneamente con los recorridos**. `seed` agrega únicamente cuatro fichas ficticias ausentes; no sobrescribe. El auxiliar se niega a usar otro proyecto o hosts externos. Si faltan dependencias Gradle en caché, primero complete su descarga sin `--offline`.

Inicie solo un AVD a la vez para cuidar la memoria del equipo. Los dedicados son `Mascotas_Compat_API24` y `Mascotas_Compat_API35`, en `D:\AndroidAVD\avd`; establezca `ANDROID_AVD_HOME` si fuera necesario. Use el serial real de `adb devices`, no lo deduzca del nombre. Espere `sys.boot_completed=1`.

```powershell
.\scripts\run-functional-compatibility.ps1 -Serial emulator-5554 -ExpectedApi 24 -RunId nuevo01
# Apagar Android 7, iniciar Android 15 y comprobar su serial/API.
$foreign=(Get-Content tests/evidencias-compatibilidad/funcionales-2026-10-22/nuevo01/api24/run.json -Raw | ConvertFrom-Json).reportId
.\scripts\run-functional-compatibility.ps1 -Serial emulator-5556 -ExpectedApi 35 -RunId nuevo01 -ForeignReportId $foreign
```

Use un RunId nuevo en cada repetición: la prueba crea una cuenta nueva por API. No recompilar entre ambos recorridos. `ForeignReportId` comprueba un rechazo `PERMISSION_DENIED` a un reporte de otra cuenta; es opcional al repetir, pero se ejecutó en ambos recorridos finales.

## Pasos y criterios

1. Crear cuenta ficticia `@example.test`, salir y volver a ingresar. Respetar la restauración de ruta; entrar en Inicio explícitamente después del login.
2. Guardar mascota Gato, Macho, comuna 2. Esperar confirmación del servidor y consultar su detalle. Verificar propietario, especie y comuna mediante lectura de servidor.
3. Crear reporte Encontrado, Gato, comuna 3, descripción ficticia. Esperar confirmación y consultar el detalle desde Mis reportes.
4. Abrir directorio con fotos, detalle de Luna y mapa. Capturar pantallas y tres muestras `Debug.MemoryInfo.totalPss` por pantalla tras una espera. No son mediciones de CPU/batería ni un benchmark controlado.
5. Deshabilitar **la red del SDK Firestore**, sin poner todo Android en modo avión. Crear otro reporte: estado pendiente y botón deshabilitado. El verificador externo debe encontrar un reporte normal y ningún documento con el ID pendiente.
6. Detener el proceso QA con `am force-stop`; reabrir con Firestore todavía sin red. Comprobar la misma sesión y el mismo ID, y capturar el pendiente recuperado.
7. Habilitar la red del SDK; esperar confirmación y consultar el reporte. El servidor debe terminar con exactamente una mascota y dos reportes propios (normal y recuperado). Salir de la cuenta.

La instrumentación se instala aparte y no se incluye en el APK de la app. No se usan datos reales. Las fotografías siguen siendo recursos locales de demostración; estos recorridos no implementan selección/persistencia de fotografías ni sincronización manual a RTDB.

## Resultados del 22 de octubre de 2026

Ejecuciones finales: `evidencias-compatibilidad/funcionales-2026-10-22/taller22d/api24` y `api35`. Cada una conserva 12 capturas, JSON de recorrido/recuperación/entorno, instalación, salida de instrumentación, verificación externa antes y después y Logcat delimitado por hora. La matriz y cifras finales están en el informe actualizado y `android-compatibility.md`.

Ambas APIs: tres funciones aprobadas, pendiente ausente antes de reconectar, mismo ID después de detener el proceso y confirmación sin duplicados. También se comprobó rechazo a reporte ajeno. Las 33 pruebas de reglas pasaron antes de sembrar los datos de recorridos.

El mismo APK normal se instaló y abrió en ambas APIs, y pasó de nuevo la prueba de miniaturas/caché/detalle. Su prueba no crea cuentas remotas; las pruebas funcionales completas corresponden al APK QA local, distinto del normal.

## Incidencias y límites

- Primer build QA: conflicto de etiqueta en manifest, resuelto con `tools:replace` en la variante.
- Primera instrumentación QA: uso de Firebase antes de terminar `Application.onCreate`, corregido esperando su inicialización antes de usar los SDK. El Logcat histórico conserva ese fallo; no corresponde al recorrido final ni a una incompatibilidad de Android 7.
- Segundo intento: la prueba esperaba Inicio después del login, aunque la aplicación restauraba Perfil. Se corrigió esa expectativa de la prueba.
- `taller22c` pasó ambas APIs, pero algunos mensajes quedaban fuera de las capturas. `taller22d` repitió con desplazamiento al estado y las fotos visibles. Sus logs separan mensajes anteriores de los de la ejecución.
- No se recorrieron nuevamente lector QR con cámara, todos los modos de accesibilidad o roles profesionales. No se probaron teléfonos físicos, backend remoto, agotamiento de cuota ni todos los posibles rechazos de red. Persistencia Firestore actual no equivale al taller Offline First de GUARDAR local y SINCRONIZAR manual hacia RTDB, aún pendiente.

Las cuatro reflexiones individuales de 100 a 150 palabras y la defensa requieren revisión y participación del grupo. No atribuir estos recorridos automatizados a Ricardo como trabajo manual suyo.
