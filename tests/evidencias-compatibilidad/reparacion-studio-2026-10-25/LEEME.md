# Corrección del Run real de Android Studio

## Fallo confirmado después de la primera reparación

El usuario volvió a obtener `Device did not start` al pulsar Run con API 24. El registro de las 07:35–07:41 del 25 de octubre muestra que la anulación de Vulkan sí estaba activa, pero Android Studio seguía agotando los 300 segundos de espera. Por tanto, las pruebas anteriores por terminal no demostraban que el Run real del IDE estuviera corregido. Se conserva ese intento en `studio-fallo-antes.txt`.

## Cambio adicional aplicado

Se abrió el proyecto en Android Studio y, desde su interfaz, se cambió:

**Settings > Tools > Emulator > Launch in the Running Devices tool window: desmarcado.**

El estado anterior estaba marcado. Android Studio guardó la nueva preferencia en:

```text
C:\Users\jcamp\AppData\Roaming\Google\AndroidStudio2026.1.4\options\emulator.xml
```

```xml
<application>
  <component name="Emulator">
    <option name="launchInToolWindow" value="false" />
  </component>
</application>
```

El emulador ahora se abre en una ventana independiente al pulsar Run. La preferencia afecta al entorno del usuario del IDE, no al APK. Se conserva la anulación de Vulkan de la reparación anterior. No se cambió código Kotlin, Firebase, permisos, bibliotecas ni versiones; no se borraron los datos del AVD. Se retiraron únicamente los procesos del intento atascado antes de repetir la prueba.

## Prueba realizada desde el botón Run

Se usó control de la interfaz de Windows para pulsar el botón Run, primero con `Mascotas Compat API24` y después con `Mascotas Compat API35`. Se cerró el primer emulador antes de iniciar el segundo. No se inició la aplicación mediante `adb am start` para sustituir estas pruebas; Android Studio la instaló y lanzó.

| Resultado | API 24 Android 7 | API 35 Android 15 |
| --- | --- | --- |
| Selección y Run en la interfaz real de Android Studio | Realizado | Realizado |
| Proceso del emulador sin `-qt-hide-window` | Confirmado | Confirmado |
| `sys.boot_completed` y `dev.bootcomplete` | Ambos `1` | Ambos `1` |
| Instalación del APK por el IDE | Confirmada en log y notificación | Confirmada en log |
| Proceso `com.mascotasmunicipales` y Activity visible | Confirmados | Confirmados |
| Pantalla de la aplicación | Bienvenido | Nuevo Reporte restaurado de la sesión existente |

La prueba de API 24 se instaló alrededor de las 07:49 y la de API 35 alrededor de las 07:52, hora de Colombia. Los JSON `api24-run.json` y `api35-run.json`, las capturas y las salidas de instalación conservan la evidencia. Las capturas de Android no muestran correos ni contraseñas. El registro de API 35 confirma que el propio IDE ejecutó el lanzamiento de MainActivity.

Se verificó además visualmente la ventana independiente de Android 15, con la app abierta. Esta comprobación cubre arranque, instalación y apertura mediante Run; no repite los recorridos funcionales completos ni verifica todos los equipos, redes o futuros inicios.

## Cómo seguir trabajando

Seleccionar el AVD y pulsar Run normalmente. El teléfono aparecerá en una **ventana separada** de Android Studio; no esperar verlo dentro de Running Devices. Mantener un solo AVD activo. Cerrar la ventana del emulador anterior antes de ejecutar la otra API.

Se dejó Android Studio abierto y Android 15 funcionando para que el usuario pueda comprobarlo. Android 7 quedó cerrado. La preferencia es reversible volviendo a marcar la misma casilla; hacerlo restaura el modo integrado que falló en esta prueba.

El código Android no se modificó. En la reparación previa de ese mismo día pasaron `:app:assembleDebug` y `:app:lintDebug` con 0 errores y 40 advertencias; después de este cambio de preferencias se comprobaron las compilaciones e instalaciones realizadas por ambos Run reales.
