# Mascotas Municipales — prototipo Firebase

Aplicación Android Kotlin para registro de mascotas y reportes de pérdida/hallazgo en Zipaquirá. Conserva el estilo en español y cuatro fotografías de demostración incluidas en `drawable-nodpi`.

## Configuración manual necesaria

**Configuración Android:** el clon local usado para desarrollar y probar tiene `app/google-services.json`, que coincide con `com.mascotasmunicipales`. El archivo se excluye de GitHub mediante `.gitignore`. Tras clonar el repositorio, descargue `google-services.json` desde Firebase Console > Configuración del proyecto > Tus apps > app Android y colóquelo en `app/google-services.json` antes de sincronizar Gradle. No use el JSON de otro proyecto.

**Estado de despliegue:** las reglas de `firestore.rules` se publicaron en la base `(default)` del proyecto configurado el 21 de septiembre de 2026. Los dos índices compuestos de `firestore.indexes.json` aparecen como **Habilitado** en Firebase Console. El proveedor Correo electrónico/contraseña también aparece **Habilitada**. No se sembraron mascotas, reportes ni usuarios en la base remota.

1. Compruebe que el proyecto Firebase siga en plan **Spark** sin facturación. La app Android ya está registrada con `com.mascotasmunicipales`.
2. Compruebe que **Authentication > Correo electrónico/contraseña** siga habilitado y que Cloud Firestore `(default)` esté disponible.
3. Para otro proyecto, revise `firestore.rules` y `firestore.indexes.json` y despliéguelos con autorización usando Firebase CLI y el ID real: `firebase deploy --only firestore:rules,firestore:indexes --project ID_REAL`.
4. Abra esta carpeta en Android Studio, sincronice Gradle y compile con `gradlew.bat :app:assembleDebug`.

