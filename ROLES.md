# Roles y primer flujo municipal · versión 1.3

## Alcance de esta entrega

Dependencia municipal de Zipaquirá, identificada por `zipaquira-bienestar-animal`. No representa una plataforma para todo Cundinamarca. Los reportes y mascotas existentes pertenecen al único municipio de este prototipo. Una expansión a varios municipios exige migrar esos documentos y sus consultas antes de habilitar otros administradores.

Ingreso: **Ciudadano / Veterinario / Administrador de la dependencia**. Elegir una opción no modifica permisos. Authentication verifica la contraseña y Firestore comprueba el documento de acceso. Toda cuenta creada desde la app nace como ciudadana. Desde Inicio o Perfil se puede entrar al acceso profesional aprobado. Un rol incorrecto muestra un mensaje y permite abrir el acceso realmente aprobado o volver al área ciudadana.

Primer recorrido completo:

1. Ciudadano crea un reporte; administrador abre **Acceso profesional > Revisar reportes**.
2. **Revisar / abrir caso** crea un caso `Revisado` con el mismo ID del reporte, sin duplicarlo al repetir.
3. Administrador consulta **Equipo veterinario**, copia el UID de un veterinario ficticio activo y lo asigna. Puede reasignar antes de la atención. El servidor verifica rol, dependencia y estado activo.
4. Veterinario ve únicamente sus casos y registra valoración, atención realizada y seguimiento recomendado. El caso queda `Atendido`.
5. Las entradas clínicas son definitivas. Una corrección es una nueva entrada que explica qué corrige; nunca reemplaza la original. Tras cerrar el caso ya no se agregan entradas.
6. Administrador escribe un resultado operativo sin detalles clínicos ni contactos y cierra caso y reporte en una transacción. También puede cerrar un caso revisado sin intervención veterinaria. No puede saltar de asignado a cerrado.
7. Ciudadano abre **Mis reportes > Detalle > Ver seguimiento del caso** para consultar estado y resultado.

## Esquema y privacidad

| Ruta | Datos / acceso |
| --- | --- |
| `users/{uid}` | Correo y fechas; lectura solo propia. `role: citizen` se conserva por compatibilidad, pero no concede acceso profesional. Sin contraseñas. |
| `access/{uid}` | `role: admin/vet`, `departmentId`, `active`, `displayName` ficticio, `approvedBy`, `approvedAt`. Lectura propia; administrador consulta únicamente veterinarios de su dependencia. Ningún cliente puede escribir. |
| `pets/{id}` | Ficha pública existente. Sin historia clínica ni contactos. |
| `reports/{id}` | Propietario y administrador municipal. Un veterinario no obtiene acceso general a reportes. Un `petId` no vacío debe pertenecer al autor al crear el reporte. El formulario actual permite reportar un animal sin registro. |
| `cases/{reportId}` | Referencias inmutables a reporte, propietario y mascota; nombre, especie, comuna, dependencia; `vetId`, `status`, `outcome`, `lastRecordId`, `version`, `updatedBy`, fechas. Lectura del propietario, administrador de la dependencia o veterinario asignado activo. |
| `cases/{id}/clinicalRecords/{id}` | Autor, valoración, atención, seguimiento y fecha del servidor. Solo veterinario asignado activo; tampoco el administrador puede leerlos. Inmutables. |
| `cases/{id}/history/{version}` | Actor, veterinario asignado, estado, versión y fecha. Escritura atómica obligatoria con cada transición, sin modificación/borrado. Mismos lectores del caso. Registro técnico; todavía no hay pantalla de historial. |

El ciudadano recibe el resultado operativo, no la historia clínica. `vetId` y `updatedBy` son identificadores opacos y no contactos. Los campos de texto libre no se filtran automáticamente: use únicamente contenido ficticio apropiado para los lectores indicados. No introduzca datos clínicos en `outcome`.

## Aprobar cuentas: procedimiento manual de confianza

**No se han publicado estas reglas ni creado roles en Firebase remoto en esta entrega.** Requiere autorización del propietario antes del despliegue y de cualquier escritura remota.

1. Revisar los cambios de `firestore.rules` e índices. Con autorización, publicar ambos en el proyecto real y esperar a que los índices estén habilitados. No habilitar facturación.
2. Crear cuentas ficticias mediante el registro de la app. En Firebase Console > Authentication verificar el UID exacto de cada cuenta. No elegir permisos por el correo introducido en el cliente.
3. Una persona con acceso administrativo de confianza a Firebase crea `access/{UID}` en Firestore:
   - `role` (string): `admin` o `vet`.
   - `departmentId` (string): `zipaquira-bienestar-animal`.
   - `active` (boolean): `true`.
   - `displayName` (string): nombre ficticio, sin contacto privado.
   - `approvedBy` (string): identificador del responsable de la aprobación.
   - `approvedAt` (timestamp): fecha de la aprobación.
