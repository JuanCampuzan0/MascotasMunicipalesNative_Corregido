# Mascotas Municipales — prototipo Firebase

Aplicación Android Kotlin para registro de mascotas y reportes de pérdida/hallazgo en Zipaquirá. Conserva el estilo en español y cuatro fotografías de demostración incluidas en `drawable-nodpi`.

## Versión 1.3: roles y primer flujo municipal

Implementa ingreso por rol, revisión y asignación administrativa, atenciones veterinarias privadas, cierre atómico y seguimiento ciudadano. Consulte **[ROLES.md](ROLES.md)** para permisos, procedimiento de aprobación, conexión, índices y límites de esta primera etapa.

**Pendiente de despliegue:** las nuevas reglas e índices incluidos en esta versión todavía NO se han publicado en Firebase remoto. El acceso profesional requiere ese despliegue autorizado y documentos `access/{uid}` aprobados manualmente. Los permisos antiguos `users.role = staff` no conceden acceso con estas reglas.

## Recuperación y sesión (incluidas desde 1.2)

- `AppViewModel` conserva la sesión y las operaciones fuera de `MainActivity`. El ingreso tiene una sola transición a la aplicación, desactiva envíos repetidos y muestra errores de preparación del perfil con **Reintentar perfil**. La creación del perfil usa una transacción para tolerar el ingreso simultáneo desde dos dispositivos.
- `DraftStore` guarda un borrador de mascota y uno de reporte por UID, la pantalla actual y el regreso desde Perfil. Usa un archivo privado con escritura atómica en `noBackupFilesDir`; no guarda contraseñas ni se incluye en copias de seguridad. Guarda tras una pausa breve al escribir, al navegar, al pasar a segundo plano y antes de enviar.
- Cada envío recibe un ID estable que se guarda **antes** de llamar a Firestore. El formulario queda bloqueado mientras se comprueba el envío. Reintentar conserva ID y contenido; solo **Registrar otra mascota / Crear otro reporte**, después de la confirmación, inicia un registro diferente.
- Al reiniciar, la app espera que Firestore termine su cola pendiente y después consulta ese ID. Solo una respuesta de escritura exitosa o una lectura del servidor confirma la sincronización. La caché por sí sola no confirma. Un rechazo o una comprobación fallida mantiene el registro local y ofrece reintento; la ausencia de confirmación no se presenta como éxito.
- Los borradores se recuperan al volver a la misma cuenta en el mismo dispositivo. No se sincronizan entre dispositivos. Desinstalar o borrar los datos de la app elimina estos borradores y su historial local. Un cierre abrupto antes del guardado puede perder los últimos 250 ms de edición; el ID de un envío se guarda de forma síncrona antes de encolarlo. Si ese guardado falla, se bloquea el envío.

El APK de `apk/MascotasMunicipalesNative-corregido.apk` corresponde a **1.3 (versionCode 4)**. Es una compilación **debug para demostración**, con la configuración Firebase normal del proyecto. La compilación temporal usada para las pruebas locales no está incluida.

## Configuración manual necesaria

**Configuración Android:** `app/google-services.json` está incluido en el repositorio para la app `com.mascotasmunicipales` del proyecto Firebase `mascotas-municipales`. Es configuración de cliente; no contiene credenciales administrativas ni claves privadas. El repositorio es público, por lo que cualquier persona puede leer estos identificadores. Nunca agregue credenciales de cuenta de servicio ni contraseñas. Para usar otro proyecto Firebase, reemplace el archivo por el JSON descargado desde Firebase Console > Configuración del proyecto > Tus apps > app Android.

**Despliegue histórico (versión anterior, no estas reglas 1.3):** las reglas de `firestore.rules` se publicaron en la base `(default)` del proyecto configurado el 21 de septiembre de 2026. Los dos índices compuestos de `firestore.indexes.json` aparecen como **Habilitado** en Firebase Console. El proveedor Correo electrónico/contraseña también aparece **Habilitada**. No se sembraron mascotas, reportes ni usuarios en la base remota.

1. Compruebe que el proyecto Firebase siga en plan **Spark** sin facturación. La app Android ya está registrada con `com.mascotasmunicipales`.
2. Compruebe que **Authentication > Correo electrónico/contraseña** siga habilitado y que Cloud Firestore `(default)` esté disponible.
3. Para otro proyecto, revise `firestore.rules` y `firestore.indexes.json` y despliéguelos con autorización usando Firebase CLI y el ID real: `firebase deploy --only firestore:rules,firestore:indexes --project ID_REAL`.
4. Abra esta carpeta en Android Studio, sincronice Gradle y compile con `gradlew.bat :app:assembleDebug`.

