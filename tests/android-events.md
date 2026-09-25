# Verificación de eventos territoriales · versión 1.4

## Seguridad automatizada

La suite completa pasó con Firestore Emulator: **32 pruebas aprobadas, 0 fallidas**. Las cuatro nuevas comprueban que solo un veterinario activo de la dependencia crea una solicitud futura; pendiente y rechazado permanecen privados; únicamente el administrador decide; el contenido no cambia durante la decisión; solo aprobado se consulta públicamente; no hay segunda decisión ni borrado; y todas las listas exigen límite máximo de 20 y filtros compatibles con las reglas.

## Recorrido Android

Use cuentas y datos ficticios contra Authentication y Firestore locales. Ejecute antes las pruebas de reglas, porque limpian la base del emulador.

1. Ingrese como veterinario aprobado y abra **Acceso profesional > Proponer evento territorial**.
2. Complete nombre, tipo, comuna, lugar, descripción y una fecha/hora futura. Envíe y confirme el mensaje del servidor.
3. Abra **Mis solicitudes de eventos**: debe aparecer `Pendiente`. Cierre y abra de nuevo la app para confirmar lectura desde el servidor.
4. Ingrese como administrador y abra **Solicitudes de eventos**. Apruebe la solicitud; la tarjeta debe desaparecer de pendientes al actualizarse.
5. Ingrese como ciudadano o abra Territorio con una sesión ciudadana. El evento aprobado futuro debe aparecer con tipo, fecha, comuna, lugar y descripción.
6. Repita con otra solicitud y rechácela. Debe aparecer `Rechazado` para el veterinario y nunca en Territorio.
7. Quite la red antes de proponer o revisar: la operación profesional debe quedar sin confirmar, sin presentarse como éxito. Actualice después de reconectar.

## Límites

- La propuesta en edición se conserva solo en memoria durante la sesión; sobrevive a rotación, pero no al cierre del proceso.
- No hay edición, cancelación, notificaciones, inscripción, mapa ni ejecución real de servicios.
- Territorio muestra hasta 20 eventos aprobados cuya fecha todavía no ha pasado. Reabrir la pantalla vuelve a consultar; no existe listener permanente.
- Las reglas e índices de 1.4 deben desplegarse juntos antes de usar este flujo en Firebase remoto. No se realizó ese despliegue.
