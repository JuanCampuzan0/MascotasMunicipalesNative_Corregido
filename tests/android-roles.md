# Verificación de roles · 24 de septiembre de 2026

## Resultado

- `:app:assembleDebug :app:lintDebug --offline`: compilación correcta; lint sin errores, 32 advertencias. Incluyen cadenas escritas directamente en Kotlin, dependencias/SDK, orientación fija, recursos no usados, icono y recomendación KTX. No se presentan como corregidas.
- Firestore local: **28 pruebas aprobadas, 0 fallidas**. Incluyen el flujo completo, cierre sin atención, correcciones inmutables, aislamiento clínico, propietario, rol antiguo sin privilegios, autoasignación, asignación a veterinario activo, revocación, reasignación, dependencia, versiones antiguas, historial atómico, campos, vínculo de mascota y límites de consultas.
- Sembrado local: ejecutado dos veces; conservó los documentos existentes.
- Pixel 7 Pro / Android 13, compilación temporal con Auth/Firestore locales: inicio como administrador, revisión, asignación a `demo-vet`, persistencia tras reiniciar el proceso; ingreso veterinario, listado de caso asignado, registro de valoración/atención/seguimiento, estado Atendido tras reiniciar; ingreso administrador, ausencia de contenido clínico en su pantalla, cierre de caso y reporte; ingreso ciudadano, reporte Cerrado y consulta del resultado operativo; acceso profesional denegado al ciudadano; cierre de sesión.
- El campo de veterinario asignado del historial se añadió después del recorrido de pantallas; las 28 pruebas de reglas y compilación/lint se repitieron con el esquema final.

## Reproducir recorrido

1. Arrancar Authentication y Firestore locales y ejecutar las pruebas de reglas primero (limpian la base local).
2. Ejecutar el sembrado local de `ROLES.md` con una contraseña ficticia. Usar una compilación Android exclusiva de pruebas que apunte a los emuladores.
3. Ingresar `admin@example.test`, opción Administrador. Revisar `Pelusa ficticia`, abrir caso y asignar UID `demo-vet`.
4. Reiniciar el proceso; verificar estado Asignado desde la lectura del servidor.
5. Cerrar sesión. Ingresar `vet@example.test`, opción Veterinario, abrir Mis casos y registrar campos ficticios. Confirmar Atendido; reiniciar y volver a consultar.
6. Cerrar sesión. Ingresar administrador, comprobar que no aparecen notas clínicas, escribir resultado operativo y cerrar. Comprobar Cerrado.
7. Ingresar `citizen@example.test`, opción Ciudadano. Abrir Mis reportes, detalle y seguimiento. Verificar estado y resultado. Intentar Acceso profesional y comprobar rechazo.

## No verificado en esta entrega

- Despliegue remoto, disponibilidad de los nuevos índices en producción y cuentas profesionales reales: no se hicieron escrituras ni despliegues remotos.
- Dos dispositivos físicos simultáneos, TalkBack manual y pruebas amplias de compatibilidad.
- Repetición del protocolo offline ciudadano completo de 1.2 en esta versión. Se conserva aquel código; las pruebas anteriores están en `android-recovery.md`.
- Funcionamiento sin red de las operaciones profesionales: están diseñadas para exigir servidor y transacciones en línea; no se promete una cola profesional offline.

La compilación con conexión a los emuladores no forma parte del APK publicado. El APK entregado usa la configuración Firebase normal y necesitará reglas/índices nuevos y permisos aprobados antes de usar el módulo profesional.