Se usan AGP 9.3.1, Gradle 9.5, Kotlin integrado en AGP 9, Firebase BoM 34.19.0, Authentication y Firestore. No hay Storage, Functions, analítica ni servicio de pago. [Configuración oficial Android](https://firebase.google.com/docs/android/setup).

## Datos y permisos

| Colección | ID estable | Contenido | Acceso |
| --- | --- | --- | --- |
| `users/{uid}` | UID de Authentication | correo, rol, fechas | solo propio; creación propia como `citizen` |
| `pets/{petId}` | automático; demo con ID fijo | ficha pública, `ownerId`, clave de foto local, código, fechas | lectura pública; alta propia; cambios propios o de administrador autorizado |
| `reports/{reportId}` | automático | `ownerId`, especie, comuna, descripción, estado, fechas | propietario o administrador municipal |
| `territories/{territoryId}` | `comuna-1` a `comuna-4` | nombre de comuna | lectura pública; escritura administrativa |

Las contraseñas viven exclusivamente en Authentication. Contactos privados no se copian a fichas públicas. `ownerId` es un UID opaco, no teléfono, correo ni dirección. Firestore guarda solo claves de fotos locales, nunca imágenes ni Base64. Las marcas de tiempo vienen del servidor. Las reglas niegan por defecto otras rutas, validan campos y bloquean cambios de rol desde clientes.

**Roles profesionales:** se autorizan mediante documentos `access/{uid}`, nunca mediante cambios de rol desde el cliente. El procedimiento manual está en [ROLES.md](ROLES.md). El campo histórico `users.role` no otorga privilegios.

## Sincronización y límites

Firestore Android habilita persistencia sin conexión por defecto. Las listas usan metadatos: `hasPendingWrites` indica cambios locales pendientes y `isFromCache` distingue datos de caché. El éxito de escritura se muestra tras completarse la tarea del servidor. Sin red, el cambio puede verse localmente, pero sigue pendiente y podría fallar al reconectarse. La caché puede estar desactualizada y contiene solo documentos consultados previamente en ese dispositivo. Los conflictos en un documento siguen la regla «última escritura gana»; al sincronizar se vuelven a aplicar las reglas. Los recuentos requieren servidor y muestran «Sin conexión» si no está disponible. [Persistencia oficial](https://firebase.google.com/docs/firestore/manage-data/enable-offline), [agregaciones](https://firebase.google.com/docs/firestore/query-data/aggregation-queries).

Inicio lee solo tres mascotas recientes; los directorios de mascotas y reportes leen hasta 30 documentos. Los indicadores cuentan hasta 1000 registros y están etiquetados así. `firestore.indexes.json` define índices compuestos de reportes por propietario y fecha/estado; mascotas usa índice simple de `createdAt`. Los directorios ciudadanos aún no tienen botón «cargar más». Los nuevos directorios profesionales sí paginan de 20 en 20. Cada ficha se consulta por ID. Si una caché vacía no puede confirmarse con el servidor, la interfaz lo dice expresamente. Los formularios limitan la longitud a lo aceptado por las reglas y evitan enviar dos veces el mismo formulario mientras una escritura está pendiente.

Siguen **simulados**: foto de reportes, lectura física QR, contacto con la organización, jornadas, vacunación y adopciones. Las comunas son etiquetas del prototipo; no se presentan cifras municipales inventadas.

El botón **Perfil** abre un menú con Accesibilidad, Información de la aplicación, Información de tu cuenta, Cambiar correo, Cambiar contraseña y Cambiar información de tus mascotas. **Accesibilidad** y **Cerrar sesión** funcionan; las demás opciones siguen indicando **Próximamente**.

Accesibilidad también está disponible antes de iniciar sesión. Sus tres interruptores guardan preferencias en el dispositivo: texto de la app 30 % más grande (además de la escala de fuente del sistema), contraste alto y paleta azul para daltonismo. La interfaz expresa estados con palabras, diferencia la pestaña seleccionada por fondo y peso del texto, etiqueta formularios y registros para TalkBack y ofrece un acceso a los ajustes de accesibilidad de Android. La app no activa TalkBack por su cuenta. Los datos y preferencias de accesibilidad no se envían a Firebase.

## Emuladores y pruebas

`firebase.json` configura Authentication y Firestore locales. Instale Node y Firebase CLI, ejecute `pnpm install` o `npm install`, y configure `FIREBASE_PROJECT_ID` con el ID **real** creado. En PowerShell:

```powershell
$env:FIREBASE_PROJECT_ID = "ID_REAL"
firebase emulators:exec --only auth,firestore --project $env:FIREBASE_PROJECT_ID "node --test tests/firestore.rules.test.mjs"
```

En la versión 1.2, las 18 pruebas de reglas pasaron con emuladores locales. Cubren acceso anónimo a mascotas y reportes, creación legítima, límites de consultas, propiedad inmutable, privacidad, campos extra y tipos, fechas del servidor, permisos de funcionarios, borrados y escalada de rol al crear el propio perfil o actualizarlo. Cada prueba limpia la base del emulador y prepara sus propios datos; se exige `FIRESTORE_EMULATOR_HOST` en loopback para impedir apuntar estas pruebas a otro servidor. Ejecútelas con una base local de prueba desechable. Para probar Android contra emuladores, configure **solo una compilación local de prueba** con `FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099)` y `FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080)` antes de usar los SDK. No deje esa configuración en la compilación normal.

La compilación `gradlew.bat :app:assembleDebug` pasó con el archivo JSON entregado. También se probó en un emulador Pixel 7 Pro con Android 13 usando una compilación temporal dirigida **solo a los emuladores locales** de Authentication y Firestore. Pasaron registro, ingreso, cierre de sesión, recuperación de sesión tras reiniciar el proceso, alta y detalle de mascota, alta y detalle de reporte con especie/comuna/descripción, cambio a «Resuelto», aislamiento de reportes entre dos cuentas ficticias y sincronización de un reporte creado sin red (de «Pendiente de sincronización» a «Sincronizado» tras reconectar). Después de optimizar, se verificó que un doble toque sin red crea un solo reporte, que la confirmación tardía no saca al usuario de Perfil y que una lista vacía sin red se identifica como caché. La compilación temporal no forma parte de esta entrega. No se crearon usuarios ni registros en Firebase remoto. No se probó con dos dispositivos ni se verificó este flujo en producción.

En la versión 1.2, `gradlew.bat :app:lintDebug` terminó correctamente: cero errores y 16 advertencias no bloqueantes. Incluyen cadenas españolas escritas directamente en Kotlin, recursos visuales no usados, ausencia de ícono propio y recomendaciones de actualizar el SDK objetivo y algunas dependencias. Los cambios de SDK y orientación requieren una prueba de compatibilidad específica antes de aplicarse.

La siembra es opcional y explícita: `node scripts/seed-demo.mjs` solo muestra vista previa. Tras configurar credenciales administrativas de confianza y el ID real, `node scripts/seed-demo.mjs --apply` crea solo documentos ficticios de ID fijo ausentes; no sobrescribe. Para emulador use `FIRESTORE_EMULATOR_HOST=127.0.0.1:8080`. La app nunca siembra automáticamente.

## Verificación de la versión 1.3

Pasaron compilación y lint (0 errores, 32 advertencias) y **28 pruebas de reglas** con Firebase local. Se probó en Android el recorrido administrador → veterinario → cierre → ciudadano, con reinicios del proceso y cuentas ficticias. [Resultados y límites exactos](tests/android-roles.md). Los nuevos permisos e índices remotos siguen pendientes de autorización y despliegue.

## Verificación de la versión 1.2

Pasaron `:app:assembleDebug` y `:app:lintDebug --offline`, además de las 18 pruebas de reglas. En Pixel 7 Pro / Android 13 conectado únicamente a emuladores locales se verificaron borradores de reporte y mascota tras reiniciar el proceso, conservación de selecciones de especie/sexo/comuna, recreación de la Activity al cambiar la escala de texto del sistema, regreso desde Perfil, aislamiento de borradores entre dos cuentas ficticias y restauración al volver a la cuenta original.

También se comprobó un reporte enviado sin conexión: conservó el mismo ID tras reiniciar, no existía aún en el servidor mientras estaba pendiente, se confirmó al reconectar y el servidor contenía exactamente un documento con ese ID. Se comprobó además un fallo de creación del perfil y su reintento, y un reporte rechazado que conservó su ID tras reiniciar y se confirmó exactamente una vez al reintentar. El protocolo reproducible está en [tests/android-recovery.md](tests/android-recovery.md).

## Verificación pendiente en Firebase remoto

1. Registre dos usuarios ficticios y repita las pruebas de acceso con el proyecto remoto, si autoriza crear datos de demostración.
2. Abra otro dispositivo con la **misma cuenta** para comprobar visibilidad autorizada.
3. Repita la prueba sin red con el proyecto remoto y revise los campos en Firebase Console.
4. Vigile cuotas Spark. [Límites oficiales](https://firebase.google.com/docs/projects/billing/firebase-pricing-plans).

### Explicación breve para el profesor

La organización parte sin servidores ni base de datos de animales. El prototipo usa infraestructura administrada nueva: Authentication identifica usuarios y Cloud Firestore guarda mascotas, reportes y comunas; las reglas limitan quién lee y modifica cada registro. La app conserva una caché local y sincroniza al recuperar conexión. Spark permite una demostración sin tarjeta dentro de cuotas de lecturas, escrituras, almacenamiento y autenticación. Si se agotan, el servicio queda limitado. Esta versión no contiene datos oficiales ni garantiza una operación municipal de producción.