4. Mantener `users/{UID}.role` como `citizen`. Los antiguos `staff` NO se convierten en administradores: evaluar cada cuenta y aprobar explícitamente su nuevo documento `access`. Hasta entonces no tendrá permisos profesionales con las nuevas reglas.
5. Revocar con `access/{UID}.active = false`. Registrar manualmente quién y cuándo realizó la revocación. Verificar con una nueva lectura/intento de escritura. La app no puede aprobarse, reactivarse ni crear administradores.
6. No incorporar claves de servicio, contraseñas o credenciales administrativas al repositorio/APK. Para un uso real se necesitaría un proceso institucional de validación profesional y tratamiento de datos; esta entrega es ficticia.

## Conexión, conflictos y límites

Las funciones ciudadanas conservan su cola offline y borradores duraderos. El módulo profesional exige lecturas `Source.SERVER` y transacciones en línea. Una operación sin respuesta muestra **Esperando confirmación**; un error muestra **No confirmado**. Tras un error se debe actualizar el caso para comprobar su estado.

Cada caso tiene una versión. Una edición desde una versión antigua se rechaza con **El caso cambió**. Cierre y reporte se actualizan juntos. El ID estable del caso y el control de versión evitan duplicar revisión/atención por reintentos. Si el proceso termina antes de mostrar el éxito, volver a abrir el caso consulta el estado del servidor.

Los borradores clínicos solo se conservan en memoria durante la sesión, incluida rotación de pantalla. No sobreviven a terminar el proceso. No se confirma su guardado antes de la respuesta del servidor. Se eliminan de memoria al cambiar de cuenta; la app no guarda contraseñas.

Firestore mantiene su caché Android. `Source.SERVER` no elimina documentos ya descargados del almacenamiento del SDK. Cambiar permisos bloquea nuevas operaciones en el servidor, pero no borra información vista o almacenada anteriormente. Una pantalla ya abierta es una lectura puntual: actualizarla comprueba el estado actual; no se promete borrado remoto ni revocación visual instantánea. Utilice dispositivos de demostración y datos ficticios. No borrar caché automáticamente, porque podría perder escrituras ciudadanas pendientes.

Reportes profesionales, casos, equipo y atenciones se consultan en páginas de 20 con cursor. Cada página indica su cantidad, **no un total municipal**. Índices requeridos están en `firestore.indexes.json`: casos por dependencia/fecha; por dependencia/veterinario/fecha; equipo por dependencia/rol/ID. Las reglas no son filtros: las consultas veterinarias deben incluir su UID y dependencia.

## Emulador y datos explícitos

Ejecute primero las pruebas con el emulador local según README. Estas pruebas limpian SOLO el Firestore local, por lo que se deben ejecutar antes de preparar una demostración manual.

Sembrado opcional, repetible y exclusivo de emuladores locales:

```powershell
$env:FIREBASE_PROJECT_ID = "mascotas-municipales"
$env:FIRESTORE_EMULATOR_HOST = "127.0.0.1:8080"
$env:FIREBASE_AUTH_EMULATOR_HOST = "127.0.0.1:9099"
$env:DEMO_PASSWORD = "CONTRASENA_FICTICIA_LOCAL"
node scripts/seed-workflow-emulator.mjs         # vista previa
node scripts/seed-workflow-emulator.mjs --apply
```

Crea cuentas `citizen@example.test`, `admin@example.test`, `vet@example.test`, perfiles, dos permisos y `reports/demo-workflow` si faltan. No modifica documentos existentes, no reinicia casos, no cambia contraseñas existentes y nunca se ejecuta al iniciar la app. Rechaza cualquier host que no sea local.

Para probar desde Android, use exclusivamente una compilación de prueba que invoque `FirebaseAuth.useEmulator("10.0.2.2",9099)` y `FirebaseFirestore.useEmulator("10.0.2.2",8080)` antes de cualquier uso del SDK, con cleartext habilitado únicamente en su manifest debug. El APK entregado conserva Firebase normal; no contiene ese enlace al emulador.

## Próximas etapas

Quedan para siguientes entregas: aprobación veterinaria desde la app con auditoría completa; resumen administrativo agregado; edición administrativa de mascotas en UI; historial visual; agenda/recordatorios de seguimientos; acceso clínico del propietario con un esquema específico; ampliar formularios de vinculación de mascota; modernización visual. El seguimiento actual es un texto clínico persistido, no una agenda ni una notificación. No hay cargas de fotos ni servicios de pago.

## Explicación para el profesor

Firebase Authentication proporciona autenticación administrada y Cloud Firestore una base de datos nueva, persistente y compartida para el prototipo. Así la organización no necesita disponer previamente de servidores o registros digitales de animales. Las reglas controlan en el servidor quién puede consultar y modificar cada información. El plan Spark tiene cuotas de almacenamiento, lecturas y escrituras; no ofrece capacidad ilimitada y, al agotarlas, el servicio puede rechazar operaciones. Las consultas acotadas reducen consumo, pero reglas y lecturas auxiliares también pueden consumir cuota. Esta fase usa únicamente Authentication y Firestore, sin Storage, Functions ni facturación. Datos y cuentas son ficticios; no se presenta como una historia clínica institucional certificada.