Se usan AGP 9.3.1, Gradle 9.5, Kotlin integrado en AGP 9, Firebase BoM 34.19.0, Authentication y Firestore. No hay Storage, Functions, analítica ni servicio de pago. [Configuración oficial Android](https://firebase.google.com/docs/android/setup).

## Datos y permisos

| Colección | ID estable | Contenido | Acceso |
| --- | --- | --- | --- |
| `users/{uid}` | UID de Authentication | correo, rol, fechas | propio o funcionario; creación propia como `citizen` |
| `pets/{petId}` | automático; demo con ID fijo | ficha pública, `ownerId`, clave de foto local, código, fechas | lectura pública; alta propia; cambios propios o de funcionario |
| `reports/{reportId}` | automático | `ownerId`, especie, comuna, descripción, estado, fechas | propietario o funcionario |
| `territories/{territoryId}` | `comuna-1` a `comuna-4` | nombre de comuna | lectura pública; escritura administrativa |

Las contraseñas viven exclusivamente en Authentication. Contactos privados no se copian a fichas públicas. `ownerId` es un UID opaco, no teléfono, correo ni dirección. Firestore guarda solo claves de fotos locales, nunca imágenes ni Base64. Las marcas de tiempo vienen del servidor. Las reglas niegan por defecto otras rutas, validan campos y bloquean cambios de rol desde clientes.

**Funcionario ficticio:** registre primero una cuenta de demostración. Una persona autorizada debe comprobar su UID en Authentication y cambiar `users/{uid}.role` de `citizen` a `staff` mediante la consola Firestore o Admin SDK desde un entorno de confianza. Verifique el UID. No incluya credenciales administrativas en la app. No se hizo ninguna asignación real.

## Sincronización y límites

Firestore Android habilita persistencia sin conexión por defecto. Las listas usan metadatos: `hasPendingWrites` indica cambios locales pendientes y `isFromCache` distingue datos de caché. El éxito de escritura se muestra tras completarse la tarea del servidor. Sin red, el cambio puede verse localmente, pero sigue pendiente y podría fallar al reconectarse. La caché puede estar desactualizada y contiene solo documentos consultados previamente en ese dispositivo. Los conflictos en un documento siguen la regla «última escritura gana»; al sincronizar se vuelven a aplicar las reglas. Los recuentos requieren servidor y muestran «Sin conexión» si no está disponible. [Persistencia oficial](https://firebase.google.com/docs/firestore/manage-data/enable-offline), [agregaciones](https://firebase.google.com/docs/firestore/query-data/aggregation-queries).

Cada lista lee un máximo de 30 documentos recientes. Los indicadores cuentan hasta 1000 registros y están etiquetados así. `firestore.indexes.json` define índices compuestos de reportes por propietario y fecha/estado; mascotas usa índice simple de `createdAt`. Aún no hay botón «cargar más», así que los documentos anteriores al límite no aparecen. Cada ficha se consulta por ID.

Siguen **simulados**: foto de reportes, lectura física QR, contacto con la organización, jornadas, vacunación y adopciones. Las comunas son etiquetas del prototipo; no se presentan cifras municipales inventadas.

## Emuladores y pruebas

`firebase.json` configura Authentication y Firestore locales. Instale Node y Firebase CLI, ejecute `pnpm install` o `npm install`, y configure `FIREBASE_PROJECT_ID` con el ID **real** creado. En PowerShell:

```powershell
$env:FIREBASE_PROJECT_ID = "ID_REAL"
firebase emulators:exec --only auth,firestore --project $env:FIREBASE_PROJECT_ID "node --test tests/firestore.rules.test.mjs"
```

Las ocho pruebas de reglas pasaron con emuladores locales. Cubren acceso anónimo, creación legítima, consultas acotadas, propiedad, privacidad de usuarios, lectura de funcionario y escalada de rol. Para probar Android contra emuladores, configure **solo una compilación local de prueba** con `FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099)` y `FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080)` antes de usar los SDK. No deje esa configuración en la compilación normal.

La compilación `gradlew.bat :app:assembleDebug` pasó con el archivo JSON entregado. También se probó en un emulador Pixel 7 Pro con Android 13 usando una compilación temporal dirigida **solo a los emuladores locales** de Authentication y Firestore. Pasaron registro, ingreso, cierre de sesión, recuperación de sesión tras reiniciar el proceso, alta y detalle de mascota, alta y detalle de reporte con especie/comuna/descripción, cambio a «Resuelto», aislamiento de reportes entre dos cuentas ficticias y sincronización de un reporte creado sin red (de «Pendiente de sincronización» a «Sincronizado» tras reconectar). La compilación temporal no forma parte de esta entrega. No se crearon usuarios ni registros en Firebase remoto. No se probó con dos dispositivos ni se verificó este flujo en producción.

La siembra es opcional y explícita: `node scripts/seed-demo.mjs` solo muestra vista previa. Tras configurar credenciales administrativas de confianza y el ID real, `node scripts/seed-demo.mjs --apply` crea solo documentos ficticios de ID fijo ausentes; no sobrescribe. Para emulador use `FIRESTORE_EMULATOR_HOST=127.0.0.1:8080`. La app nunca siembra automáticamente.

## Verificación pendiente en Firebase remoto

1. Registre dos usuarios ficticios y repita las pruebas de acceso con el proyecto remoto, si autoriza crear datos de demostración.
2. Abra otro dispositivo con la **misma cuenta** para comprobar visibilidad autorizada.
3. Repita la prueba sin red con el proyecto remoto y revise los campos en Firebase Console.
4. Vigile cuotas Spark. [Límites oficiales](https://firebase.google.com/docs/projects/billing/firebase-pricing-plans).

### Explicación breve para el profesor

La organización parte sin servidores ni base de datos de animales. El prototipo usa infraestructura administrada nueva: Authentication identifica usuarios y Cloud Firestore guarda mascotas, reportes y comunas; las reglas limitan quién lee y modifica cada registro. La app conserva una caché local y sincroniza al recuperar conexión. Spark permite una demostración sin tarjeta dentro de cuotas de lecturas, escrituras, almacenamiento y autenticación. Si se agotan, el servicio queda limitado. Esta versión no contiene datos oficiales ni garantiza una operación municipal de producción.
